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
import net.corda.core.flows.ReceiveStateAndRefFlow;
import net.corda.core.flows.SendStateAndRefFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.node.services.IdentityService;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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

        private final ProgressTracker progressTracker = new ProgressTracker(
                FETCH_STATES,
                CHECK_BOND_FOR_REDEMPTION,
                IDENTIFY_OBSERVERS,
                FETCH_BOND_TOKENS_SIZE,
                CALCULATE_COUPON,
                SEND_CASH,
                SIGNING_TRANSACTIONS,
                UPDATE_STATES,
                UPDATE_TERM_STATES,
                DONE
        );

        private static final ProgressTracker.Step FETCH_STATES = new ProgressTracker
                .Step("Fetch Bond States for Redemption");
        private static final ProgressTracker.Step IDENTIFY_OBSERVERS = new ProgressTracker
                .Step("Identifying Bond Observers");
        private static final ProgressTracker.Step CHECK_BOND_FOR_REDEMPTION = new ProgressTracker
                .Step("Check Bond States for Redemption payment");
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
        private static final ProgressTracker.Step UPDATE_TERM_STATES = new ProgressTracker
                .Step("Updating Term States");

        private static final ProgressTracker.Step DONE = new ProgressTracker
                .Step("Done Processing Flow");


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
            progressTracker.setCurrentStep(FETCH_STATES);
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
            progressTracker.setCurrentStep(IDENTIFY_OBSERVERS);

            List<Party> bondObservers = new ArrayList<>(Utility.getLegalIdentitiesByOU
                    (getServiceHub().getIdentityService(), "Observer"));
            bondObservers.add(me);

            LocalDate mDate = LocalDate.parse(maturityDate, locateDateformat);
            StateAndRef<TermState> termStateStateAndRef = CustomQuery.
                    queryActiveTermsByTermStateLinearID(UniqueIdentifier.Companion.fromString(termId), getServiceHub());
            if(termStateStateAndRef == null) {
                throw new FlowException("Couldn't find Active TermState");
            }
            TokenPointer<TermState> termStateTokenPointer = termStateStateAndRef
                    .getState()
                    .getData().toPointer();
            StateAndRef<TermState> termStateRef = termStateTokenPointer.getPointer()
                    .resolve(getServiceHub());
            TermState termState = termStateRef.getState().getData();

            for (BondState bs: bondIssuedByMe){
                log.info("Calculating coupon on bondId {}", bs.getLinearId());
                progressTracker.setCurrentStep(CHECK_BOND_FOR_REDEMPTION);
                Party holder = bs.getInvestor();
                int parValue = bs.getParValue();
                FlowSession bondHolderSession = initiateFlow(holder);
                subFlow(new SendStateAndRefFlow(bondHolderSession, ImmutableList.of(termStateRef)));

                StateAndRef<BondState> oldBondStateAndRef = CustomQuery.
                        queryBondByLinearID(bs.getLinearId(), getServiceHub());
                String bondLinearId = bs.getLinearId().toString();

                CouponPaymentFlow.CouponPaymentNotification couponPaymentNotification =
                        new CouponPaymentFlow.CouponPaymentNotification(
                                me,
                                0L,
                                "PENDING",
                                bondLinearId,
                                bs.getTermStateLinearID().toString(), true);
                progressTracker.setCurrentStep(FETCH_BOND_TOKENS_SIZE);
                Long redeemedTokens = bondHolderSession
                        .sendAndReceive(CouponPaymentFlow.CouponPaymentNotification.class, couponPaymentNotification)
                        .unwrap(data -> {
                            if (data.getStatus().equals("OK") && data.isRedeem()) {
                                return data.getNumberOfTokens();
                            } else {
                                return null;
                            }
                        });                //Long bondTokens = subFlow(new QueryBondToken.GetTokenBalance(couponPaymentNotification.getBondLinearID()));
                if(redeemedTokens == null){
                    // check if number of tokens match ?
                    throw new FlowException("Something went wrong with redeeming Coupon Payment");
                }
                LocalDate nCouponDate = LocalDate.parse(bs.getNextCouponDate(), locateDateformat);
                progressTracker.setCurrentStep(CALCULATE_COUPON);
                double coupon;
                if(nCouponDate.isBefore(mDate)){
                    long days = Duration.between(mDate, nCouponDate).toDays();
                    double si = parValue / ((((bs.getInterestRate()/100))/365) * days);
                    coupon = (parValue + si) * redeemedTokens;
                }else {
                    coupon = parValue;
                }
                log.info("{}$ Coupon redemption to be send to bond holder", coupon);
                progressTracker.setCurrentStep(SEND_CASH);
                progressTracker.setCurrentStep(SIGNING_TRANSACTIONS);
                subFlow(new TransferTokenFlow.
                        TransferTokenInitiator(String.valueOf(coupon), bs.getCurrency(), bs.getInvestor()));
                progressTracker.setCurrentStep(UPDATE_STATES);
                BondState newBondState = new BondState(
                        me, bs.getInvestor(), bs.getInterestRate(),bs.getParValue(),
                        bs.getMaturityDate(), bs.getCreditRating(), 0,
                        BondStatus.MATURED.name(), bs.getBondType(), bs.getCurrency(),
                        bs.getBondName(), bs.getTermStateLinearID(),bs.getLinearId(),
                        bs.getPaymentFrequencyInMonths(), bs.getIssueDate(), "");
                SignedTransaction txId = subFlow(new UpdateEvolvableToken(oldBondStateAndRef, newBondState, bondObservers));
                progressTracker.setCurrentStep(SIGNING_TRANSACTIONS);
                subFlow(new FinalityFlow(txId, ImmutableList.of(bondHolderSession)));
            }
            progressTracker.setCurrentStep(UPDATE_TERM_STATES);
            TermState newTermState = new TermState
                    (termState.getIssuer(), termState.getInvestors(), termState.getBondName(),
                            BondStatus.MATURED.name(),termState.getInterestRate(), termState.getParValue(),
                            0, termState.getRedemptionAvailable(), termState.getLinearId(),
                            termState.getMaturityDate(), termState.getBondType(), termState.getCurrency(),
                            termState.getCreditRating(), termState.getPaymentFrequencyInMonths());
            progressTracker.setCurrentStep(SIGNING_TRANSACTIONS);
            subFlow(new UpdateEvolvableToken(termStateRef, newTermState, bondObservers));
            progressTracker.setCurrentStep(DONE);
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

        private final ProgressTracker progressTracker = new ProgressTracker(
                IDENTIFY_OBSERVERS,
                QUERY_BOND_TOKENS_SIZE,
                SIGNING_TRANSACTIONS,
                REDEEM_TOKENS,
                SEND_REDEEM_PAYMENT_RESPONSE,
                DONE
        );
        private static final ProgressTracker.Step IDENTIFY_OBSERVERS = new ProgressTracker
                .Step("Identifying Observers");

        private static final ProgressTracker.Step QUERY_BOND_TOKENS_SIZE = new ProgressTracker
                .Step("Query Bond Tokens held");

        private static final ProgressTracker.Step REDEEM_TOKENS = new ProgressTracker
                .Step("Redeem Bond Tokens");

        private static final ProgressTracker.Step SIGNING_TRANSACTIONS = new ProgressTracker
                .Step("Signing Transaction");

        private static final ProgressTracker.Step SEND_REDEEM_PAYMENT_RESPONSE = new ProgressTracker
                .Step("Send Bond Tokens size held by Bond Holder");

        private static final ProgressTracker.Step DONE = new ProgressTracker
                .Step("Done Processing Responder Flow");

        public RedeemCouponResponder(FlowSession bondHolderSession){
            this.bondHolderSession = bondHolderSession;
        }
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            Party bondHolder = getOurIdentity();
            Party bondIssuer = bondHolderSession.getCounterparty();
            IdentityService identityService = getServiceHub().getIdentityService();
            progressTracker.setCurrentStep(IDENTIFY_OBSERVERS);
            List<Party> observers = Utility.getLegalIdentitiesByOU(identityService,"Observer");
            List<StateAndRef<TermState>> investorTermStateRefList =
                    subFlow(new ReceiveStateAndRefFlow<>(bondHolderSession));
            StateAndRef<TermState> investorTermStateRef = investorTermStateRefList.get(0);
            TermState termState = investorTermStateRef.getState().getData();
            CouponPaymentFlow.CouponPaymentNotification cpn =
                    bondHolderSession.receive(CouponPaymentFlow.CouponPaymentNotification.class)
                            .unwrap(it -> it);

            progressTracker.setCurrentStep(QUERY_BOND_TOKENS_SIZE);
            Long numberOfToken;
            boolean isRedeemed = false;
            if(cpn.isRedeem()){
                Amount<TokenType> amount = QueryUtilities.tokenBalanceForIssuer(getServiceHub().getVaultService(),
                        investorTermStateRef.getState().getData().toPointer(), termState.getIssuer());
                numberOfToken = amount.getQuantity();
                progressTracker.setCurrentStep(REDEEM_TOKENS);
                progressTracker.setCurrentStep(SIGNING_TRANSACTIONS);
                subFlow(new RedeemFungibleTokens(amount, termState.getIssuer(), observers));
                isRedeemed = true;
            }else {
                numberOfToken = subFlow(new QueryBondToken.GetTokenBalance(cpn.getTermLinearId()));
            }
            progressTracker.setCurrentStep(SEND_REDEEM_PAYMENT_RESPONSE);
            bondHolderSession.send(new CouponPaymentFlow.CouponPaymentNotification(cpn.getIssuer(),
                    numberOfToken,
                    "OK",
                    cpn.getBondLinearID(),
                    cpn.getTermLinearId(), isRedeemed));
            progressTracker.setCurrentStep(DONE);
            return subFlow(new ReceiveFinalityFlow(bondHolderSession));
        }
    }
}
