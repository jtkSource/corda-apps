package com.jtk.corda.cash.issuance.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.jtk.corda.cash.issuance.state.CashState;
import com.jtk.corda.workflows.Utility;
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
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

@InitiatingFlow
@StartableByRPC
public class CreateCashFlow extends FlowLogic<String> {
    private static final Logger log = LoggerFactory.getLogger(CreateCashFlow.class);

    private final ProgressTracker progressTracker = new ProgressTracker(
            AUTHORIZATION,
            IDENTIFY_OBSERVERS,
            IDENTIFY_NOTARIES,
            CREATE_TOKEN,
            SIGNING,
            DONE
    );

    private static final ProgressTracker.Step AUTHORIZATION = new ProgressTracker
            .Step("Validating Authorized Nodes");
    private static final ProgressTracker.Step IDENTIFY_OBSERVERS = new ProgressTracker
            .Step("Identifying Observers");
    private static final ProgressTracker.Step IDENTIFY_NOTARIES = new ProgressTracker
            .Step("Identifying Notaries");

    private static final ProgressTracker.Step CREATE_TOKEN = new ProgressTracker
            .Step("Creating Tokens");

    private static final ProgressTracker.Step SIGNING = new ProgressTracker
            .Step("Signing Tokens");

    private static final ProgressTracker.Step DONE = new ProgressTracker
            .Step("Done Issuing Tokens");

    private String currency;
    private final String amount;
    private double usdRate;

    public CreateCashFlow(String currency, String amount, double usdRate) {
        this.currency = currency;
        this.amount = amount;
        this.usdRate = usdRate;
    }

    @Override
    @Suspendable
    public String call() throws FlowException {
        BigDecimal amountInBG = new BigDecimal(amount);
        log.info("Creating Central Bank: [{}] monies...", amountInBG);

        progressTracker.setCurrentStep(AUTHORIZATION);
        Party me = getOurIdentity();
        if (!me.getName().equals(CordaParties.CENTRAL_BANK.getCordaX500Name())) {
            throw new FlowException("Only Central Bank can create money");
        }
        progressTracker.setCurrentStep(IDENTIFY_OBSERVERS);
        List<Party> observers = Utility.getLegalIdentitiesByOU(getServiceHub().getIdentityService(), "Observer");
        log.info("Observers {}", observers);
        if (observers.size() == 0) {
            throw new FlowException("Cannot issue money without observers");
        }
        progressTracker.setCurrentStep(IDENTIFY_NOTARIES);
        Party notary = getServiceHub().getNetworkMapCache()
                .getNotary(CordaParties.NOTARY.getCordaX500Name());
        progressTracker.setCurrentStep(CREATE_TOKEN);
        final CashState cashState = new CashState(currency, usdRate, me, new UniqueIdentifier());
        TransactionState<CashState> transactionState = new TransactionState<>(cashState, notary);
        subFlow(new CreateEvolvableTokens(transactionState, observers));
        FungibleToken fungibleCashTokens = new FungibleTokenBuilder()
                .ofTokenType(cashState.toPointer())
                .issuedBy(me)
                .heldBy(me)
                .withAmount(amountInBG)
                .buildFungibleToken();
        progressTracker.setCurrentStep(SIGNING);

        SignedTransaction stx = subFlow(new IssueTokens(ImmutableList.of(fungibleCashTokens), observers));

        progressTracker.setCurrentStep(DONE);

        return  String.format("Created Cash Tokens >{ " +
                        "\"currencyCode\":\"%s\"," +
                        "\"linearStateId\":\"%s\"," +
                        "\"issuer\":\"%s\"," +
                        "\"usdPairRate\":\"%f\"," +
                        "\"amount\":\"%f\"," +
                        "\"transactionId\":\"%s\"," +
                        " }",cashState.getCurrencyCode(),cashState.getLinearStateId(),
                cashState.getIssuer().getName().getCommonName(),
                cashState.getUsdPairRate(), amountInBG,
                stx.getId());
    }
}
