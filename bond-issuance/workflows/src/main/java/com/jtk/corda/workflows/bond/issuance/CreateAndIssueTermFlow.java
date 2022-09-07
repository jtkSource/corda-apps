package com.jtk.corda.workflows.bond.issuance;

import co.paralleluniverse.fibers.Suspendable;
import com.jtk.corda.CordaParties;
import com.jtk.corda.contants.BondStatus;
import com.jtk.corda.states.bond.issuance.TermState;
import com.jtk.corda.workflows.utils.Utility;
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens;
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
import net.corda.core.utilities.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * - Issuer issues a term state which records the bonds properties
 */

@InitiatingFlow
@StartableByRPC
public class CreateAndIssueTermFlow extends FlowLogic<String> {
    private final String bondName; // Name of bond cannot be changed
    private final double interestRate; // current interest rate for the bonds
    private final int parValue; // current price of the bond
    private final int unitsAvailable; // number of units available - reduces with every bond that is brought
    private final String maturityDate;
    private final String bondType;
    private final String creditRating;
    private final String currency;
    private static final Logger log = LoggerFactory.getLogger(CreateAndIssueTermFlow.class);
    private final int paymentFrequencyInMonths;

    private final ProgressTracker progressTracker = new ProgressTracker(
            AUTHORIZATION,
            IDENTIFY_OBSERVERS,
            CREATE_STATES,
            SIGNING_TRANSACTIONS,
            DONE
    );
    private static final ProgressTracker.Step AUTHORIZATION = new ProgressTracker
            .Step("Validating Authorized Nodes");
    private static final ProgressTracker.Step IDENTIFY_OBSERVERS = new ProgressTracker
            .Step("Identifying Observers");

    private static final ProgressTracker.Step CREATE_STATES = new ProgressTracker
            .Step("Creating Term States");

    private static final ProgressTracker.Step SIGNING_TRANSACTIONS = new ProgressTracker
            .Step("Signing Transaction");

    private static final ProgressTracker.Step DONE = new ProgressTracker
            .Step("Done Processing Flow");

    public CreateAndIssueTermFlow(String bondName, double interestRate, int parValue, int unitsAvailable,
                                  String maturityDate, String bondType, String currency,
                                  String creditRating, int paymentFrequencyInMonths)
    {
        this.bondName = bondName;
        this.interestRate = interestRate;
        this.parValue = parValue;
        this.unitsAvailable = unitsAvailable;
        this.maturityDate = maturityDate;
        this.bondType = bondType;
        this.currency = currency;
        this.creditRating = creditRating;
        this.paymentFrequencyInMonths = paymentFrequencyInMonths;
    }

    @Override
    @Suspendable
    public String call() throws FlowException {
        // Sample specific - retrieving the hard-coded observers
        progressTracker.setCurrentStep(AUTHORIZATION);

        Party company = getOurIdentity();
        log.info("Flow called by {}", company.getName().getOrganisation());
        if(!company.getName().getOrganisationUnit().equals("Bank")) {
            throw new FlowException("Only Banks can call CreateAndIssueTerm...");
        }
        progressTracker.setCurrentStep(IDENTIFY_OBSERVERS);
        CordaX500Name notaryX500Name = CordaParties.NOTARY.getCordaX500Name();
        IdentityService identityService = getServiceHub().getIdentityService();
        List<Party> otherBanks = Utility.getLegalIdentitiesByOU(identityService, "Bank")
                .stream()
                .filter(party -> !party.getName().getCommonName().equals(company.getName().getCommonName()))
                .collect(Collectors.toList());
        // Observers include the notary and all banks interested in knowing the terms
        List<Party> observers = Utility.getLegalIdentitiesByOU(identityService,"Observer");
        observers.addAll(otherBanks);
        log.info("Identified observers to publish term-state: {}",
                observers.stream().map(party -> party.getName().getCommonName()).collect(Collectors.joining(",")));

        progressTracker.setCurrentStep(CREATE_STATES);

        final TermState termState = new TermState(
                company, new HashSet<>(), bondName, BondStatus.ACTIVE.name(),
                interestRate, parValue,
                unitsAvailable, 0,new UniqueIdentifier(),
                this.maturityDate, this.bondType, this.currency,
                this.creditRating,this.paymentFrequencyInMonths);

        log.info("Created TermState {}", termState);

        final Party notary = getServiceHub().getNetworkMapCache().getNotary(notaryX500Name);
        log.info("Identified Notary {}", notary);

        progressTracker.setCurrentStep(SIGNING_TRANSACTIONS);
        TransactionState<TermState> transactionState = new TransactionState<>(termState, notary);
        SignedTransaction stx = subFlow(new CreateEvolvableTokens(transactionState, observers));

        log.info("Done CreateAndIssueTerm...");

        String jsnStr = String.format("{\"transactionID\":\"%s\"" +
                        ",\"bondName\":\"%s\"" +
                        ",\"parValue\":%s" +
                        ",\"linearId\":\"%s\"}",
                stx.getId(), this.bondName, this.parValue, termState.getLinearId());

        progressTracker.setCurrentStep(DONE);
        return "Generated 1 term >"+jsnStr;
    }
}
