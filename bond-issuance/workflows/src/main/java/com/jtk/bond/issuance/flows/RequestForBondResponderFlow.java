package com.jtk.bond.issuance.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.jtk.bond.issuance.constants.CordaParties;
import com.jtk.bond.issuance.flows.utils.CustomQuery;
import com.jtk.bond.issuance.state.BondState;
import com.jtk.bond.issuance.state.TermState;
import com.jtk.corda.Utility;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken;
import com.r3.corda.lib.tokens.workflows.utilities.FungibleTokenBuilder;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.TransactionState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FinalityFlow;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.flows.ReceiveStateAndRefFlow;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.node.services.IdentityService;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Request will generate a BondState which has a copy of the terms and is shared by the Issuer and Investor
 * Once all the bonds are sold, the issuer can start giving coupon payments
 */

@InitiatedBy(RequestForBondInitiatorFlow.class)
public class RequestForBondResponderFlow extends FlowLogic<SignedTransaction>{

    private static final Logger log = LoggerFactory.getLogger(RequestForBondResponderFlow.class);

    private final FlowSession investorSession;

    public RequestForBondResponderFlow(FlowSession investorSession){
        this.investorSession = investorSession;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        log.info("Called response from investor: {}", investorSession.getCounterparty().getName().getCommonName());
        List<StateAndRef<TermState>> investorTermStateRefList = subFlow(new ReceiveStateAndRefFlow<>(investorSession));
        StateAndRef<TermState> investorTermStateRef = investorTermStateRefList.get(0);
        TermState investorTermState = investorTermStateRef.getState().getData();

        //query for the current TermStateAndRef
        StateAndRef<TermState> currentTermStateAndRef = CustomQuery.queryTermsByTermStateLinearID
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
        Set<Party> investors = new HashSet<>(investorTermState.getInvestors());
        investors.add(brn.investor);

        List<Party> allBanks = Utility.getLegalIdentitiesByOU(identityService, "Bank")
                .stream()
                .filter(party -> !Objects.equals(party.getName().getCommonName(),
                        getOurIdentity().getName().getCommonName()))
                .collect(Collectors.toList());
        List<Party> observers = Utility.getLegalIdentitiesByOU(identityService,"Observer");

        /*** Update the TermState ****/
        TermState newTermState = new TermState(
                investorTermState.getIssuer(), investors,investorTermState.getBondName(),
                investorTermState.getBondStatus(),investorTermState.getCouponPaymentLeft(),
                investorTermState.getInterestRate(),investorTermState.getParValue(),
                newAvailableUnits, (investorTermState.getRedemptionAvailable() + brn.units),
                investorTermState.getLinearId(),investorTermState.getMaturityDate(),
                investorTermState.getBondType(),investorTermState.getCurrency(),
                investorTermState.getCreditRating(), investorTermState.getPaymentsPerYear());

        List<Party> termObservers = new ArrayList<>();
        termObservers.addAll(allBanks);
        termObservers.addAll(observers);
        log.info("Identified observers to publish term-state: {}",
                termObservers.stream().map(party -> party.getName().getCommonName()).collect(Collectors.joining(",")));
        SignedTransaction txId = subFlow(new UpdateEvolvableToken(investorTermStateRef, newTermState, termObservers));
            log.info("TermState ID:{} has been updated with availableUnits:{} TxID: {}", newTermState.getLinearId(),
                    newAvailableUnits,
                    txId.getId());
        /*** Published new TermState ****/

        /** Issue a bond **/
        final BondState bondState = new BondState(
                getOurIdentity(),brn.investor,newTermState.getInterestRate(),newTermState.getParValue(),
                newTermState.getMaturityDate(), newTermState.getCreditRating(), newTermState.getCouponPaymentLeft(),
                newTermState.getBondStatus(), newTermState.getBondType(), newTermState.getCurrency(), newTermState.getBondName(),
                newTermState.getLinearId(),new UniqueIdentifier(), newTermState.getPaymentsPerYear());

        TransactionState<BondState> transactionState = new TransactionState<>(bondState, notary);
        List<Party> bondObservers = new ArrayList<>();
        bondObservers.addAll(observers);
        bondObservers.add(getOurIdentity());
        subFlow(new CreateEvolvableTokens(transactionState, bondObservers));

        log.info("Published evolvable tokens for Bond {} ", bondState.getBondName());

        FungibleToken bondFungibleToken = new FungibleTokenBuilder()
                .ofTokenType(bondState.toPointer())
                .issuedBy(getOurIdentity())
                .heldBy(brn.investor)
                .withAmount(brn.units)
                .buildFungibleToken();

        final SignedTransaction stx = subFlow(new IssueTokens(ImmutableList.of(bondFungibleToken), bondObservers));
        return subFlow(new FinalityFlow(stx, ImmutableList.of(investorSession)));
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
