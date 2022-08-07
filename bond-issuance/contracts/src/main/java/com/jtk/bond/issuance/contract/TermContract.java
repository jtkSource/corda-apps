package com.jtk.bond.issuance.contract;

import com.jtk.bond.issuance.contract.contants.BondCreditRating;
import com.jtk.bond.issuance.contract.contants.BondType;
import com.jtk.bond.issuance.state.TermState;
import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static net.corda.core.contracts.ContractsDSL.requireThat;

/**
 * TermContract is used to ensure that only the party that created the term can make changes to the contract
 *  As per requirement you can prevent the change of some fields if required.
 */
public class TermContract extends EvolvableTokenContract implements Contract {
    public static final String CONTRACT_ID = "com.jtk.bond.issuance.contract.TermContract";
    private static final Logger log = LoggerFactory.getLogger(TermContract.class);
    final DateTimeFormatter locateDateformat = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public void verify(@NotNull LedgerTransaction tx) {
        log.info("TermContract Verifying...");

        TermState outputState = (TermState) tx.getOutput(0);
        // ensure state is created by the same party that invoked the flow
        if(!(tx.getCommand(0).getSigners().contains(outputState.getIssuer().getOwningKey()))){
            throw new IllegalArgumentException("Issuer Signature Required!!");
        }

        // perform all the other checks for EvolvableTokenContract
        super.verify(tx);
    }

    @Override
    public void additionalCreateChecks(LedgerTransaction tx) {
        TermState createdTerm = tx.outputsOfType(TermState.class).get(0);
        requireThat(req->{
            req.using("BondTerm Issuer cannot be empty", (createdTerm.getIssuer()!=null));
            req.using("BondTerm State cannot be empty", (!createdTerm.getBondStatus().isEmpty()));
            req.using("BondTerm Coupon payment left cannot be less than zero", (createdTerm.getCouponPaymentLeft()>=0));
            req.using("BondTerm Interest rate payment cannot be less than 0",(createdTerm.getInterestRate() >= 0.0));
            req.using("BondTerm Interest purchase price cannot be less than 0",(createdTerm.getPurchasePrice() >= 0.0));
            req.using("BondTerm maturity date cannot be null", (createdTerm.getMaturityDate()!=null));
            LocalDate date = null;
            try {
                date = LocalDate.parse(createdTerm.getMaturityDate(), locateDateformat);
            }catch (Exception e){
                req.using("Maturity date format should be yyyyMMdd", false);
            }
            req.using("BondTerm maturity date must be greater than a 30 days",
                    (date.isAfter(LocalDate.now().plusDays(30))));
            req.using("BondTerm Credit rating cannot be empty or NA ",
                    (createdTerm.getCreditRating() != null) &&
                            BondCreditRating.lookupRating(createdTerm.getCreditRating()).orElse(BondCreditRating.NA) != BondCreditRating.NA);
            req.using("BondTerm type cannot be empty or NA ",
                    (createdTerm.getBondType() != null) && BondType.lookup(createdTerm.getBondType()).orElse(BondType.NA) != BondType.NA);
            req.using("BondTerm Units Available cannot be zero when created", createdTerm.getUnitsAvailable() > 0);
            req.using("BondTerm redemption value should be zero on create ", createdTerm.getRedemptionAvailable() == 0);

            return null;
        });
    }

    @Override
    public void additionalUpdateChecks(LedgerTransaction tx) {
        TermState inputTermState = tx.inputsOfType(TermState.class).get(0);
        TermState outputTermState = tx.outputsOfType(TermState.class).get(0);

        requireThat(req-> {
            //Validations when a bond Term is updated
            req.using("BondTerm Issuer cannot be changed", inputTermState.getIssuer().equals(outputTermState.getIssuer()));
            req.using("BondTerm Currency must not be changed.", inputTermState.getCurrency().equals(outputTermState.getCurrency()));
            req.using("BondTerm Name must not be changed.", inputTermState.getBondName().equals(outputTermState.getBondName()));
            req.using("BondTerm FractionDigits must not be changed.", inputTermState.getFractionDigits() == outputTermState.getFractionDigits());
            req.using("BondTerm available units shouldn't be less than 0 and greater than totalUnits",
                    inputTermState.getUnitsAvailable() >= 0 &&
                    inputTermState.getUnitsAvailable() <= inputTermState.getTotalUnits());
            req.using("BondTerm available redemption units should be equal to (totalUnits - UnitsAvailable)",
                    (inputTermState.getRedemptionAvailable() == inputTermState.getTotalUnits() - inputTermState.getUnitsAvailable()));

            return null;
        });


    }
}
