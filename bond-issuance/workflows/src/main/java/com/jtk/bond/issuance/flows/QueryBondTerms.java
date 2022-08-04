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

    @InitiatingFlow
    @StartableByRPC
    public static class GetBondByRating extends FlowLogic<String>{
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final String creditRating;

        public GetBondByRating(String creditRating) {
            this.creditRating = creditRating;
        }
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }
        @Override
        @Suspendable
        public String call() {
            return CustomQuery.queryTermsPointerByCreditRating(creditRating, getServiceHub()).toString();
        }
    }

    @InitiatingFlow
    @StartableByRPC
    public static class GetBondLessThanMaturityDate extends FlowLogic<String>{
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final String maturityDate;

        public GetBondLessThanMaturityDate(String maturityDate) {
            this.maturityDate = maturityDate;
        }
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }
        @Override
        @Suspendable
        public String call() {
            return CustomQuery.queryTermsPointerLessThanMaturityDate(maturityDate, getServiceHub()).toString();
        }
    }

    @InitiatingFlow
    @StartableByRPC
    public static class GetBondGreaterThanMaturityDate extends FlowLogic<String>{
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final String maturityDate;

        public GetBondGreaterThanMaturityDate(String maturityDate) {
            this.maturityDate = maturityDate;
        }
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }
        @Override
        @Suspendable
        public String call() {
            return CustomQuery.queryTermsPointerGreaterThanMaturityDate(maturityDate, getServiceHub()).toString();
        }
    }
}
