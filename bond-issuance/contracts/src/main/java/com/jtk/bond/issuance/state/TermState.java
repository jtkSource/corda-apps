package com.jtk.bond.issuance.state;

import com.google.common.collect.ImmutableList;
import com.jtk.bond.issuance.contract.TermContract;
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearPointer;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.Party;
import net.corda.core.schemas.StatePersistable;
import net.corda.core.serialization.CordaSerializable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final Set<Party> investors; // the investors who brought the bond
    private final String bondStatus; // state of the bond
    private final String bondName; // Name of bond cannot be changed
    private final String currency;
    private final double interestRate; // current interest rate for the bonds
    private final int parValue; // current price of the bond - faceValue
    private final String maturityDate; // Maturity date of the bond
    private final String creditRating; // credit rating of the bond
    private final int totalUnits; // the total number of units allowed by this term when created = unitsAvailable
    private final int unitsAvailable; // number of units available - reduces with every bond state that is created
                                      //  value = totalUnits...0
    private final int redemptionAvailable; // number of units that have to be redeemed - increased with every bond brought
                                           //  value = 0...totalUnits

    private final int paymentFrequencyInMonths;
    private final String bondType;
    private final UniqueIdentifier linearId; // identifier of the bond
    public TermState(Party issuer, Set<Party> investors, String bondName, String bondStatus,
                     double interestRate, int parValue,
                     int unitsAvailable, int redemptionAvailable, UniqueIdentifier linearId,
                     String maturityDate, String bondType, String currency, String creditRating,
                     int paymentFrequencyInMonths) {
        this.issuer = issuer;
        this.investors = investors;
        this.bondStatus = bondStatus;
        this.bondType = bondType;
        this.bondName = bondName;
        this.currency = currency;
        this.paymentFrequencyInMonths = paymentFrequencyInMonths;
        this.interestRate = interestRate;
        this.parValue = parValue;
        this.maturityDate = maturityDate;
        this.creditRating = creditRating;
        this.unitsAvailable = unitsAvailable;
        this.redemptionAvailable = redemptionAvailable;
        this.totalUnits = unitsAvailable + redemptionAvailable;
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

    public Set<Party> getInvestors() {
        return investors;
    }

    public String getBondStatus() {
        return bondStatus;
    }

    public double getInterestRate() {
        return interestRate;
    }

    public int getParValue() {
        return parValue;
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

    public int getTotalUnits() {
        return totalUnits;
    }

    public int getPaymentFrequencyInMonths() {
        return paymentFrequencyInMonths;
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
        if (!(paymentFrequencyInMonths == termState.paymentFrequencyInMonths)) return false;
        return linearId.equals(termState.linearId);
    }

    @Override
    public int hashCode() {
        int result = issuer.hashCode();
        result = 31 * result + bondName.hashCode();
        result = 31 * result + linearId.hashCode();
        result = 31 * result + paymentFrequencyInMonths;
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("TermState{");
        sb.append("issuer=").append(issuer);
        sb.append(", bondStatus='").append(bondStatus).append('\'');
        sb.append(", bondName='").append(bondName).append('\'');
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", interestRate=").append(interestRate);
        sb.append(", parValue=").append(parValue);
        sb.append(", maturityDate='").append(maturityDate).append('\'');
        sb.append(", creditRating='").append(creditRating).append('\'');
        sb.append(", totalUnits=").append(totalUnits);
        sb.append(", paymentFrequencyInMonths='").append(paymentFrequencyInMonths);
        sb.append(", unitsAvailable=").append(unitsAvailable);
        sb.append(", redemptionAvailable=").append(redemptionAvailable);
        sb.append(", bondType='").append(bondType).append('\'');
        sb.append(", linearId=").append(linearId);
        sb.append('}');
        return sb.toString();
    }

    public String toJson(){

        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"issuer\":").append("\"").append(issuer).append("\"");
        String invList = this.investors.stream().map(party -> party.getName().getCommonName())
                .collect(Collectors.joining("\",\"","\"","\""));
        sb.append(",\"investors\":");
        if(invList.length() != 0) {
            sb.append("[").append(invList).append("]");
        }else {
            sb.append("[]");
        }
        sb.append(",\"bondStatus\":").append("\"").append(bondStatus).append("\"");
        sb.append(",\"bondName\":").append("\"").append(bondName).append("\"");
        sb.append(",\"currency\":").append("\"").append(currency).append("\"");
        sb.append(",\"interestRate\":").append(interestRate);
        sb.append(",\"parValue\":").append(parValue);
        sb.append(",\"maturityDate\":").append(maturityDate);
        sb.append(",\"creditRating\":").append("\"").append(creditRating).append("\"");
        sb.append(",\"totalUnits\":").append(totalUnits);
        sb.append(",\"paymentFrequencyInMonths\":").append(paymentFrequencyInMonths);
        sb.append(",\"unitsAvailable\":").append(unitsAvailable);
        sb.append(",\"redemptionAvailable\":").append(redemptionAvailable);
        sb.append(",\"bondType\":").append("\"").append(bondType).append("\"");
        sb.append(",\"linearId\":").append("\"").append(linearId).append("\"");
        sb.append('}');
        return sb.toString();
    }
}
