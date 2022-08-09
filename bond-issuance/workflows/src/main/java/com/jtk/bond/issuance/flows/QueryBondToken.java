package com.jtk.bond.issuance.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.jtk.bond.issuance.flows.utils.CustomQuery;
import com.jtk.bond.issuance.state.BondState;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilities;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.utilities.ProgressTracker;

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
}
