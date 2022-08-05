package com.jtk.bond.issuance.state;

import com.google.common.collect.ImmutableList;
import com.jtk.bond.issuance.contract.BondContract;
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearPointer;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.Party;
import net.corda.core.schemas.StatePersistable;
import net.corda.core.serialization.CordaSerializable;

import java.util.List;

@CordaSerializable
@BelongsToContract(BondContract.class)
public class BondState extends EvolvableTokenType implements StatePersistable  {

    private final int fractionDigits = 0;
    private final Party issuer;
    private final Party investor;
    private final double interestRate;
    private final double purchasePrice;
    private final String maturityDate;
    private final String creditRating;
    private final double couponPaymentLeft;
    private final String bondType;

    private final String currency;

    private final String bondStatus; // state of the bond

    private final String bondName;

    private final UniqueIdentifier teamStateLinearID;
    private final UniqueIdentifier linearId;

    public BondState(Party issuer, Party investor, double interestRate, double purchasePrice, String maturityDate,
                     String creditRating, double couponPaymentLeft,String bondStatus,String bondType,String currency,
                     String bondName, UniqueIdentifier teamStateLinearID,
                     UniqueIdentifier linearId) {
        this.issuer = issuer;
        this.investor = investor;
        this.interestRate = interestRate;
        this.purchasePrice = purchasePrice;
        this.maturityDate = maturityDate;
        this.creditRating = creditRating;
        this.bondStatus = bondStatus;
        this.bondType = bondType;
        this.currency = currency;
        this.bondName = bondName;
        this.couponPaymentLeft = couponPaymentLeft;
        this.teamStateLinearID = teamStateLinearID;
        this.linearId = linearId;
    }

    @Override
    public int getFractionDigits() {
        return this.fractionDigits;
    }

    @Override
    public List<Party> getMaintainers() {
        return ImmutableList.of(this.investor);
    }

    @Override
    public UniqueIdentifier getLinearId() {
        return this.linearId;
    }

    public Party getIssuer() {
        return issuer;
    }

    public Party getInvestor() {
        return investor;
    }

    public double getInterestRate() {
        return interestRate;
    }

    public double getPurchasePrice() {
        return purchasePrice;
    }

    public String getMaturityDate() {
        return maturityDate;
    }

    public String getCreditRating() {
        return creditRating;
    }

    public double getCouponPaymentLeft() {
        return couponPaymentLeft;
    }

    public String getBondStatus() {
        return bondStatus;
    }

    public String getBondType() {
        return bondType;
    }

    public String getCurrency() {
        return currency;
    }

    public String getBondName() {
        return bondName;
    }

    public UniqueIdentifier getTeamStateLinearID() {
        return teamStateLinearID;
    }

    public TokenPointer<BondState> toPointer(){
        return new TokenPointer<>(new LinearPointer<>(linearId, BondState.class), fractionDigits);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BondState bondState = (BondState) o;

        if (!issuer.equals(bondState.issuer)) return false;
        if (!currency.equals(bondState.currency)) return false;
        if (!bondName.equals(bondState.bondName)) return false;
        if (!investor.equals(bondState.investor)) return false;
        if (!teamStateLinearID.equals(bondState.teamStateLinearID)) return false;
        return linearId.equals(bondState.linearId);
    }

    @Override
    public int hashCode() {
        int result = issuer.hashCode();
        result = 31 * result + investor.hashCode();
        result = 31 * result + currency.hashCode();
        result = 31 * result + bondName.hashCode();
        result = 31 * result + teamStateLinearID.hashCode();
        result = 31 * result + linearId.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("BondState{");
        sb.append("issuer=").append(issuer);
        sb.append(", investor=").append(investor);
        sb.append(", interestRate=").append(interestRate);
        sb.append(", purchasePrice=").append(purchasePrice);
        sb.append(", maturityDate='").append(maturityDate).append('\'');
        sb.append(", creditRating='").append(creditRating).append('\'');
        sb.append(", couponPaymentLeft=").append(couponPaymentLeft);
        sb.append(", bondType='").append(bondType).append('\'');
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", bondStatus='").append(bondStatus).append('\'');
        sb.append(", bondName='").append(bondName).append('\'');
        sb.append(", teamStateLinearID=").append(teamStateLinearID);
        sb.append(", linearId=").append(linearId);
        sb.append('}');
        return sb.toString();
    }
}
