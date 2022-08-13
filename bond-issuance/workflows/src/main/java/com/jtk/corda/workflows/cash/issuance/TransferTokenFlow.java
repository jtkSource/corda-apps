package com.jtk.corda.workflows.cash.issuance;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.jtk.corda.states.cash.issuance.CashState;
import com.jtk.corda.workflows.utils.CustomQuery;
import com.jtk.corda.workflows.utils.Utility;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.TokenPointer;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import com.r3.corda.lib.tokens.workflows.utilities.FungibleTokenBuilder;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilities;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TransferTokenFlow {

    private static final ProgressTracker.Step AUTHORIZATION = new ProgressTracker
            .Step("Validating Authorized Nodes");
    private static final ProgressTracker.Step IDENTIFY_OBSERVERS = new ProgressTracker
            .Step("Identifying Observers");
    private static final ProgressTracker.Step FIND_TOKEN = new ProgressTracker
            .Step("Fetching Tokens");

    private static final ProgressTracker.Step TRANSFER_TOKENS = new ProgressTracker
            .Step("Transferring tokens");

    private static final ProgressTracker.Step DONE = new ProgressTracker
            .Step("Done Transferring Tokens");
    private static final ProgressTracker progressTracker = new ProgressTracker(
            AUTHORIZATION,
            IDENTIFY_OBSERVERS,
            FIND_TOKEN,
            TRANSFER_TOKENS,
            DONE
    );

    @InitiatingFlow
    @StartableByRPC
    public static class TransferTokenInitiator extends FlowLogic<String> {
        private static final Logger log = LoggerFactory.getLogger(TransferTokenInitiator.class);

        private final String currencyCode;
        private final String amount;
        private final Party recipient;

        public TransferTokenInitiator( String amount, String currencyCode, Party recipient) {
            this.currencyCode = currencyCode;
            this.amount = amount;
            this.recipient = recipient;
        }

        @Override
        @Suspendable
        public String call() throws FlowException {
            BigDecimal amountInBG = new BigDecimal(amount);
            log.info("Transferring {} {} to {} ", amountInBG, currencyCode, recipient.getName().getCommonName());

            progressTracker.setCurrentStep(AUTHORIZATION);
            Party me = getOurIdentity();
            ImmutableList<String> allowedSenders = ImmutableList.of("CBDC", "Bank");
            ImmutableList<String> allowedRecipients = ImmutableList.of("Bank");
            if (!allowedSenders.contains(me.getName().getOrganisationUnit())) {
                throw new FlowException("You can only transfer from Bank or CBDC");
            }
            if (!allowedRecipients.contains(recipient.getName().getOrganisationUnit())) {
                throw new FlowException("You can only transfer to Banks");
            }

            progressTracker.setCurrentStep(IDENTIFY_OBSERVERS);
            List<Party> observers = Utility.getLegalIdentitiesByOU(getServiceHub().getIdentityService(), "Observer");
            log.info("Observers: {}", observers.stream().map(party -> party.getName().getCommonName()).collect(Collectors.toList()));
            List<CashState> cashStates = CustomQuery.queryCashStateByCurrency(currencyCode, getServiceHub());
            if(cashStates.size() > 0) {

                progressTracker.setCurrentStep(FIND_TOKEN);
                CashState cashState = cashStates.get(0);
                TokenPointer<CashState> cashPointer = cashStates.get(0).toPointer();
                final QueryCriteria heldByMeCriteria = QueryUtilities.heldTokenAmountCriteria(cashState.toPointer(), me);
                FungibleToken transferTokens = new FungibleTokenBuilder()
                        .ofTokenType(cashPointer)
                        .issuedBy(cashState.getIssuer()) // issued by central bank
                        .heldBy(me)
                        .withAmount(amountInBG)
                        .buildFungibleToken();

                progressTracker.setCurrentStep(TRANSFER_TOKENS);
                PartyAndAmount<TokenType> partyAmount = new PartyAndAmount(recipient, transferTokens.getAmount());

                SignedTransaction stx = subFlow(new MoveFungibleTokens(
                        Collections.singletonList(partyAmount),
                        observers,
                        heldByMeCriteria,
                        me));

                progressTracker.setCurrentStep(DONE);
                return  String.format("Transferred Cash Tokens >{ " +
                                "\"currencyCode\":\"%s\"," +
                                "\"issuer\":\"%s\"," +
                                "\"recipient\":\"%s\"," +
                                "\"amount\":\"%.2f\"," +
                                "\"transactionId\":\"%s\"" +
                                " }",
                        cashState.getCurrencyCode(),
                        cashState.getIssuer().getName().getCommonName(),
                        recipient.getName().getCommonName(),
                        amountInBG,
                        stx.getId());
            }else {
                throw new FlowException("No Cash State ["+currencyCode+"] available ");
            }
        }
    }
}
