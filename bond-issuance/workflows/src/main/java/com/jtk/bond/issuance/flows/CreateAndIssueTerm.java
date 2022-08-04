package com.jtk.bond.issuance.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.jtk.bond.issuance.constants.CordaParties;
import com.jtk.bond.issuance.contract.contants.BondCreditRating;
import com.jtk.bond.issuance.contract.contants.BondType;
import com.jtk.bond.issuance.state.TermState;
import com.jtk.corda.Utility;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens;
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens;
import com.r3.corda.lib.tokens.workflows.utilities.FungibleTokenBuilder;
import net.corda.core.contracts.TransactionState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.node.services.IdentityService;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;

/**
 * - Issuer issues a term state which records the bonds properties
 */

@InitiatingFlow
@StartableByRPC
public class CreateAndIssueTerm extends FlowLogic<String> {
    private final String bondName; // Name of bond cannot be changed
    private final Currency currency;
    private final int couponPaymentLeft; // number of coupon payments left
    private final double interestRate; // current interest rate for the bonds
    private final double purchasePrice; // current price of the bond
    private final String maturityDate; // Maturity date of the bond
    private final BondCreditRating creditRating; // credit rating of the bond
    private final int unitsAvailable; // number of units available - reduces with every bond that is brought
    private final BondType bondType;

    private static final Logger log = LoggerFactory.getLogger(CreateAndIssueTerm.class);

    public CreateAndIssueTerm(String bondName, String currencyCode, int couponPaymentLeft,
                              double interestRate, double purchasePrice, String maturityDate,
                              String creditRating, int unitsAvailable, String bondType)
    {
        this.bondName = bondName;
        this.currency = Currency.getInstance(currencyCode);
        this.couponPaymentLeft = couponPaymentLeft;
        this.interestRate = interestRate;
        this.purchasePrice = purchasePrice;
        this.maturityDate = maturityDate;
        this.creditRating = BondCreditRating.lookupRating(creditRating).orElse(BondCreditRating.NA);
        this.unitsAvailable = unitsAvailable;
        this.bondType = BondType.lookupRating(bondType).orElse(BondType.NA);
    }

    @Override
    @Suspendable
    public String call() throws FlowException {
        // Sample specific - retrieving the hard-coded observers
        Party company = getOurIdentity();
        log.info("Flow called by {}", company.getName().getOrganisation());

        if(!company.getName().getOrganisationUnit().equals("Bank")) {
            throw new IllegalArgumentException("Only Banks can call CreateAndIssueTerm...");
        }
        CordaX500Name notaryX500Name = CordaX500Name.parse(CordaParties.NOTARY.getCordaX500Name());
        IdentityService identityService = getServiceHub().getIdentityService();
        List<Party> otherBanks = Utility.getLegalIdentitiesByOU(identityService, "Bank")
                .stream()
                .filter(party -> !party.getName().getCommonName().equals(company.getName().getCommonName()))
                .collect(Collectors.toList());
        List<Party> observers = Utility.getLegalIdentitiesByOU(identityService,"Observer");
        observers.addAll(otherBanks);
        log.info("Identified observers to publish term-state: {}",
                observers.stream().map(party -> party.getName().getCommonName()).collect(Collectors.joining(",")));

        final TermState termState = new TermState(
                company, new ArrayList<>(), bondName, "ACTIVE", bondType.getValue(),
                currency.getCurrencyCode(), couponPaymentLeft, interestRate, purchasePrice,
                maturityDate, creditRating.getValue(), unitsAvailable, 0,new UniqueIdentifier());

        log.info("Created TermState {}", termState);

        final Party notary = getServiceHub().getNetworkMapCache()
                .getNotary(notaryX500Name);

        TransactionState<TermState> transactionState = new TransactionState<>(termState, notary);
        // Using the build-in flow to create an evolvable token type -- Term

        subFlow(new CreateEvolvableTokens(transactionState, observers));
        // Indicate the recipient which is the issuing party itself here
        //new FungibleToken(issueAmount, getOurIdentity(), null);
        FungibleToken termStateToken = new FungibleTokenBuilder()
                .ofTokenType(termState.toPointer())
                .withAmount(1)
                .issuedBy(getOurIdentity())
                .heldBy(getOurIdentity())
                .buildFungibleToken();
        SignedTransaction stx = subFlow(new IssueTokens(ImmutableList.of(termStateToken), observers));
        log.info("Done CreateAndIssueTerm...");
        return "\nGenerated 1 "  + this.bondName + " Term with price: "
                + this.purchasePrice + " " + this.currency + "\nTransaction ID: "+ stx.getId();
    }
}
