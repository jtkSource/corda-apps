package com.jtk.corda.cash.issuance.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.jtk.corda.cash.issuance.state.CashState;
import com.jtk.corda.cash.issuance.utils.CustomQuery;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilities;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.utilities.ProgressTracker;

import java.math.BigDecimal;
import java.util.List;

public class QueryCashTokenFlow {

    private static final BigDecimal ZERO = new BigDecimal("0.0");

    @InitiatingFlow
    @StartableByRPC
    public static class GetTokenBalance extends FlowLogic<BigDecimal> {
        private final ProgressTracker progressTracker = new ProgressTracker();
        private String currencyCode;

        public GetTokenBalance(String currencyCode) {
            this.currencyCode = currencyCode;
        }

        @Override
        @Suspendable
        public BigDecimal call() throws FlowException {
            List<CashState> cashStates = CustomQuery.queryCashStateByCurrency(currencyCode, getServiceHub());
            if (cashStates.size() > 0) {
                if (cashStates.size() != 1) {
                    throw new FlowException("There cannot be more than one cashflow state for the same currency");
                }
                TokenPointer<CashState> cashPointer = cashStates.get(0).toPointer();
                Amount<TokenType> bondTokenAmount = QueryUtilities.tokenBalance(getServiceHub().getVaultService(), cashPointer);
                return BigDecimal.valueOf((bondTokenAmount.getQuantity() * bondTokenAmount.getDisplayTokenSize().doubleValue()));
            } else return ZERO;
        }
    }

}
