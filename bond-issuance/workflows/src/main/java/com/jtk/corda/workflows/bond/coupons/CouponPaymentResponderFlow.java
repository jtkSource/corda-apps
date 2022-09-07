package com.jtk.corda.workflows.bond.coupons;

import co.paralleluniverse.fibers.Suspendable;
import com.jtk.corda.workflows.bond.issuance.QueryBondToken;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.flows.ReceiveFinalityFlow;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InitiatedBy(CouponPaymentFlow.class)
public class CouponPaymentResponderFlow extends FlowLogic<SignedTransaction> {
    private static final Logger log = LoggerFactory.getLogger(CouponPaymentResponderFlow.class);
    private final FlowSession bondHolderSession;

    private final ProgressTracker progressTracker = new ProgressTracker(
            QUERY_BOND_TOKENS_SIZE,
            SIGNING_TRANSACTIONS,
            SEND_COUPON_PAYMENT_RESPONSE,
            DONE
    );

    private static final ProgressTracker.Step QUERY_BOND_TOKENS_SIZE = new ProgressTracker
            .Step("Query Bond Tokens held");

    private static final ProgressTracker.Step SIGNING_TRANSACTIONS = new ProgressTracker
            .Step("Signing Transaction");
    private static final ProgressTracker.Step SEND_COUPON_PAYMENT_RESPONSE = new ProgressTracker
            .Step("Send Bond Tokens size held by Bond Holder");
    private static final ProgressTracker.Step DONE = new ProgressTracker
            .Step("Done Processing Responder Flow");

    public CouponPaymentResponderFlow(FlowSession bondHolderSession){
        this.bondHolderSession = bondHolderSession;
    }

    @Override
    @Suspendable
    public SignedTransaction call() throws FlowException {
        Party bondHolder = getOurIdentity();
        Party bondIssuer = bondHolderSession.getCounterparty();
        CouponPaymentFlow.CouponPaymentNotification cpn =
                bondHolderSession.receive(CouponPaymentFlow.CouponPaymentNotification.class).unwrap(it -> it);
        progressTracker.setCurrentStep(QUERY_BOND_TOKENS_SIZE);

        Long numberOfToken = subFlow(new QueryBondToken.GetTokenBalance(cpn.getTermLinearId()));
        progressTracker.setCurrentStep(SEND_COUPON_PAYMENT_RESPONSE);

        bondHolderSession.send(new CouponPaymentFlow.CouponPaymentNotification(cpn.getIssuer(),
                numberOfToken,
                "OK",
                cpn.getBondLinearID(),
                cpn.getTermLinearId(), false));
        progressTracker.setCurrentStep(DONE);
        return subFlow(new ReceiveFinalityFlow(bondHolderSession));
    }
}
