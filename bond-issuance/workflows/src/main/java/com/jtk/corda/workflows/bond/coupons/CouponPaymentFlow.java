package com.jtk.corda.workflows.bond.coupons;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import static com.jtk.corda.contants.BondStatus.ACTIVE;
import com.jtk.corda.states.bond.issuance.BondState;
import com.jtk.corda.workflows.cash.issuance.TransferTokenFlow;
import com.jtk.corda.workflows.utils.CouponPaymentUtil;
import com.jtk.corda.workflows.utils.CustomQuery;
import com.jtk.corda.workflows.utils.Utility;
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.flows.FinalityFlow;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.SchedulableFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.serialization.ConstructorForDeserialization;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@InitiatingFlow
@StartableByRPC
@SchedulableFlow
public class CouponPaymentFlow extends FlowLogic<String> {
    private static final Logger log = LoggerFactory.getLogger(CouponPaymentFlow.class);
    private final static DateTimeFormatter locateDateformat = DateTimeFormatter.ofPattern("yyyyMMdd");
    private String couponDate;

    private final ProgressTracker progressTracker = new ProgressTracker(
            FETCH_STATES,
            CHECK_BOND_FOR_COUPON,
            IDENTIFY_OBSERVERS,
            FETCH_BOND_TOKENS_SIZE,
            CALCULATE_COUPON,
            SEND_CASH,
            SIGNING_TRANSACTIONS,
            UPDATE_STATES,
            DONE
    );

    private static final ProgressTracker.Step FETCH_STATES = new ProgressTracker
            .Step("Fetch Bond States for Coupon payment");

    private static final ProgressTracker.Step CHECK_BOND_FOR_COUPON = new ProgressTracker
            .Step("Check Bond States for Coupon payment");

    private static final ProgressTracker.Step FETCH_BOND_TOKENS_SIZE = new ProgressTracker
            .Step("Fetch Bond Tokens size held by Bond Holder");

    private static final ProgressTracker.Step CALCULATE_COUPON = new ProgressTracker
            .Step("Calculate Coupon Payment");

    private static final ProgressTracker.Step SEND_CASH = new ProgressTracker
            .Step("Sending digital currency");

    private static final ProgressTracker.Step SIGNING_TRANSACTIONS = new ProgressTracker
            .Step("Signing Transaction");

    private static final ProgressTracker.Step UPDATE_STATES = new ProgressTracker
            .Step("Updating Bond States");

    private static final ProgressTracker.Step IDENTIFY_OBSERVERS = new ProgressTracker
            .Step("Identifying Bond Observers");

    private static final ProgressTracker.Step DONE = new ProgressTracker
            .Step("Done Processing Flow");

    public CouponPaymentFlow(){
        this.couponDate = LocalDate.now().format(locateDateformat);
    }

    public CouponPaymentFlow(String couponDate){
        this.couponDate = couponDate;
    }
    @Override
    @Suspendable
    public String call() throws FlowException {
        Party me = getOurIdentity();
        log.info("Checking coupon payments for {}",me.getName().getCommonName());
        progressTracker.setCurrentStep(FETCH_STATES);
        List<BondState> bondIssuedByMe = CustomQuery.queryBondsPointerWithCouponDate(couponDate, getServiceHub())
                .stream()
                .filter(bondState -> bondState.getIssuer().equals(me))
                .filter(bondState -> bondState.getBondStatus().equalsIgnoreCase(ACTIVE.name()))
                .collect(Collectors.toList());
        log.info("Found {} coupons to pay ", bondIssuedByMe.size());

        progressTracker.setCurrentStep(IDENTIFY_OBSERVERS);
        List<Party> bondObservers = new ArrayList<>(Utility.getLegalIdentitiesByOU
                (getServiceHub().getIdentityService(), "Observer"));
        bondObservers.add(me);

        for (BondState bs:  bondIssuedByMe){
            if(bs.getCouponPaymentLeft() > 0){
                progressTracker.setCurrentStep(CHECK_BOND_FOR_COUPON);
                log.info("Calculating coupon on bondId {}", bs.getLinearId());
                LocalDate cDate = LocalDate.parse(couponDate, locateDateformat);
                Party holder = bs.getInvestor();
                int parValue = bs.getParValue();
                FlowSession bondHolderSession = initiateFlow(holder);
                StateAndRef<BondState> oldBondStateAndRef = CustomQuery.queryBondByLinearID(bs.getLinearId(), getServiceHub());
                String bondLinearId = oldBondStateAndRef.getState()
                        .getData()
                        .getLinearId()
                        .toString();
                CouponPaymentNotification couponPaymentNotification =
                        new CouponPaymentNotification(
                                me,
                                0L,
                                "PENDING",
                                bondLinearId,
                                bs.getTermStateLinearID().toString(), false);
                progressTracker.setCurrentStep(FETCH_BOND_TOKENS_SIZE);
                Long numberOfTokens = bondHolderSession.sendAndReceive(CouponPaymentNotification.class, couponPaymentNotification)
                        .unwrap(data -> {
                            if (data.status.equals("OK")) {
                                return data.getNumberOfTokens();
                            } else {
                                return null;
                            }
                        });
                if(numberOfTokens == null){
                    throw new FlowException("Something went wrong with Coupon payment");
                }
                //Coupon payment = parvalue * (annual coupon rate / number of payments per year)
                progressTracker.setCurrentStep(CALCULATE_COUPON);
                int paymentsPerYear = 12 / bs.getPaymentFrequencyInMonths();
                double coupon = (parValue * (bs.getInterestRate() / paymentsPerYear)) * numberOfTokens;
                log.info("{}$ Coupon to be send to bond holder", coupon);

                progressTracker.setCurrentStep(SEND_CASH);
                progressTracker.setCurrentStep(SIGNING_TRANSACTIONS);
                SignedTransaction tStxTran = subFlow(
                        new TransferTokenFlow.
                                TransferTokenInitiator(String.valueOf(coupon), bs.getCurrency(), bs.getInvestor()));

                log.info("TransactionID:{} for coupons to investor {}",
                        tStxTran.getId(),
                        bs.getInvestor().getName().getCommonName());

                // update bond with the next coupon date
                // reduce the number of coupon payments by on2
                LocalDate nextCouponDate = CouponPaymentUtil.getNextCouponPaymentDate(cDate, 30, bs.getPaymentFrequencyInMonths());
                LocalDate  mDate = LocalDate.parse(bs.getMaturityDate(),locateDateformat);
                String  nCouponDate = locateDateformat.format(nextCouponDate);
                if(nextCouponDate.isAfter(mDate)){
                    nCouponDate = couponDate;
                }
                long couponPaymentLeft = bs.getCouponPaymentLeft() - 1;
                progressTracker.setCurrentStep(UPDATE_STATES);
                BondState newBondState = new BondState(
                        me, bs.getInvestor(), bs.getInterestRate(),bs.getParValue(),
                        bs.getMaturityDate(), bs.getCreditRating(), couponPaymentLeft,
                        bs.getBondStatus(), bs.getBondType(), bs.getCurrency(),
                        bs.getBondName(), bs.getTermStateLinearID(),bs.getLinearId(),
                        bs.getPaymentFrequencyInMonths(), bs.getIssueDate(), nCouponDate);

                progressTracker.setCurrentStep(SIGNING_TRANSACTIONS);
                SignedTransaction txId = subFlow(new UpdateEvolvableToken(oldBondStateAndRef, newBondState, bondObservers));
                log.info("BondState has been updated with nextCouponDate:{},couponPaymentLeft:{}  after coupon payment TxID: {}",
                        nCouponDate,
                        couponPaymentLeft,
                        txId.getId());
                SignedTransaction finalTx = subFlow(new FinalityFlow(txId, ImmutableList.of(bondHolderSession)));

                log.info(String.format("Completed Coupon payment to %s for coupon date %s with transactionId: %s",
                        holder.getName().getCommonName(), cDate, finalTx.getId()));
            }else {
                log.info("No coupon left to pay pn bondId {}", bs.getLinearId());
            }
        }
        progressTracker.setCurrentStep(DONE);
        return String.format("{ " +
                        "\"issuer\":\"%s\"," +
                        "\"couponDate\":\"%s\"" +
                        "}",
                me.getName().getCommonName(),
                couponDate);
    }

    @CordaSerializable
    public static class CouponPaymentNotification {
        private final Party issuer;
        private final Long numberOfTokens;
        private final String status;

        private final String bondLinearID;
        private String termLinearId;
        private boolean redeem;

        @ConstructorForDeserialization
        public CouponPaymentNotification(Party issuer,
                                         Long numberOfTokens,
                                         String status,
                                         String bondLinearID,
                                         String termLinearId, boolean redeem) {
            this.issuer = issuer;
            this.numberOfTokens = numberOfTokens;
            this.status = status;
            this.bondLinearID = bondLinearID;
            this.termLinearId = termLinearId;
            this.redeem = redeem;
        }

        public Party getIssuer() {
            return issuer;
        }

        public Long getNumberOfTokens() {
            return numberOfTokens;
        }

        public String getBondLinearID() {
            return bondLinearID;
        }

        public String getStatus() {
            return status;
        }

        public String getTermLinearId() {
            return termLinearId;
        }

        public boolean isRedeem() {
            return redeem;
        }
    }
}