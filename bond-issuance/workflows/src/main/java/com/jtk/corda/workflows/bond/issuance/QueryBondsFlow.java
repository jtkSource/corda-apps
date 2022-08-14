package com.jtk.corda.workflows.bond.issuance;

import co.paralleluniverse.fibers.Suspendable;
import com.jtk.corda.workflows.utils.CustomQuery;
import com.jtk.corda.states.bond.issuance.BondState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.utilities.ProgressTracker;

import java.util.stream.Collectors;

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
            return CustomQuery.queryBondByTermStateLinearID(teamStateLinearID, getServiceHub())
                    .stream()
                    .map(BondState::toJson)
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
            return CustomQuery.queryBondsPointerLessThanMaturityDate(maturityDate, getServiceHub())
                    .stream().map(BondState::toJson)
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
            return CustomQuery.queryBondsPointerGreaterThanMaturityDate(maturityDate, getServiceHub())
                    .stream().map(BondState::toJson)
                    .collect(Collectors.toList())
                    .toString();
        }
    }

    @InitiatingFlow
    @StartableByRPC
    public static class GetBondWithMaturityDate extends FlowLogic<String>{
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final String maturityDate;

        public GetBondWithMaturityDate(String maturityDate) {
            this.maturityDate = maturityDate;
        }
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }
        @Override
        @Suspendable
        public String call() {
            return CustomQuery.queryBondsPointerGreaterThanMaturityDate(maturityDate, getServiceHub())
                    .stream().map(BondState::toJson)
                    .collect(Collectors.toList())
                    .toString();
        }
    }


    @InitiatingFlow
    @StartableByRPC
    public static class GetBondsByCurrency extends FlowLogic<String>{
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final String currency;

        public GetBondsByCurrency(String currency) {
            this.currency = currency;
        }
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }
        @Override
        @Suspendable
        public String call() {
            return CustomQuery.queryBondsPointerByCurrency(currency, getServiceHub())
                    .stream()
                    .map(BondState::toJson)
                    .collect(Collectors.toList())
                    .toString();
        }
    }

    @InitiatingFlow
    @StartableByRPC
    public static class GetBondsByRating extends FlowLogic<String>{
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final String creditRating;

        public GetBondsByRating(String creditRating) {
            this.creditRating = creditRating;
        }
        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }
        @Override
        @Suspendable
        public String call() {
            return CustomQuery.queryBondPointerByCreditRating(creditRating, getServiceHub())
                    .stream()
                    .map(BondState::toJson)
                    .collect(Collectors.toList())
                    .toString();
        }
    }
}
