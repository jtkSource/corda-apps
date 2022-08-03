package com.jtk.bond.issuance.flows;

import com.jtk.bond.contants.BondCreditRating;
import com.jtk.bond.contants.BondType;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.node.services.IdentityService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Currency;

/**
 * - Issuer issues a term state which records the bonds properties
 */

public class CreateAndIssueTerm extends FlowLogic<String> {
    private final String bondName; // Name of bond cannot be changed
    private final Currency currency;
    private final int couponPaymentLeft; // number of coupon payments left
    private final double interestRate; // current interest rate for the bonds
    private final double purchasePrice; // current price of the bond
    private final LocalDate maturityDate; // Maturity date of the bond
    private final BondCreditRating creditRating; // credit rating of the bond
    private final int unitsAvailable; // number of units available - reduces with every bond that is brought
    private final BondType bondType;

    final DateTimeFormatter locateDateformat = DateTimeFormatter.ofPattern("yyyyMMdd");

    public CreateAndIssueTerm(String bondName, String currencyCode, int couponPaymentLeft,
                              double interestRate, double purchasePrice, String maturityDate,
                              String creditRating, int unitsAvailable, String bondType)
    {
        this.bondName = bondName;
        this.currency = Currency.getInstance(currencyCode);
        this.couponPaymentLeft = couponPaymentLeft;
        this.interestRate = interestRate;
        this.purchasePrice = purchasePrice;
        this.maturityDate = LocalDate.parse(maturityDate, locateDateformat);
        this.creditRating = BondCreditRating.lookupRating(creditRating).orElse(BondCreditRating.NA);
        this.unitsAvailable = unitsAvailable;
        this.bondType = BondType.lookupRating(bondType).orElse(BondType.NA);
    }

    @Override
    public String call() throws FlowException {
        // Sample specific - retrieving the hard-coded observers
        IdentityService identityService = getServiceHub().getIdentityService();


        return null;
    }
}
