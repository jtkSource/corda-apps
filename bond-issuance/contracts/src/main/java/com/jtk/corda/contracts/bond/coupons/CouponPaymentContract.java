package com.jtk.corda.contracts.bond.coupons;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

public class CouponPaymentContract implements Contract {
    public final static String contractID = "com.jtk.corda.contracts.bond.coupons.CouponPaymentContract";
    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {

    }
    public interface Commands extends CommandData {
        class CheckForPayments implements Commands {}
    }
}
