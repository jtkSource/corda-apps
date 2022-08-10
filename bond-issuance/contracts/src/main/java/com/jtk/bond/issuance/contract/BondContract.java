package com.jtk.bond.issuance.contract;

import com.jtk.bond.issuance.contract.contants.BondCreditRating;
import com.jtk.bond.issuance.contract.contants.BondType;
import com.jtk.bond.issuance.state.BondState;
import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class BondContract extends EvolvableTokenContract implements Contract {
    public static final String CONTRACT_ID = "com.jtk.bond.issuance.contract.BondContract";
    private static final Logger log = LoggerFactory.getLogger(BondContract.class);
    final DateTimeFormatter locateDateformat = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public void verify(@NotNull LedgerTransaction tx) {
        log.info("BondContract Verifying... ");
        BondState outputState = (BondState)tx.getOutput(0);
        if(!tx.getCommand(0).getSigners().contains(outputState.getInvestor().getOwningKey())){
            throw new IllegalArgumentException("Investor signature is required");
        }
        if(!tx.getCommand(0).getSigners().contains(outputState.getIssuer().getOwningKey())){
            throw new IllegalArgumentException("Issuer signature is required");
        }

        super.verify(tx);
    }

    @Override
    public void additionalCreateChecks(@NotNull LedgerTransaction tx) {
        // need to ensure that the bond is created as per the term sheets
        BondState bondState = tx.outputsOfType(BondState.class).get(0);
        // bond state should be associated with a term state

        requireThat(req-> {
           req.using("BondState requires a TermState ", bondState.getTermStateLinearID()!=null);
           req.using("BondState requires an Investor from a Bank",
                   bondState.getInvestor().getName().getOrganisationUnit().equals("Bank"));
            req.using("BondState Status cannot be empty", (!bondState.getBondStatus().isEmpty()));
            req.using("BondState Coupon payment left cannot be less than zero", (bondState.getCouponPaymentLeft()>=0));
            req.using("BondState Interest rate payment cannot be less than 0",(bondState.getInterestRate() >= 0.0));
           req.using("BondState parValue cannot be less than 100 and greater than 1000",
                    (bondState.getParValue() >= 100 && bondState.getParValue() <= 1000));

            LocalDate maturityDate = null;
            LocalDate issueDate = null;
            try {
                maturityDate = LocalDate.parse(bondState.getMaturityDate(), locateDateformat);
                issueDate = LocalDate.parse(bondState.getIssueDate(), locateDateformat);
            }catch (Exception e){
                req.using("Maturity date and Issue Date format should be yyyyMMdd", false);
            }
            req.using("BondState maturity date must be greater than a 30 days",
                    (maturityDate.isAfter(LocalDate.now().plusDays(30))));
            req.using("BondState maturity date must be greater than issue date",
                    (maturityDate.isAfter(issueDate)));

            req.using("BondTerm payment frequency must be grater than zero", (bondState.getPaymentFrequencyInMonths()>0));
            req.using("BondState Credit rating cannot be empty or NA ",
                    (bondState.getCreditRating() != null) &&
                            BondCreditRating.lookupRating(bondState.getCreditRating()).orElse(BondCreditRating.NA) != BondCreditRating.NA);
            req.using("BondState type cannot be empty or NA ",
                    (bondState.getBondType() != null) && BondType.lookup(bondState.getBondType()).orElse(BondType.NA) != BondType.NA);
            return null;
        });

    }

    @Override
    public void additionalUpdateChecks(@NotNull LedgerTransaction tx) {
        // need to ensure that the bond is created as per the term sheets
        BondState inputBondState = tx.inputsOfType(BondState.class).get(0);
        BondState outputBondState = tx.outputsOfType(BondState.class).get(0);
        requireThat(req-> {
            //Validations when a bond Term is updated
            req.using("Bond Issuer cannot be changed", (inputBondState.getIssuer().equals(outputBondState.getIssuer())));
            req.using("Bond Currency must not be changed.", (inputBondState.getCurrency().equals(outputBondState.getCurrency())));
            req.using("Bond payment frequency must not be changed.",
                    (inputBondState.getPaymentFrequencyInMonths() == outputBondState.getPaymentFrequencyInMonths()));
            req.using("Bond maturity date must not be changed.",
                    (inputBondState.getMaturityDate() == outputBondState.getMaturityDate()));
            req.using("Bond issue date must not be changed.",
                    (inputBondState.getIssueDate() == outputBondState.getIssueDate()));
            req.using("Bond Name must not be changed.", (inputBondState.getBondName().equals(outputBondState.getBondName())));
            req.using("Bond FractionDigits must not be changed.", (inputBondState.getFractionDigits() == outputBondState.getFractionDigits()));
            return null;
        });

    }
}
