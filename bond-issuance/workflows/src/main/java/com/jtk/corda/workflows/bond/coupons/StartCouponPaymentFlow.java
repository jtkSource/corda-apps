package com.jtk.corda.workflows.bond.coupons;

import co.paralleluniverse.fibers.Suspendable;
import com.jtk.corda.contracts.bond.coupons.CouponPaymentContract;
import com.jtk.corda.states.bond.coupons.CouponPaymentState;
import net.corda.core.contracts.CommandData;
import net.corda.core.flows.*;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

import static com.jtk.corda.contracts.bond.coupons.CouponPaymentContract.Commands.*;

// TBD
public class StartCouponPaymentFlow extends FlowLogic<Void> {
    private static final Logger log = LoggerFactory.getLogger(StartCouponPaymentFlow.class);
    private final ProgressTracker progressTracker = tracker();

    private static final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating a CouponPaymentState transaction");
    private static final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction...");
    private static final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Recording transaction") {
        @Override
        public ProgressTracker childProgressTracker() {
            return FinalityFlow.tracker();
        }
    };

    private final int schedulePeriodInSeconds ;

    public StartCouponPaymentFlow(int schedulePeriodInSeconds) {
        this.schedulePeriodInSeconds = schedulePeriodInSeconds;
    }

    private static ProgressTracker tracker() {
        return new ProgressTracker(
                GENERATING_TRANSACTION,
                SIGNING_TRANSACTION,
                FINALISING_TRANSACTION
        );
    }

    @Override
    public Void call() throws FlowException {
        log.info("Start Coupon Payment every {} seconds", this.schedulePeriodInSeconds);
        progressTracker.setCurrentStep(GENERATING_TRANSACTION);
        CouponPaymentState couponPaymentState = new CouponPaymentState(getOurIdentity(), this.schedulePeriodInSeconds);
        CommandData cmd = new CheckForPayments();
        TransactionBuilder txBuilder = new TransactionBuilder(getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0))
                .addOutputState(couponPaymentState, CouponPaymentContract.contractID)
                .addCommand(cmd, getOurIdentity().getOwningKey());
        progressTracker.setCurrentStep(SIGNING_TRANSACTION);
        SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);
        progressTracker.setCurrentStep(FINALISING_TRANSACTION);
        subFlow(new FinalityFlow(signedTx, Collections.emptyList(), FINALISING_TRANSACTION.childProgressTracker()));
        return null;
    }
}
