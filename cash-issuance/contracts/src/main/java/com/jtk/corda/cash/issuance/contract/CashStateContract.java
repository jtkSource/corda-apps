package com.jtk.corda.cash.issuance.contract;

import com.jtk.corda.cash.issuance.state.CashSate;
import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Currency;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class CashStateContract extends EvolvableTokenContract implements Contract {

    private static final Logger log = LoggerFactory.getLogger(CashStateContract.class);

    @Override
    public void verify(@NotNull LedgerTransaction tx) {
        log.info("Verifying the CashStateContract...");
        CashSate cashSate = (CashSate) tx.getOutput(0);
        if(!tx.getCommand(0).getSigners().contains(cashSate.getIssuer().getOwningKey())){
            throw new IllegalArgumentException("Issuer signature is required!");
        }
        super.verify(tx);
    }

    @Override
    public void additionalCreateChecks(@NotNull LedgerTransaction tx) {
        CashSate cashSate = tx.outputsOfType(CashSate.class).get(0);
        requireThat(req->{
            req.using("Currency code cannot be empty ", (cashSate.getCurrencyCode()!=null && cashSate.getCurrencyCode().length()>0));
            try {
                Currency currency = Currency.getInstance(cashSate.getCurrencyCode());
            }catch (Exception e){
                req.using("Currency code should be valid: " + cashSate.getCurrencyCode(), false);
            }
            req.using("Currency USD pair rate cannot be zero",cashSate.getUsdPairRate()!=0.0);

            return null;
        });

    }

    @Override
    public void additionalUpdateChecks(@NotNull LedgerTransaction tx) {
        CashSate inputState = tx.inputsOfType(CashSate.class).get(0);
        CashSate outputState = tx.outputsOfType(CashSate.class).get(0);
        requireThat(req->{
            req.using("Currency code cannot be changed", inputState.getCurrencyCode() == outputState.getCurrencyCode());
            req.using("Issuer cannot be changed", inputState.getIssuer() == outputState.getIssuer());
            return null;
        });
    }
}
