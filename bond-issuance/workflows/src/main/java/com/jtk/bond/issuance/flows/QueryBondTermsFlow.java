package com.jtk.bond.issuance.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.jtk.bond.issuance.flows.utils.CustomQuery;
import com.jtk.bond.issuance.state.TermState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.utilities.ProgressTracker;

import java.util.stream.Collectors;

public class QueryBondTermsFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class GetBondTermsByCurrency extends FlowLogic<String>{
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final String currency;

        public GetBondTermsByCurrency(String currency) {
            this.currency = currency;
        }
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }
        @Override
        @Suspendable
        public String call() {
            return CustomQuery.queryTermsPointerByCurrency(currency, getServiceHub())
                    .stream()
                    .map(TermState::toJson)
                    .collect(Collectors.toList())
                    .toString();
        }
    }

    @InitiatingFlow
    @StartableByRPC
    public static class GetBondTermsByRating extends FlowLogic<String>{
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final String creditRating;

        public GetBondTermsByRating(String creditRating) {
            this.creditRating = creditRating;
        }
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }
        @Override
        @Suspendable
        public String call() {
            return CustomQuery.queryTermsPointerByCreditRating(creditRating, getServiceHub())
                    .stream()
                    .map(TermState::toJson)
                    .collect(Collectors.toList())
                    .toString();
        }
    }

    @InitiatingFlow
    @StartableByRPC
    public static class GetBondTermsLessThanMaturityDate extends FlowLogic<String>{
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final String maturityDate;

        public GetBondTermsLessThanMaturityDate(String maturityDate) {
            this.maturityDate = maturityDate;
        }
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }
        @Override
        @Suspendable
        public String call() {
            return CustomQuery.queryTermsPointerLessThanMaturityDate(maturityDate, getServiceHub())
                    .stream().map(TermState::toJson)
                    .collect(Collectors.toList())
                    .toString();
        }
    }

    @InitiatingFlow
    @StartableByRPC
    public static class GetBondTermsGreaterThanMaturityDate extends FlowLogic<String>{
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final String maturityDate;

        public GetBondTermsGreaterThanMaturityDate(String maturityDate) {
            this.maturityDate = maturityDate;
        }
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }
        @Override
        @Suspendable
        public String call() {
            return CustomQuery.queryTermsPointerGreaterThanMaturityDate(maturityDate, getServiceHub())
                    .stream().map(TermState::toJson)
                    .collect(Collectors.toList())
                    .toString();
        }
    }

    @InitiatingFlow
    @StartableByRPC
    public static class GetBondTermByTeamStateLinearID extends FlowLogic<String>{
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final UniqueIdentifier teamStateLinearID;

        public GetBondTermByTeamStateLinearID(UniqueIdentifier teamStateLinearID) {
            this.teamStateLinearID = teamStateLinearID;
        }
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }
        @Override
        @Suspendable
        public String call() {
            StateAndRef<TermState> termSateAndRef = CustomQuery.queryTermsByTeamStateLinearID(teamStateLinearID, getServiceHub());
            return termSateAndRef.getState().getData().toPointer().getPointer()
                    .resolve(getServiceHub()).getState().getData().toJson();
        }
    }
}