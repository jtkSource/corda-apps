package com.jtk.corda.workflows.bond.coupons;

import co.paralleluniverse.fibers.Suspendable;
import com.jtk.corda.states.bond.issuance.BondState;
import com.jtk.corda.workflows.bond.issuance.QueryBondToken;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatedBy;
import net.corda.core.flows.ReceiveFinalityFlow;
import net.corda.core.flows.ReceiveStateAndRefFlow;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
        List<StateAndRef<BondState>> holderSessionBondState = subFlow(new ReceiveStateAndRefFlow<>(bondHolderSession));
        if(holderSessionBondState.size()!=1){
            throw new FlowException("Only one bondstate expected");
        }
        UniqueIdentifier bondUniqueLinearId = holderSessionBondState.get(0).getState().getData().getLinearId();

        CouponPaymentFlow.CouponPaymentNotification couponPaymentNotification =
                bondHolderSession.receive(CouponPaymentFlow.CouponPaymentNotification.class).unwrap(it -> it);
        Long bondTokens = subFlow(new QueryBondToken.GetTokenBalance(bondUniqueLinearId.toString()));
        bondHolderSession.send(new CouponPaymentFlow.CouponPaymentNotification(couponPaymentNotification.getIssuer(), bondTokens,"OK"));
        return subFlow(new ReceiveFinalityFlow(bondHolderSession));
    }
}
