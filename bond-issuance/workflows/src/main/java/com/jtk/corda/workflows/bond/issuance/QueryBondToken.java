package com.jtk.corda.workflows.bond.issuance;

import co.paralleluniverse.fibers.Suspendable;
import com.jtk.corda.states.bond.issuance.TermState;
import com.jtk.corda.workflows.utils.CustomQuery;
import com.jtk.corda.states.bond.issuance.BondState;
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

import java.util.List;

public class QueryBondToken {

    @InitiatingFlow
    @StartableByRPC
    public static class GetTokenBalance extends FlowLogic<Long> {
        private String termLinearID;

        public GetTokenBalance(String termLinearID) {
            this.termLinearID = termLinearID;
        }
        @Override
        @Suspendable
        public Long call() throws FlowException {
            TokenPointer<TermState> pointer = CustomQuery.
                    queryAllTermsByTermStateLinearID(UniqueIdentifier.Companion.fromString(termLinearID), getServiceHub())
                    .getState().getData().toPointer();
            Amount<TokenType> bondTokenAmount = QueryUtilities
                    .tokenBalance(getServiceHub().getVaultService(), pointer);
            return bondTokenAmount.getQuantity();
        }
    }

}
