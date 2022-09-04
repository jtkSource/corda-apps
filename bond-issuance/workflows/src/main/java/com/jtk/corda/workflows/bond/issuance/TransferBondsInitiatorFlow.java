package com.jtk.corda.workflows.bond.issuance;

import com.jtk.corda.states.bond.issuance.BondState;
import com.jtk.corda.states.bond.issuance.TermState;
import com.jtk.corda.workflows.utils.CustomQuery;
import com.jtk.corda.workflows.utils.Utility;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens;
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount;
import com.r3.corda.lib.tokens.workflows.utilities.FungibleTokenBuilder;
import com.r3.corda.lib.tokens.workflows.utilities.QueryUtilities;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.identity.Party;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class TransferBondsInitiatorFlow extends FlowLogic<SignedTransaction> {
    private static final Logger log = LoggerFactory.getLogger(TransferBondsInitiatorFlow.class);
    private UniqueIdentifier termIdentifier;
    private int transferAmount;
    private Party recipient;

    public TransferBondsInitiatorFlow(UniqueIdentifier termIdentifier, int transferAmount, Party recipient){
        this.termIdentifier = termIdentifier;
        this.transferAmount = transferAmount;
        this.recipient = recipient;
    }
    @Override
    public SignedTransaction call() throws FlowException {
        Party me = getOurIdentity();
        if (!me.getName().getOrganisationUnit().equals("Bank")) {
            log.error("The flow is not invoked by a bank");
            throw new FlowException("Flow can be invoked by OU=Bank only");
        }
        StateAndRef<TermState> termStateAndRef = CustomQuery
                .queryTermsByTermStateLinearID(termIdentifier, getServiceHub());
        List<Party> observers = Utility.getLegalIdentitiesByOU(getServiceHub().getIdentityService(), "Observer");

        TermState ts = termStateAndRef.getState().getData();
        QueryCriteria queryCriteria = QueryUtilities.heldTokenAmountCriteria(ts.toPointer(), me);
        FungibleToken transferTokens = new FungibleTokenBuilder()
                .ofTokenType(ts.toPointer())
                .issuedBy(ts.getIssuer()) // issued by central bank
                .heldBy(me)
                .withAmount(transferAmount)
                .buildFungibleToken();
        PartyAndAmount<TokenType> partyAmount = new PartyAndAmount(recipient, transferTokens.getAmount());
        log.info("Transferring {} tokens  to {} ", transferAmount, recipient);
        return subFlow(new MoveFungibleTokens(Collections.singletonList(partyAmount), observers, queryCriteria, me));
    }
}
