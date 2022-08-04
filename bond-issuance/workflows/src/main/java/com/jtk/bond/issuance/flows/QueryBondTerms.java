package com.jtk.bond.issuance.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.jtk.bond.issuance.flows.utils.CustomQuery;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.utilities.ProgressTracker;

public class QueryBondTerms {

    @InitiatingFlow
    @StartableByRPC
    public static class GetBondByCurrency extends FlowLogic<String>{
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final String currency;

        public GetBondByCurrency(String currency) {
            this.currency = currency;
        }
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }
        @Override
        @Suspendable
        public String call() {
            return CustomQuery.queryTermsPointerByCurrency(currency, getServiceHub()).toString();
        }
    }
}
