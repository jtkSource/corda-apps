package com.jtk.corda.workflows.bond.issuance;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.jtk.corda.CordaParties;
import com.jtk.corda.states.bond.issuance.BondState;
import com.jtk.corda.states.bond.issuance.TermState;
import com.jtk.corda.workflows.utils.CouponPaymentUtil;
import com.jtk.corda.workflows.utils.CustomQuery;
import com.jtk.corda.workflows.utils.Utility;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final FlowSession investorSession;

    public RequestForBondResponderFlow(FlowSession investorSession){
        this.investorSession = investorSession;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        Party bondIssuer = getOurIdentity();
        Party counterparty = investorSession.getCounterparty();

        log.info("Issuing bond from {} to investor: {}",bondIssuer.getName().getCommonName(),
                counterparty.getName().getCommonName());

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
        CordaX500Name notaryX500Name = CordaParties.NOTARY.getCordaX500Name();
        final Party notary = getServiceHub().getNetworkMapCache().getNotary(notaryX500Name);
        Set<Party> investors = new HashSet<>(investorTermState.getInvestors());
        investors.add(brn.investor);

        List<Party> allBanks = Utility.getLegalIdentitiesByOU(identityService, "Bank")
                .stream()
                .filter(party -> !Objects.equals(party.getName().getCommonName(),
                        bondIssuer.getName().getCommonName()))
                .collect(Collectors.toList());
        List<Party> observers = Utility.getLegalIdentitiesByOU(identityService,"Observer");

        /*** Update the TermState ****/
        TermState newTermState = new TermState(
                investorTermState.getIssuer(), investors,investorTermState.getBondName(),
                investorTermState.getBondStatus(),investorTermState.getInterestRate(),investorTermState.getParValue(),
                newAvailableUnits, (investorTermState.getRedemptionAvailable() + brn.units),
                investorTermState.getLinearId(),investorTermState.getMaturityDate(),
                investorTermState.getBondType(),investorTermState.getCurrency(),
                investorTermState.getCreditRating(), investorTermState.getPaymentFrequencyInMonths());

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
        LocalDate mDate = LocalDate.parse(newTermState.getMaturityDate(), dateFormatter);
        LocalDate now = LocalDate.now();
        long numberOfPayments = CouponPaymentUtil.getCouponPayments
                (newTermState.getPaymentFrequencyInMonths(), 30, mDate, now);
        LocalDate nextCouponDate = CouponPaymentUtil.getNextCouponPaymentDate
                (now,30, newTermState.getPaymentFrequencyInMonths());

        // In order to demo coupon payment first coupon is always on the same date
        nextCouponDate = LocalDate.now();

        String issueDate = dateFormatter.format(now);
        String nCouponDate = dateFormatter.format(nextCouponDate);

        log.info("next coupon payment date: {}", nCouponDate);

        List<BondState> bondIssuedByMe = CustomQuery
                .queryBondByTermStateLinearID(newTermState.getLinearId(), getServiceHub())
                .stream()
                .filter(bondState -> bondState.getInvestor().equals(counterparty))
                .collect(Collectors.toList());

        List<Party> bondObservers = new ArrayList<>();
        bondObservers.addAll(observers);
        bondObservers.add(bondIssuer);
        BondState bondState;
        if(bondIssuedByMe.size() == 0) {
            bondState = new BondState(
                    bondIssuer, brn.investor, newTermState.getInterestRate(), newTermState.getParValue(),
                    newTermState.getMaturityDate(), newTermState.getCreditRating(), numberOfPayments,
                    newTermState.getBondStatus(), newTermState.getBondType(), newTermState.getCurrency(),
                    newTermState.getBondName(), newTermState.getLinearId(), new UniqueIdentifier(),
                    newTermState.getPaymentFrequencyInMonths(), issueDate, nCouponDate);
            TransactionState<BondState> transactionState = new TransactionState<>(bondState, notary);
            subFlow(new CreateEvolvableTokens(transactionState, bondObservers));
        }else {
            bondState = bondIssuedByMe.get(0);
        }
        investorSession.send(new BondRequestNotification(
                brn.investor,
                brn.units,
                "OK",
                bondState.getLinearId().toString()));

        log.info("Published evolvable tokens for Bond {} ", bondState.getBondName());

        FungibleToken bondFungibleToken = new FungibleTokenBuilder()
                .ofTokenType(newTermState.toPointer())
                .issuedBy(bondIssuer)
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

        private final String status;
        private String bondLinearId;

        public BondRequestNotification(Party investor, int units, String status, String bondLinearId) {
            this.investor = investor;
            this.units = units;
            this.status = status;
            this.bondLinearId = bondLinearId;
        }
        public int getUnits() {
            return units;
        }

        public Party getInvestor() {
            return investor;
        }

        public String getStatus() {
            return status;
        }

        public String getBondLinearId() {
            return bondLinearId;
        }
    }

}
