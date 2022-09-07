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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InitiatedBy(CouponPaymentFlow.class)
public class CouponPaymentResponderFlow extends FlowLogic<SignedTransaction> {
    private static final Logger log = LoggerFactory.getLogger(CouponPaymentResponderFlow.class);
    private final FlowSession bondHolderSession;

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
        Long numberOfToken = subFlow(new QueryBondToken.GetTokenBalance(cpn.getTermLinearId()));
        bondHolderSession.send(new CouponPaymentFlow.CouponPaymentNotification(cpn.getIssuer(),
                numberOfToken,
                "OK",
                cpn.getBondLinearID(),
                cpn.getTermLinearId(), false));
        return subFlow(new ReceiveFinalityFlow(bondHolderSession));
    }
}
