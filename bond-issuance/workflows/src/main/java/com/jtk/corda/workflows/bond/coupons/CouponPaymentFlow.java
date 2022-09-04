package com.jtk.corda.workflows.bond.coupons;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
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
        List<BondState> bondIssuedByMe = CustomQuery.queryBondsPointerWithCouponDate(couponDate, getServiceHub())
                .stream()
                .filter(bondState -> bondState.getIssuer().equals(me))
                .collect(Collectors.toList());
        log.info("Found {} coupons to pay ", bondIssuedByMe.size());
        for (BondState bs:  bondIssuedByMe){
            if(bs.getCouponPaymentLeft() > 0){
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
                                bs.getTermStateLinearID().toString());
                Long numberOfTokens = bondHolderSession.sendAndReceive(CouponPaymentNotification.class, couponPaymentNotification)
                        .unwrap(data -> {
                            if (data.status.equals("OK")) {
                                return data.getNumberOfTokens();
                            } else {
                                return null;
                            }
                        });
                //Long bondTokens = subFlow(new QueryBondToken.GetTokenBalance(couponPaymentNotification.getBondLinearID()));
                if(numberOfTokens == null){
                    // check if number of tokens match ?
                    throw new FlowException("Something went wrong with Coupon payment");
                }
                //Coupon payment = parvalue * (annual coupon rate / number of payments per year)
                int paymentsPerYear = 12 / bs.getPaymentFrequencyInMonths();
                double coupon = (parValue * (bs.getInterestRate() / paymentsPerYear)) * numberOfTokens;
                log.info("{}$ Coupon to be send to bond holder", coupon);

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
                    nCouponDate = "";
                }
                long couponPaymentLeft = bs.getCouponPaymentLeft() - 1;
                BondState newBondState = new BondState(
                        me, bs.getInvestor(), bs.getInterestRate(),bs.getParValue(),
                        bs.getMaturityDate(), bs.getCreditRating(), couponPaymentLeft,
                        bs.getBondStatus(), bs.getBondType(), bs.getCurrency(),
                        bs.getBondName(), bs.getTermStateLinearID(),bs.getLinearId(),
                        bs.getPaymentFrequencyInMonths(), bs.getIssueDate(), nCouponDate);

                List<Party> bondObservers = new ArrayList<>(Utility.getLegalIdentitiesByOU
                        (getServiceHub().getIdentityService(), "Observer"));
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

        @ConstructorForDeserialization
        public CouponPaymentNotification(Party issuer,
                                         Long numberOfTokens,
                                         String status,
                                         String bondLinearID,
                                         String termLinearId) {
            this.issuer = issuer;
            this.numberOfTokens = numberOfTokens;
            this.status = status;
            this.bondLinearID = bondLinearID;
            this.termLinearId = termLinearId;
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
    }
}