package com.jtk.corda.workflows.bond.issuance;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.jtk.corda.workflows.cash.issuance.TransferTokenFlow;
import com.jtk.corda.workflows.utils.CustomQuery;
import com.jtk.corda.states.bond.issuance.TermState;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.InitiatingFlow;
import net.corda.core.flows.ReceiveFinalityFlow;
import net.corda.core.flows.SendStateAndRefFlow;
import net.corda.core.flows.StartableByRPC;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.UntrustworthyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@InitiatingFlow
@StartableByRPC
public class RequestForBondInitiatorFlow extends FlowLogic<String> {
    private static final Logger log = LoggerFactory.getLogger(RequestForBondInitiatorFlow.class);
    private final UniqueIdentifier teamStateLinearID;
    private final int unitsOfBonds;

    public RequestForBondInitiatorFlow(UniqueIdentifier teamStateLinearID, int unitsOfBonds) {
        this.teamStateLinearID = teamStateLinearID;
        this.unitsOfBonds = unitsOfBonds;
    }

    @Suspendable
    @Override
    public String call() throws FlowException {

        Party investorParty = getOurIdentity();
        if (!investorParty.getName().getOrganisationUnit().equals("Bank")) {
            log.error("The flow is not invoked by a bank");
            throw new FlowException("Flow can be invoked by OU=Bank only");
        }

        //find term based on linearID and see if the requested amount is available
        StateAndRef<TermState> termStateAndRef = CustomQuery.queryTermsByTermStateLinearID
                (teamStateLinearID, getServiceHub());

        if (termStateAndRef != null) {
            log.info("Term: {} is present ", teamStateLinearID);
            TermState termState = termStateAndRef.getState()
                    .getData().toPointer(TermState.class)
                    .getPointer().resolve(getServiceHub()).getState().getData();
            // check if requested unit is greater than available units
            if (unitsOfBonds > termState.getUnitsAvailable()) {
                throw new FlowException("Requesting for more bonds than available for the term");
            }
            if (termState.getIssuer().equals(investorParty)) {
                throw new FlowException("Issuer cannot be investor of the bond");
            }

            RequestForBondResponderFlow.BondRequestNotification bondRequestNotification =
                    new RequestForBondResponderFlow.BondRequestNotification(getOurIdentity(), unitsOfBonds,"PENDING");

            // We start by initiating a flow session with the counterparty. We
            // will use this session to send and receive messages from the
            // counterparty.
            FlowSession termIssuerSession = initiateFlow(termState.getIssuer());
            log.info("RequestForBondFlow$Initiator Session initiated for counterparty {}",
                    termIssuerSession.getCounterparty().getName().getCommonName());
            // first send the TermState held by the investor
            subFlow(new SendStateAndRefFlow(termIssuerSession, ImmutableList.of(termStateAndRef)));
            // then send the bond request notification
            UntrustworthyData<RequestForBondResponderFlow.BondRequestNotification> counterPartyData =
                    termIssuerSession.sendAndReceive(RequestForBondResponderFlow.BondRequestNotification.class, bondRequestNotification);

            Boolean successfullySendCash = counterPartyData.unwrap(data -> {
                if (data.getStatus().equalsIgnoreCase("OK")) {
                    try {
                        log.info("Counterparty accepted BondRequest...");
                        int totalAmount = termState.getParValue() * unitsOfBonds;
                        log.info("Trying to send {} cash",totalAmount);
                        SignedTransaction tStxTran = subFlow(new TransferTokenFlow.TransferTokenInitiator(
                                String.valueOf(totalAmount),
                                termState.getCurrency(),
                                termState.getIssuer()));
                        log.info("TransactionID:{} for tokens to issuer {}", tStxTran.getId(), termState.getIssuer().getName().getCommonName());
                    }catch (Exception e){
                        log.error("Couldn't transfer money to counterparty", e);
                        return false;
                    }
                    return true;
                } else {
                    log.warn("Counterparty Rejected BondRequest...");
                    return false;
                }
            });
            if(successfullySendCash){
                try {

                    SignedTransaction finalTx = subFlow(new ReceiveFinalityFlow(termIssuerSession));

                    FungibleToken fungibleToken = (FungibleToken) finalTx.getTx().getOutputStates().get(0);
                    Party tokenIssuer = fungibleToken.getIssuer();
                    String issuerCN = tokenIssuer.getName().getCommonName();
                    Party tokenHolder = (Party)fungibleToken.getHolder();
                    String holderCN = tokenHolder.getName().getCommonName();
                    Amount<IssuedTokenType> fungibleTokenAmount = ((FungibleToken) finalTx.getTx().getOutputStates().get(0)).getAmount();
                    String tokenIdentifier = fungibleTokenAmount.getToken().getTokenType().getTokenIdentifier();
                    return "{" +
                            "\"tokenType\": \"FungibleToken\", " +
                            "\"name\": \"BondState\", " +
                            "\"tokenIdentifier\": \""+tokenIdentifier+"\", " +
                            "\"issuer\": \""+issuerCN+"\", " +
                            "\"holder\": \""+holderCN+"\", " +
                            "\"amount\": "+fungibleTokenAmount.getQuantity()+" " +
                            "}";
                } catch (Exception e) {
                    log.error("Unexpected Exception ", e);
                    throw new IllegalArgumentException("Unexpected Exception in Finalizing the flow");
                }
            }else {
                throw new IllegalArgumentException("Cash couldn't be transferred successfully ");
            }

        } else {
            throw new IllegalArgumentException(teamStateLinearID + ": not found xxx");
        }
    }
}
