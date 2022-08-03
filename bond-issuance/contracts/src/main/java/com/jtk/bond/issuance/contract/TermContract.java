package com.jtk.bond.issuance.contract;

import com.jtk.bond.contants.BondCreditRating;
import com.jtk.bond.contants.BondType;
import com.jtk.bond.issuance.state.TermState;
import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;

import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * TermContract is used to ensure that only the party that created the term can make changes to the contract
 *  As per requirement you can prevent the change of some fields if required.
 */
public class TermContract extends EvolvableTokenContract implements Contract {
    public static final String CONTRACT_ID = "com.jtk.bond.issuance.contract.TermContract";

    @Override
    public void verify(@NotNull LedgerTransaction tx) {
        TermState outputState = (TermState) tx.getOutput(0);
        // ensure state is created by the same party that invoked the flow
        if(!(tx.getCommand(0).getSigners().contains(outputState.getIssuer().getOwningKey()))){
            throw new IllegalArgumentException("Company Signature Required!!");
        }

        // perform all the other checks for EvolvableTokenContract
        super.verify(tx);
    }

    @Override
    public void additionalCreateChecks(LedgerTransaction tx) {
        TermState createdTerm = tx.outputsOfType(TermState.class).get(0);
        requireThat(req->{
            req.using("Bond Issuer cannot be empty", (createdTerm.getIssuer()!=null));
            req.using("Bond State cannot be empty", (!createdTerm.getBondState().isEmpty()));
            req.using("Bond Coupon payment left cannot be zero", (createdTerm.getCouponPaymentLeft()!=0));
            req.using("Bond Interest rate payment cannot be less than 0",(createdTerm.getInterestRate() >= 0.0));
            req.using("Bond Interest purchase price cannot be less than 0",(createdTerm.getPurchasePrice() >= 0.0));
            req.using("Bond maturity date cannot be null", (createdTerm.getMaturityDate()!=null));
            req.using("Bond maturity date must be greater than a 30 days",
                    (createdTerm.getMaturityDate().isAfter(LocalDate.now().plusDays(30))));
            req.using("Bond Credit rating cannot be empty or NA ",
                    (createdTerm.getCreditRating() != null) && createdTerm.getCreditRating() != BondCreditRating.NA);
            req.using("Bond type cannot be empty or NA ",
                    (createdTerm.getBondType() != null) && createdTerm.getBondType() != BondType.NA);
            req.using("Bond Units Available cannot be zero when created", createdTerm.getUnitsAvailable() > 0);
            req.using("Bond redemption value should be zero on create ", createdTerm.getRedemptionAvailable() == 0);

            return null;
        });
    }

    @Override
    public void additionalUpdateChecks(LedgerTransaction tx) {
        TermState inputTermState = tx.inputsOfType(TermState.class).get(0);
        TermState outputTermState = tx.outputsOfType(TermState.class).get(0);
        requireThat(req-> {
            //Validations when a bond Term is updated
            req.using("Bond Issuer cannot be changed", inputTermState.getIssuer().equals(outputTermState.getIssuer()));
            req.using("Bond Currency must not be changed.", inputTermState.getCurrency().equals(outputTermState.getCurrency()));
            req.using("Stock Name must not be changed.", inputTermState.getBondName().equals(outputTermState.getBondName()));
            req.using("Bond FractionDigits must not be changed.", inputTermState.getFractionDigits() == outputTermState.getFractionDigits());
            return null;
        });


    }
}
