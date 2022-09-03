package com.jtk.corda.states.bond.coupons;

import com.jtk.corda.contracts.bond.coupons.CouponPaymentContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.SchedulableState;
import net.corda.core.contracts.ScheduledActivity;
import net.corda.core.contracts.StateRef;
import net.corda.core.flows.FlowLogicRefFactory;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.serialization.ConstructorForDeserialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@BelongsToContract(CouponPaymentContract.class)
public class CouponPaymentState implements SchedulableState {
    private static final Logger log = LoggerFactory.getLogger(CouponPaymentState.class);
    private Party issuer;
    private int durationInSecs;
    private final Instant nextActivityTime;
    public CouponPaymentState(Party issuer, int durationInSecs) {
        this.issuer = issuer;
        this.durationInSecs = durationInSecs;
        this.nextActivityTime = Instant.now().plusSeconds(durationInSecs);
    }

    @ConstructorForDeserialization
    public CouponPaymentState(Party issuer , int durationInSecs, Instant nextActivityTime) {
        this.issuer = issuer;
        this.durationInSecs = durationInSecs;
        this.nextActivityTime = nextActivityTime;
    }

    @Override
    public List<AbstractParty> getParticipants() {
        return Collections.singletonList(issuer);
    }

    public Party getIssuer() {
        return issuer;
    }

    public int getDurationInSecs() {
        return durationInSecs;
    }

    public Instant getNextActivityTime() {
        return nextActivityTime;
    }

    @Override
    public ScheduledActivity nextScheduledActivity(StateRef thisStateRef, FlowLogicRefFactory flowLogicRefFactory) {
        log.info("");
        return new ScheduledActivity(flowLogicRefFactory.
                create("com.jtk.corda.workflows.bond.coupons.CouponPaymentFlow", thisStateRef), nextActivityTime);
    }
}
