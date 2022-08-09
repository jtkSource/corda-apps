package com.jtk.bond.issuance.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.jtk.bond.issuance.flows.utils.CustomQuery;
import com.jtk.bond.issuance.state.BondState;
import com.jtk.bond.issuance.state.TermState;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilities;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.utilities.ProgressTracker;

import java.util.List;
import java.util.stream.Collectors;

public class QueryBondToken {

    @InitiatingFlow
    @StartableByRPC
    public static class GetTokenBalance extends FlowLogic<Long> {
        private final ProgressTracker progressTracker = new ProgressTracker();
        private String bondLinearID;

        public GetTokenBalance(String bondLinearID) {
            this.bondLinearID = bondLinearID;
        }

        @Override
        @Suspendable
        public Long call() throws FlowException {
            TokenPointer<BondState> bondPointer = CustomQuery.queryBondByLinearID
                            (UniqueIdentifier.Companion.fromString(bondLinearID), getServiceHub())
                    .getState().getData()
                    .toPointer();
            Amount<TokenType> bondTokenAmount = QueryUtilities
                    .tokenBalance(getServiceHub().getVaultService(), bondPointer);
            return bondTokenAmount.getQuantity();
        }
    }


    @InitiatingFlow
    @StartableByRPC
    public static class GetTokenSum extends FlowLogic<Long> {
        private final ProgressTracker progressTracker = new ProgressTracker();
        private final String termLinearID;

        public GetTokenSum(String termLinearID) {
            this.termLinearID = termLinearID;
        }

        @Override
        @Suspendable
        public Long call() throws FlowException {
            List<BondState> bondList = CustomQuery.queryBondByTeamStateLinearID
                    (UniqueIdentifier.Companion.fromString(termLinearID), getServiceHub());

            return bondList.stream()
                    .map(BondState::getLinearId)
                    .map(uniqueIdentifier -> CustomQuery.queryBondByLinearID
                                    (uniqueIdentifier, getServiceHub())
                            .getState()
                            .getData().toPointer())
                    .map(bondPointer -> QueryUtilities.tokenBalance(getServiceHub().getVaultService(), bondPointer))
                    .map(Amount::getQuantity)
                    .reduce(0L, Long::sum);
        }
    }

}
