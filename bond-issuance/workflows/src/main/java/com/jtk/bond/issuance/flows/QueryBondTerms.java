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
            return CustomQuery.queryTermsPointerByCurrency(currency, getServiceHub())
                    .stream()
                    .map(TermState::toJson)
                    .collect(Collectors.toList())
                    .toString();
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
            return CustomQuery.queryTermsPointerByCreditRating(creditRating, getServiceHub())
                    .stream()
                    .map(TermState::toJson)
                    .collect(Collectors.toList())
                    .toString();
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
            return CustomQuery.queryTermsPointerLessThanMaturityDate(maturityDate, getServiceHub())
                    .stream().map(TermState::toJson)
                    .collect(Collectors.toList())
                    .toString();
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
            return CustomQuery.queryTermsPointerGreaterThanMaturityDate(maturityDate, getServiceHub())
                    .stream().map(TermState::toJson)
                    .collect(Collectors.toList())
                    .toString();
        }
    }

    @InitiatingFlow
    @StartableByRPC
    public static class GetBondByTeamStateLinearID extends FlowLogic<String>{
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final UniqueIdentifier teamStateLinearID;

        public GetBondByTeamStateLinearID(UniqueIdentifier teamStateLinearID) {
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
