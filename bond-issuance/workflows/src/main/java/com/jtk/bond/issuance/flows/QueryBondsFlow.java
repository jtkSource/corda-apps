package com.jtk.bond.issuance.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.jtk.bond.issuance.flows.utils.CustomQuery;
import com.jtk.bond.issuance.state.BondState;
import com.jtk.bond.issuance.state.TermState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.utilities.ProgressTracker;

public class QueryBondsFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class GetBondByTermStateLinearID extends FlowLogic<String> {
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final UniqueIdentifier teamStateLinearID;

        public GetBondByTermStateLinearID(UniqueIdentifier teamStateLinearID) {
            this.teamStateLinearID = teamStateLinearID;
        }
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }
        @Override
        @Suspendable
        public String call() {
            StateAndRef<BondState> termSateAndRef = CustomQuery.queryBondByTeamStateLinearID(teamStateLinearID, getServiceHub());
            return termSateAndRef.getState().getData().toPointer().getPointer()
                    .resolve(getServiceHub()).getState().getData().toJson();
        }
    }
}
