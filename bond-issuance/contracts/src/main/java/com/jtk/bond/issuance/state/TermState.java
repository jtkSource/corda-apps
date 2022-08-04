package com.jtk.bond.issuance.state;

import com.google.common.collect.ImmutableList;
import com.jtk.bond.issuance.contract.contants.BondCreditRating;
import com.jtk.bond.issuance.contract.contants.BondType;
import com.jtk.bond.issuance.contract.TermContract;
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearPointer;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.Party;
import net.corda.core.schemas.StatePersistable;
import net.corda.core.serialization.CordaSerializable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;

/**
 * - Issuer issues a term state which records the bonds properties
 * - Data from each term state is displayed on a UI for investors to browse
 * - Once an investor find a bond they are interested in, they can request it
 * - The request will generate a BondState which has a copy of the terms and is shared with the Issuer
 * and the Investor
 */
@CordaSerializable
@BelongsToContract(TermContract.class)
public class TermState extends EvolvableTokenType implements StatePersistable {
    private final int fractionDigits = 0;
    private final Party issuer; // Issuer of the bond
    private final List<Party> investors; // the investors who brought the bond
    private final String bondState; // state of the bond
    private final String bondName; // Name of bond cannot be changed

    private final String currency;
    private final int couponPaymentLeft; // number of coupon payments left
    private final double interestRate; // current interest rate for the bonds
    private final double purchasePrice; // current price of the bond
    private final String maturityDate; // Maturity date of the bond

    private final String creditRating; // credit rating of the bond
    private final int unitsAvailable; // number of units available - reduces with every bond that is brought
    private final int redemptionAvailable; // number of units that have to be redeemed - increased with every bond brought
    private final String bondType;
    private final UniqueIdentifier linearId; // identifier of the bond

    public TermState(Party issuer, List<Party> investors, String bondName, String bondState,
                     int couponPaymentLeft, double interestRate, double purchasePrice,
                     int unitsAvailable, int redemptionAvailable, UniqueIdentifier linearId,
                     String maturityDate, String bondType, String currency, String creditRating) {
        this.issuer = issuer;
        this.investors = investors;
        this.bondState = bondState;
        this.bondType = bondType;
        this.bondName = bondName;
        this.currency = currency;
        this.couponPaymentLeft = couponPaymentLeft;
        this.interestRate = interestRate;
        this.purchasePrice = purchasePrice;
        this.maturityDate = maturityDate;
        this.creditRating = creditRating;
        this.unitsAvailable = unitsAvailable;
        this.redemptionAvailable = redemptionAvailable;
        this.linearId = linearId;
    }

    @Override
    public int getFractionDigits() {
        return this.fractionDigits;
    }

    @Override
    public List<Party> getMaintainers() {
        return ImmutableList.of(issuer);
    }

    @Override
    public UniqueIdentifier getLinearId() {
        return this.linearId;
    }


    public Party getIssuer() {
        return issuer;
    }

    public List<Party> getInvestors() {
        return investors;
    }

    public String getBondState() {
        return bondState;
    }

    public int getCouponPaymentLeft() {
        return couponPaymentLeft;
    }

    public double getInterestRate() {
        return interestRate;
    }

    public double getPurchasePrice() {
        return purchasePrice;
    }

    public int getUnitsAvailable() {
        return unitsAvailable;
    }

    public int getRedemptionAvailable() {
        return redemptionAvailable;
    }


    public String getBondName() {
        return bondName;
    }

    public String getBondType() {
        return bondType;
    }

    public String getMaturityDate() {
        return maturityDate;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCreditRating() {
        return creditRating;
    }

    /* This method returns a TokenPointer by using the linear Id of the evolvable state */
    public TokenPointer<TermState> toPointer(){
        return new TokenPointer<>(new LinearPointer<>(linearId, TermState.class), fractionDigits);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TermState termState = (TermState) o;

        if (!issuer.equals(termState.issuer)) return false;
        if (!bondName.equals(termState.bondName)) return false;
        return linearId.equals(termState.linearId);
    }

    @Override
    public int hashCode() {
        int result = issuer.hashCode();
        result = 31 * result + bondName.hashCode();
        result = 31 * result + linearId.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TermState{");
        sb.append("issuer=").append(issuer);
        sb.append(", bondState='").append(bondState).append('\'');
        sb.append(", bondName='").append(bondName).append('\'');
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", couponPaymentLeft=").append(couponPaymentLeft);
        sb.append(", interestRate=").append(interestRate);
        sb.append(", purchasePrice=").append(purchasePrice);
        sb.append(", maturityDate='").append(maturityDate).append('\'');
        sb.append(", creditRating='").append(creditRating).append('\'');
        sb.append(", unitsAvailable=").append(unitsAvailable);
        sb.append(", redemptionAvailable=").append(redemptionAvailable);
        sb.append(", bondType='").append(bondType).append('\'');
        sb.append(", linearId=").append(linearId);
        sb.append('}');
        return sb.toString();
    }
}
