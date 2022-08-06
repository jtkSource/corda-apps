package com.jtk.bond.issuance.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.jtk.bond.issuance.constants.CordaParties;
import com.jtk.bond.issuance.flows.utils.CustomQuery;
import com.jtk.bond.issuance.state.BondState;
import com.jtk.bond.issuance.state.TermState;
import com.jtk.corda.Utility;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensUtilities;
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken;
import com.r3.corda.lib.tokens.workflows.utilities.FungibleTokenBuilder;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.TransactionState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.CollectSignaturesFlow;
import net.corda.core.flows.FinalityFlow;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.ReceiveFinalityFlow;
import net.corda.core.flows.ReceiveStateAndRefFlow;
import net.corda.core.flows.SendStateAndRefFlow;
import net.corda.core.flows.SignTransactionFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.node.services.IdentityService;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * Request will generate a BondState which has a copy of the terms and is shared by the Issuer and Investor
 * Once all the bonds are sold, the issuer can start giving coupon payments
 */

public class RequestForBondFlow {

    private static final Logger log = LoggerFactory.getLogger(RequestForBondFlow.class);

    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<String>{
        private static final Logger log = LoggerFactory.getLogger(RequestForBondFlow.class);
        private final UniqueIdentifier teamStateLinearID;
        private final int unitsOfBonds;

        public Initiator(UniqueIdentifier teamStateLinearID, int unitsOfBonds) {
            this.teamStateLinearID = teamStateLinearID;
            this.unitsOfBonds = unitsOfBonds;
        }

        @Suspendable
        @Override
        public String call() throws FlowException {

            Party investorParty = getOurIdentity();
            if(!investorParty.getName().getOrganisationUnit().equals("Bank")){
                log.error("The flow is not invoked by a bank");
                throw new FlowException("Flow can be invoked by OU=Bank only");
            }

            //find term based on linearID and see if the requested amount is available
            StateAndRef<TermState> termStateAndRef = CustomQuery.queryTermsByTeamStateLinearID
                    (teamStateLinearID, getServiceHub());

            if(termStateAndRef != null){
                log.info("Term: {} is present ", teamStateLinearID);
                TermState termState = termStateAndRef.getState()
                        .getData().toPointer(TermState.class)
                        .getPointer().resolve(getServiceHub()).getState().getData();
                // check if requested unit is greater than available units
                if(unitsOfBonds > termState.getUnitsAvailable()){
                    throw new FlowException("Requesting for more bonds than available for the term");
                }
                if(termState.getIssuer().equals(investorParty)){
                    throw new FlowException("Issuer cannot be investor of the bond");
                }

                BondRequestNotification bondRequestNotification =
                        new BondRequestNotification(getOurIdentity(), unitsOfBonds);


                // We start by initiating a flow session with the counterparty. We
                // will use this session to send and receive messages from the
                // counterparty.
                FlowSession counterpartySession = initiateFlow(termState.getIssuer());
                // first send the TermState held by the investor
                subFlow(new SendStateAndRefFlow(counterpartySession, ImmutableList.of(termStateAndRef)));
                // then send the bond request notification
                counterpartySession.send(bondRequestNotification);

                class SignTxFlow extends SignTransactionFlow{

                    private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker){
                        super(otherPartyFlow, progressTracker);
                    }
                    @Override
                    protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                        requireThat(req->{
                            log.info("Checking Signed Transaction of BondState created ");
                            // BondState should be signed by the investor
                            BondState bondState = stx.getTx().outputsOfType(BondState.class).get(0);
                            req.using("Received Bond should be owned by the investor",
                                    bondState.getInvestor().equals(getOurIdentity()));
                            return null;
                        });
                    }
                }
                final  SignTxFlow signTxFlow = new SignTxFlow(counterpartySession, SignTransactionFlow.Companion.tracker());
                final SecureHash txId = subFlow(signTxFlow).getId();
                subFlow(new ReceiveFinalityFlow(counterpartySession, txId));
                return "Request has been send, Please wait for the Bond Issuer to Respond";
            }else {
                throw new IllegalArgumentException(teamStateLinearID + ": not found xxx");
            }

        }
    }

    @InitiatedBy(Initiator.class)
    public static class Responder extends FlowLogic<SignedTransaction>{
        private final FlowSession investorSession;

        public Responder(FlowSession investorSession){
            this.investorSession = investorSession;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // receive te TermState
            List<StateAndRef<TermState>> investorTermStateRefList = subFlow(new ReceiveStateAndRefFlow<>(investorSession));
            StateAndRef<TermState> investorTermStateRef = investorTermStateRefList.get(0);
            TermState investorTermState = investorTermStateRef.getState().getData();

            //query for the current TermStateAndRef
            StateAndRef<TermState> currentTermStateAndRef = CustomQuery.queryTermsByTeamStateLinearID
                            (investorTermState.getLinearId(), getServiceHub())
                    .getState().getData().toPointer()
                    .getPointer()
                    .resolve(getServiceHub());

            log.info("Queried TermState for {}", investorTermState.getLinearId());
            // check if the TermState held by the investor is the same
            BondRequestNotification brn = investorSession.receive(BondRequestNotification.class)
                    .unwrap(it -> {
                        if(!investorTermStateRef.getRef().getTxhash().equals(currentTermStateAndRef.getRef().getTxhash())){
                            throw new FlowException("TermState does not match with the issuers. " +
                                    "Investors may not have updated the latest TermState.");
                        }
                        return it;
                    });

            log.info("Received Request to create {} BondStates",brn.units);
            int newAvailableUnits = investorTermState.getUnitsAvailable() - brn.units;
            if(newAvailableUnits < 0 ){
                throw new FlowException("Cannot request for bonds more than available in TermState");
            }
            IdentityService identityService = getServiceHub().getIdentityService();
            CordaX500Name notaryX500Name = CordaX500Name.parse(CordaParties.NOTARY.getCordaX500Name());
            final Party notary = getServiceHub().getNetworkMapCache().getNotary(notaryX500Name);

            /*** Update the TermState ****/
            List<Party> otherBanks = Utility.getLegalIdentitiesByOU(identityService, "Bank")
                    .stream()
                    .filter(party -> !party.getName().getCommonName().equals(getOurIdentity().getName().getCommonName()))
                    .collect(Collectors.toList());
            List<Party> observers = Utility.getLegalIdentitiesByOU(identityService,"Observer");
            observers.addAll(otherBanks);
            log.info("Identified observers to publish term-state: {}",
                    observers.stream().map(party -> party.getName().getCommonName()).collect(Collectors.joining(",")));

            Set<Party> investors = investorTermState.getInvestors();
            investors.add(brn.investor);
            TermState newTermState = new TermState(
                    investorTermState.getIssuer(), investors,investorTermState.getBondName(),
                    investorTermState.getBondStatus(),investorTermState.getCouponPaymentLeft(),
                    investorTermState.getInterestRate(),investorTermState.getPurchasePrice(),
                    newAvailableUnits, (investorTermState.getRedemptionAvailable() + brn.units),
                    investorTermState.getLinearId(),investorTermState.getMaturityDate(),
                    investorTermState.getBondType(),investorTermState.getCurrency(),investorTermState.getCreditRating());

            SignedTransaction txId = subFlow(new UpdateEvolvableToken(investorTermStateRef, newTermState, observers));
            log.info("TermState ID:{} has been updated with availableUnits:{} TxID: {}", newTermState.getLinearId(),
                    newAvailableUnits,
                    txId.getId());

            /*** Published new TermState ****/
            /** Issue a bond **/
            final BondState bondState = new BondState(
                    getOurIdentity(),brn.investor,newTermState.getInterestRate(),newTermState.getPurchasePrice(),
                    newTermState.getMaturityDate(), newTermState.getCreditRating(), newTermState.getCouponPaymentLeft(),
                    newTermState.getBondStatus(), newTermState.getBondType(), newTermState.getCurrency(), newTermState.getBondName(),
                    newTermState.getLinearId(),new UniqueIdentifier());

            TransactionState<BondState> transactionState = new TransactionState<>(bondState, notary);
            subFlow(new CreateEvolvableTokens(transactionState, observers));

            log.info("Published evolvable tokens for Bond {} ", bondState.getBondName());

            TransactionBuilder txBuilder = new TransactionBuilder(notary);
            FungibleToken bondFungibleToken = new FungibleTokenBuilder()
                    .ofTokenType(bondState.toPointer())
                    .issuedBy(getOurIdentity())
                    .heldBy(brn.investor)
                    .withAmount(brn.units)
                    .buildFungibleToken();
            IssueTokensUtilities.addIssueTokens(txBuilder, bondFungibleToken);
            txBuilder.verify(getServiceHub());
            log.info("Signing Fungible tokens for Bond {} ", bondState.getBondName());
            SignedTransaction ptx = getServiceHub().signInitialTransaction(txBuilder, getOurIdentity().getOwningKey());
            final ImmutableSet<FlowSession> sessions = ImmutableSet.of(investorSession);
            final SignedTransaction stx = subFlow(new CollectSignaturesFlow(
                    ptx,
                    sessions));
            return subFlow(new FinalityFlow(stx, sessions));
        }
    }


    @CordaSerializable
    public static class BondRequestNotification {
        private final Party investor;
        private final int units;

        public BondRequestNotification(Party investor, int units) {
            this.investor = investor;
            this.units = units;
        }

        public int getUnits() {
            return units;
        }

        public Party getInvestor() {
            return investor;
        }
    }

}
