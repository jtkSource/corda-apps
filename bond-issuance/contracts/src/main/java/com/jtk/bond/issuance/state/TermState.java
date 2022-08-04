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
    private final Currency currency;
    private final int couponPaymentLeft; // number of coupon payments left
    private final double interestRate; // current interest rate for the bonds
    private final double purchasePrice; // current price of the bond
    private final LocalDate maturityDate; // Maturity date of the bond
    private final BondCreditRating creditRating; // credit rating of the bond
    private final int unitsAvailable; // number of units available - reduces with every bond that is brought
    private final int redemptionAvailable; // number of units that have to be redeemed - increased with every bond brought
    private final BondType bondType;
    private final UniqueIdentifier linearId; // identifier of the bond

    final DateTimeFormatter locateDateformat = DateTimeFormatter.ofPattern("yyyyMMdd");

    public TermState(Party issuer, List<Party> investors, String bondName, String bondState,
                     String bondType, String currency,
                     int couponPaymentLeft, double interestRate, double purchasePrice, String maturityDate,
                     String creditRating, int unitsAvailable, int redemptionAvailable, UniqueIdentifier linearId) {
        this.issuer = issuer;
        this.investors = investors;
        this.bondState = bondState;
        this.bondType = BondType.lookupRating(bondType).orElse(BondType.NA);
        this.bondName = bondName;
        this.currency = Currency.getInstance(currency);
        this.couponPaymentLeft = couponPaymentLeft;
        this.interestRate = interestRate;
        this.purchasePrice = purchasePrice;
        this.maturityDate = LocalDate.parse(maturityDate, locateDateformat);
        this.creditRating = BondCreditRating.lookupRating(creditRating).orElse(BondCreditRating.NA);
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

    public LocalDate getMaturityDate() {
        return maturityDate;
    }

    public BondCreditRating getCreditRating() {
        return creditRating;
    }

    public int getUnitsAvailable() {
        return unitsAvailable;
    }

    public int getRedemptionAvailable() {
        return redemptionAvailable;
    }

    public BondType getBondType() {
        return bondType;
    }

    public String getBondName() {
        return bondName;
    }

    public Currency getCurrency() {
        return currency;
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
        if (!currency.equals(termState.currency)) return false;
        if (bondType != termState.bondType) return false;
        return linearId.equals(termState.linearId);
    }

    @Override
    public int hashCode() {
        int result = issuer.hashCode();
        result = 31 * result + bondName.hashCode();
        result = 31 * result + currency.hashCode();
        result = 31 * result + bondType.hashCode();
        result = 31 * result + linearId.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TermState{");
        sb.append("issuer=").append(issuer);
        sb.append(", investors=").append(investors);
        sb.append(", bondState='").append(bondState).append('\'');
        sb.append(", bondName='").append(bondName).append('\'');
        sb.append(", currency=").append(currency);
        sb.append(", couponPaymentLeft=").append(couponPaymentLeft);
        sb.append(", interestRate=").append(interestRate);
        sb.append(", purchasePrice=").append(purchasePrice);
        sb.append(", maturityDate=").append(maturityDate);
        sb.append(", creditRating=").append(creditRating);
        sb.append(", unitsAvailable=").append(unitsAvailable);
        sb.append(", redemptionAvailable=").append(redemptionAvailable);
        sb.append(", bondType=").append(bondType);
        sb.append(", linearId=").append(linearId);
        sb.append('}');
        return sb.toString();
    }
}
