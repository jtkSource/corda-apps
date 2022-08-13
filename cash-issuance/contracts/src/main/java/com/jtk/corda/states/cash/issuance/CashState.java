package com.jtk.corda.states.cash.issuance;

import com.google.common.collect.ImmutableList;
import com.jtk.corda.contracts.cash.issuance.CashStateContract;
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearPointer;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.Party;
import net.corda.core.schemas.StatePersistable;
import net.corda.core.serialization.CordaSerializable;

import java.util.Currency;
import java.util.List;

@CordaSerializable
@BelongsToContract(CashStateContract.class)
public class CashState extends EvolvableTokenType implements StatePersistable {
    private final String currencyCode;
    private final double usdPairRate;
    private final int fractionalDigits;
    private final UniqueIdentifier linearStateId;
    private final Party issuer;

    public CashState(String currencyCode, double usdPairRate, Party issuer, UniqueIdentifier linearStateId) {
        this.currencyCode = currencyCode;
        this.fractionalDigits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
        this.usdPairRate = usdPairRate;
        this.linearStateId = linearStateId;
        this.issuer = issuer;
    }

    @Override
    public int getFractionDigits() {
        return fractionalDigits;
    }

    @Override
    public List<Party> getMaintainers() {
        return ImmutableList.of(issuer);
    }

    @Override
    public UniqueIdentifier getLinearId() {
        return linearStateId;
    }

    public Party getIssuer() {
        return issuer;
    }

    public double getUsdPairRate() {
        return usdPairRate;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public UniqueIdentifier getLinearStateId() {
        return linearStateId;
    }

    public int getFractionalDigits() {
        return fractionalDigits;
    }

    public TokenPointer<CashState> toPointer(){
        return new TokenPointer<>(new LinearPointer<>(linearStateId, CashState.class), fractionalDigits);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CashState cashSate = (CashState) o;

        if (!currencyCode.equals(cashSate.currencyCode)) return false;
        if (!linearStateId.equals(cashSate.linearStateId)) return false;
        return issuer.equals(cashSate.issuer);
    }

    @Override
    public int hashCode() {
        int result = currencyCode.hashCode();
        result = 31 * result + linearStateId.hashCode();
        result = 31 * result + issuer.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("{ " +
                "\"currencyCode\":\"%s\"," +
                "\"linearStateId\":\"%s\"," +
                "\"issuer\":\"%s\"," +
                "\"usdPairRate\":\"%f\"," +
                " }",currencyCode,linearStateId,
                issuer.getName().getCommonName(),
                usdPairRate);
    }
}
