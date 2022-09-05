package com.jtk.corda.workflows.bond.coupons;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.jtk.corda.contants.BondStatus;
import static com.jtk.corda.contants.BondStatus.ACTIVE;
import com.jtk.corda.states.bond.issuance.BondState;
import com.jtk.corda.states.bond.issuance.TermState;
import com.jtk.corda.workflows.bond.issuance.QueryBondToken;
import com.jtk.corda.workflows.cash.issuance.TransferTokenFlow;
import com.jtk.corda.workflows.utils.CustomQuery;
import com.jtk.corda.workflows.utils.Utility;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.workflows.flows.rpc.RedeemFungibleTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilities;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FinalityFlow;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.ReceiveFinalityFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redeem Coupons for particular term
 * This can be an early redemption or when the maturity date is hit
 */
public class RedeemCouponPaymentFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class RedeemCouponInitiator extends FlowLogic<String>{

        private static final Logger log = LoggerFactory.getLogger(RedeemCouponInitiator.class);
        private final String termId;
        private final String maturityDate;
        private final boolean earlyRedemption;
        private final static DateTimeFormatter locateDateformat = DateTimeFormatter.ofPattern("yyyyMMdd");

        public RedeemCouponInitiator(String termId, boolean earlyRedemption){
            this.termId = termId;
            this.earlyRedemption = earlyRedemption;
            this.maturityDate = LocalDate.now().format(locateDateformat);
        }

        public RedeemCouponInitiator(String maturityDate){
            this.termId = "";
            this.earlyRedemption = false;
            this.maturityDate = maturityDate;
        }

        @Override
        @Suspendable
        public String call() throws FlowException {
            Party me = getOurIdentity();
            List<BondState> bondIssuedByMe = new ArrayList<>();
            if(this.earlyRedemption){
                log.info("Early redemption on termId:{}", termId);
                bondIssuedByMe.addAll(
                        CustomQuery.queryBondByTermStateLinearID(UniqueIdentifier.Companion.fromString(this.termId),
                                getServiceHub())
                        .stream()
                        .filter(bondState -> bondState.getIssuer().equals(me))
                        .filter(bondState -> bondState.getBondStatus().equalsIgnoreCase(ACTIVE.name()))
                        .collect(Collectors.toList())) ;
                log.info("Found {} bonds for early redemption ", bondIssuedByMe.size());


            }else {
                // fetch all bonds that have reached maturity
                bondIssuedByMe.addAll(
                        CustomQuery.queryBondsPointerEqualMaturityDate
                                (maturityDate, getServiceHub())
                        .stream()
                        .filter(bondState -> bondState.getIssuer().equals(me))
                        .filter(bondState -> bondState.getBondStatus().equalsIgnoreCase(ACTIVE.name()))
                        .collect(Collectors.toList())
                );
                log.info("Found {} matured bonds ", bondIssuedByMe.size());
            }

            List<Party> bondObservers = new ArrayList<>(Utility.getLegalIdentitiesByOU
                    (getServiceHub().getIdentityService(), "Observer"));
            LocalDate mDate = LocalDate.parse(maturityDate, locateDateformat);
            Set<UniqueIdentifier> setOfTerms = new HashSet<>();

            for (BondState bs: bondIssuedByMe){
                log.info("Calculating coupon on bondId {}", bs.getLinearId());
                Party holder = bs.getInvestor();
                int parValue = bs.getParValue();
                FlowSession bondHolderSession = initiateFlow(holder);
                StateAndRef<BondState> oldBondStateAndRef = CustomQuery.
                        queryBondByLinearID(bs.getLinearId(), getServiceHub());
                String bondLinearId = bs.getLinearId().toString();
                CouponPaymentFlow.CouponPaymentNotification couponPaymentNotification =
                        new CouponPaymentFlow.CouponPaymentNotification(
                                me,
                                0L,
                                "PENDING",
                                bondLinearId,
                                bs.getTermStateLinearID().toString());
                Long numberOfTokens = bondHolderSession
                        .sendAndReceive(CouponPaymentFlow.CouponPaymentNotification.class, couponPaymentNotification)
                        .unwrap(data -> {
                            if (data.getStatus().equals("OK")) {
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
                LocalDate nCouponDate = LocalDate.parse(bs.getNextCouponDate(), locateDateformat);
                double coupon = 0;
                if(nCouponDate.isBefore(mDate)){
                    long days = Duration.between(mDate, nCouponDate).toDays();
                    double si = parValue / ((((bs.getInterestRate()/100))/365) * days);
                    coupon = (parValue + si) * numberOfTokens;
                }else {
                    coupon = parValue;
                }
                log.info("{}$ Coupon redemption to be send to bond holder", coupon);

                subFlow(new TransferTokenFlow.
                        TransferTokenInitiator(String.valueOf(coupon), bs.getCurrency(), bs.getInvestor()));
                setOfTerms.add(bs.getTermStateLinearID());

                BondState newBondState = new BondState(
                        me, bs.getInvestor(), bs.getInterestRate(),bs.getParValue(),
                        bs.getMaturityDate(), bs.getCreditRating(), 0,
                        BondStatus.MATURED.name(), bs.getBondType(), bs.getCurrency(),
                        bs.getBondName(), bs.getTermStateLinearID(),bs.getLinearId(),
                        bs.getPaymentFrequencyInMonths(), bs.getIssueDate(), "");
                SignedTransaction txId = subFlow(new UpdateEvolvableToken(oldBondStateAndRef, newBondState, bondObservers));
                subFlow(new FinalityFlow(txId, ImmutableList.of(bondHolderSession)));
            }
            for (UniqueIdentifier id : setOfTerms){
                TokenPointer<TermState> termStateTokenPointer = CustomQuery.
                        queryTermsByTermStateLinearID(id, getServiceHub())
                        .getState()
                        .getData().toPointer();
                StateAndRef<TermState> termStateRef = termStateTokenPointer.getPointer()
                        .resolve(getServiceHub());
                TermState termState = termStateRef.getState().getData();
//                Amount<TokenType> amount = QueryUtilities.tokenBalance(getServiceHub().getVaultService(), termStateTokenPointer);
//                subFlow(new RedeemFungibleTokens(amount, termState.getIssuer(), bondObservers));
                TermState newTermState = new TermState
                        (termState.getIssuer(), termState.getInvestors(), termState.getBondName(),
                                BondStatus.MATURED.name(),termState.getInterestRate(), termState.getParValue(),
                                0, termState.getRedemptionAvailable(), termState.getLinearId(),
                                termState.getMaturityDate(), termState.getBondType(), termState.getCurrency(),
                                termState.getCreditRating(), termState.getPaymentFrequencyInMonths());
                subFlow(new UpdateEvolvableToken(termStateRef, newTermState, bondObservers));
            }

            return String.format("{ " +
                            "\"issuer\":\"%s\"," +
                            "\"maturityDate\":\"%s\"" +
                            "}",
                    me.getName().getCommonName(),
                    maturityDate);
        }

    }

    @InitiatedBy(RedeemCouponInitiator.class)
    public static class RedeemCouponResponder extends FlowLogic<SignedTransaction> {
        private final FlowSession bondHolderSession;

        public RedeemCouponResponder(FlowSession bondHolderSession){
            this.bondHolderSession = bondHolderSession;
        }
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            Party bondHolder = getOurIdentity();
            Party bondIssuer = bondHolderSession.getCounterparty();
            CouponPaymentFlow.CouponPaymentNotification cpn =
                    bondHolderSession.receive(CouponPaymentFlow.CouponPaymentNotification.class)
                            .unwrap(it -> it);
            Long numberOfToken = subFlow(new QueryBondToken.GetTokenBalance(cpn.getTermLinearId()));
            bondHolderSession.send(new CouponPaymentFlow.CouponPaymentNotification(cpn.getIssuer(),
                    numberOfToken,
                    "OK",
                    cpn.getBondLinearID(),
                    cpn.getTermLinearId()));
            return subFlow(new ReceiveFinalityFlow(bondHolderSession));
        }
    }
}
