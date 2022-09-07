package com.jtk.corda.workflows.bond.issuance;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.jtk.corda.CordaParties;
import com.jtk.corda.states.bond.issuance.BondState;
import com.jtk.corda.states.bond.issuance.TermState;
import com.jtk.corda.workflows.cash.issuance.TransferTokenFlow;
import com.jtk.corda.workflows.utils.CustomQuery;
import com.jtk.corda.workflows.utils.Utility;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import com.r3.corda.lib.tokens.workflows.utilities.FungibleTokenBuilder;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilities;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.TransactionState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FinalityFlow;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.ReceiveFinalityFlow;
import net.corda.core.flows.ReceiveStateAndRefFlow;
import net.corda.core.flows.SendStateAndRefFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.node.services.IdentityService;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class InterBankBondTransferFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class InterBankBondTransferFlowInitiator extends FlowLogic<String> {
        private static final Logger log = LoggerFactory.getLogger(InterBankBondTransferFlowInitiator.class);
        private UniqueIdentifier termIdentifier;
        private int unitsOfBond;
        private int cost;
        private Party bondHolder;

        public InterBankBondTransferFlowInitiator(UniqueIdentifier termIdentifier,
                                                  int unitsOfBond,
                                                  int cost,
                                                  Party bondHolder) {
            this.termIdentifier = termIdentifier;
            this.unitsOfBond = unitsOfBond;
            this.cost = cost;
            this.bondHolder = bondHolder;
        }

        @Override
        @Suspendable
        public String call() throws FlowException {
            try {
                log.info("Initiating Inter bank bond transfer...");
                Party me = getOurIdentity();
                if (!me.getName().getOrganisationUnit().equals("Bank") ||
                        !bondHolder.getName().getOrganisationUnit().equals("Bank")) {
                    log.error("Only Bank to Bank Transfer is allowed");
                    throw new FlowException("Only Bank to Bank Transfer is allowed");
                }
                StateAndRef<TermState> termStateAndRef = CustomQuery.queryActiveTermsByTermStateLinearID
                        (this.termIdentifier, getServiceHub());
                if (termStateAndRef != null) {
                    log.info("Term: {} is present ", this.termIdentifier);
                    TermState termState = termStateAndRef.getState()
                            .getData().toPointer(TermState.class)
                            .getPointer()
                            .resolve(getServiceHub())
                            .getState().getData();
                    if (termState.getIssuer().equals(bondHolder)) {
                        throw new FlowException("Issuer cannot be recipient of the bond");
                    }
                    FlowSession flowSession = initiateFlow(bondHolder);
                    log.info("InterBankBondTransferFlowInitiator session initiated for counterparty {}",
                            flowSession.getCounterparty().getName().getCommonName());
                    // send the term state
                    subFlow(new SendStateAndRefFlow(flowSession, ImmutableList.of(termStateAndRef)));
                    List<BondState> bonds = CustomQuery.queryBondByTermStateLinearID(termIdentifier, getServiceHub());
                    String bondId = bonds.stream()
                            .map(BondState::getLinearId)
                            .map(UniqueIdentifier::toString)
                            .findAny().orElse("");

                    BondTransferNotification nf = new BondTransferNotification(
                            unitsOfBond,
                            cost,
                            termIdentifier.toString(),
                            bondId,
                            "PENDING");
                    AtomicReference<String> stringAtomicReference = new AtomicReference<>("");
                    Boolean cashSendSuccessfully = flowSession
                            .sendAndReceive(BondTransferNotification.class, nf)
                            .unwrap(data -> {
                                if (data.getStatus().equalsIgnoreCase("OK")) {
                                    try {
                                        stringAtomicReference.set(data.getBondIdentifier());
                                        log.info("Counterparty accepted BondRequest...");
                                        log.info("Trying to send {} cash", cost);
                                        SignedTransaction tStxTran =
                                                subFlow(new TransferTokenFlow.TransferTokenInitiator(String.valueOf(cost), termState.getCurrency(), termState.getIssuer()));
                                        log.info("TransactionID:{} for tokens to issuer {}", tStxTran.getId(), termState.getIssuer().getName().getCommonName());
                                    } catch (Exception e) {
                                        log.error("Couldn't transfer money to counterparty", e);
                                        return false;
                                    }
                                    return true;
                                } else {
                                    log.warn("Counterparty Rejected BondRequest...");
                                    return false;
                                }
                            });

                    if (cashSendSuccessfully) {
                        SignedTransaction finalTx = subFlow(new ReceiveFinalityFlow(flowSession));
                        FungibleToken fungibleToken = (FungibleToken) finalTx.getTx().getOutputStates().get(0);
                        Party tokenIssuer = fungibleToken.getIssuer();
                        String issuerCN = tokenIssuer.getName().getCommonName();
                        Party tokenHolder = (Party) fungibleToken.getHolder();
                        String holderCN = tokenHolder.getName().getCommonName();
                        Amount<IssuedTokenType> fungibleTokenAmount = ((FungibleToken) finalTx.getTx().getOutputStates().get(0)).getAmount();
                        String tokenIdentifier = fungibleTokenAmount.getToken().getTokenType().getTokenIdentifier();
                        return "{" +
                                "\"tokenType\": \"FungibleToken\", " +
                                "\"name\": \"BondToken\", " +
                                "\"tokenIdentifier\": \"" + tokenIdentifier + "\", " +
                                "\"bondIdentifier\": \"" + stringAtomicReference.get() + "\", " +
                                "\"issuer\": \"" + issuerCN + "\", " +
                                "\"holder\": \"" + holderCN + "\", " +
                                "\"amount\": " + fungibleTokenAmount.getQuantity() + " " +
                                "}";
                    }
                } else {
                    throw new FlowException(this.termIdentifier + ": not found");
                }
            }catch (Exception e){
                log.error("Unexpected Exception when transferring tokens", e);
                return "{" +
                        "\"errorMsg\": \""+e.getMessage()+"\" " +
                        "}";
            }
            return null;
        }
    }

    @InitiatedBy(InterBankBondTransferFlowInitiator.class)
    public static class InterBankBondTransferResponseFlow extends FlowLogic<SignedTransaction> {

        private static final Logger log = LoggerFactory.getLogger(InterBankBondTransferResponseFlow.class);
        private final FlowSession investorSession;

        public InterBankBondTransferResponseFlow(FlowSession investorSession){
            this.investorSession = investorSession;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {
            IdentityService identityService = getServiceHub().getIdentityService();
            Party bondHolder = getOurIdentity();
            Party counterparty = investorSession.getCounterparty();
            final Party notary = getServiceHub().getNetworkMapCache().getNotary(CordaParties.NOTARY.getCordaX500Name());
            List<Party> observers = Utility.getLegalIdentitiesByOU(identityService,"Observer");

            log.info(" Issuing bond from {} to investor: {} ",
                    bondHolder.getName().getCommonName(),
                    counterparty.getName().getCommonName());
            List<StateAndRef<TermState>> investorTermStateRefList =
                    subFlow(new ReceiveStateAndRefFlow<>(investorSession));
            StateAndRef<TermState> investorTermStateRef = investorTermStateRefList.get(0);
            TermState its = investorTermStateRef.getState().getData();

            StateAndRef<TermState> currentTermStateAndRef =
                    CustomQuery.queryActiveTermsByTermStateLinearID(its.getLinearId(), getServiceHub())
                            .getState().getData().toPointer()
                            .getPointer()
                            .resolve(getServiceHub());

            Long totalTokens = subFlow(new QueryBondToken.GetTokenBalance(its.getLinearId().toString()));

            log.info("Queried TermState for ID [{}]", its.getLinearId());
            // check if the TermState held by the investor is the same
            List<BondState> bonds = new ArrayList<>();
            BondTransferNotification brn = investorSession.receive(BondTransferNotification.class)
                    .unwrap(it -> {
                        // check to see if the holder has bonds in that term
                        bonds.addAll(CustomQuery.queryBondByTermStateLinearID
                                                (UniqueIdentifier.Companion.fromString(it.termId),
                                                        getServiceHub()));
                        if(bonds.size() == 0 ){
                            it.status = "NoBondsFound";
                            return it;
                        }
                        // check for availability of tokens
                        if(it.getUnitsOfBond() > totalTokens){
                            it.status = "NotEnoughBonds";
                            return it;
                        }
                        // it.cashAmount; - check if cashAmount is sufficient for it.termId using external API

                        if(!investorTermStateRef.getRef().getTxhash()
                                .equals(currentTermStateAndRef.getRef().getTxhash())){
                            it.status = "NotLatestTerm";
                            return it;
                        }
                        it.status = "OK";
                        return it;
                    });

            if(brn.status.equalsIgnoreCase("OK")){
                investorSession.
                        send(new BondTransferNotification (brn.unitsOfBond, brn.cost, brn.termId, brn.bondIdentifier, brn.status));
                if(Strings.isNullOrEmpty(brn.getBondIdentifier())){
                    BondState bondState = bonds.get(0);
                    BondState newBondState = new BondState(
                            bondState.getIssuer(), counterparty, bondState.getInterestRate(), bondState.getParValue(),
                            bondState.getMaturityDate(), bondState.getCreditRating(), bondState.getCouponPaymentLeft(),
                            bondState.getBondStatus(), bondState.getBondType(), bondState.getCurrency(),
                            bondState.getBondName(), bondState.getTermStateLinearID(), new UniqueIdentifier(),
                            bondState.getPaymentFrequencyInMonths(), bondState.getIssueDate(), bondState.getNextCouponDate());
                    TransactionState<BondState> transactionState = new TransactionState<>(newBondState, notary);
                    List<Party> bondObservers = new ArrayList<>();
                    bondObservers.addAll(observers);
                    bondObservers.add(bondState.getIssuer());
                    subFlow(new CreateEvolvableTokens(transactionState, bondObservers));
                }

                final QueryCriteria heldByMeCriteria = QueryUtilities.heldTokenAmountCriteria(its.toPointer(), bondHolder);
                FungibleToken transferTokens = new FungibleTokenBuilder()
                        .ofTokenType(its.toPointer())
                        .issuedBy(its.getIssuer())
                        .heldBy(bondHolder)
                        .withAmount(brn.getUnitsOfBond())
                        .buildFungibleToken();
                PartyAndAmount<TokenType> partyAmount = new PartyAndAmount(counterparty, transferTokens.getAmount());
                final SignedTransaction stx =  subFlow(new MoveFungibleTokens(Collections.singletonList(partyAmount), observers, heldByMeCriteria, bondHolder));
                return subFlow(new FinalityFlow(stx, ImmutableList.of(investorSession)));
            }else {
                throw new FlowException("Failed validation for Bond Transfer request - " + brn.status);
            }
        }
    }

    @CordaSerializable
    public static class BondTransferNotification {
        private static final Logger log = LoggerFactory.getLogger(BondTransferNotification.class);
        private int unitsOfBond;
        private int cost;
        private String termId;
        private String bondIdentifier;
        private String status;

        public BondTransferNotification(int unitsOfBond, int cost, String termId,
                                        String bondIdentifier,
                                        String status) {
            this.unitsOfBond = unitsOfBond;
            this.cost = cost;
            this.termId = termId;
            this.bondIdentifier = bondIdentifier;
            this.status = status;
        }

        public int getCost() {
            return cost;
        }

        public String getStatus() {
            return status;
        }

        public String getTermId() {
            return termId;
        }

        public String getBondIdentifier() {
            return bondIdentifier;
        }

        public int getUnitsOfBond() {
            return unitsOfBond;
        }
    }
}
