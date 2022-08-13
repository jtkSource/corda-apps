package com.jtk.corda.workflows;

import com.google.common.collect.ImmutableList;
import com.jtk.corda.workflows.bond.issuance.CreateAndIssueTermFlow;
import com.jtk.corda.workflows.bond.issuance.QueryBondTermsFlow;
import com.jtk.corda.workflows.bond.issuance.QueryBondsFlow;
import com.jtk.corda.workflows.bond.issuance.RequestForBondInitiatorFlow;
import com.jtk.corda.workflows.cash.issuance.CreateCashFlow;
import com.jtk.corda.workflows.cash.issuance.QueryCashTokenFlow;
import com.jtk.corda.workflows.cash.issuance.TransferTokenFlow;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.node.NetworkParameters;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetworkNotarySpec;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestCordapp;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertEquals;

public class FlowTests {

    private static final Logger log = LoggerFactory.getLogger(FlowTests.class);
    protected MockNetwork network;
    protected StartedMockNode observerNode;
    protected StartedMockNode notaryNode;

    protected StartedMockNode gsNode;
    protected StartedMockNode hsbcNode;

    protected Party notaryParty;
    private Party gsParty;
    private Party cbParty;
    private Party hsbcParty;

    private StartedMockNode cbNode;

    // "CN=Goldman Sachs,OU=Bank,O=Goldman Sachs,L=New York,C=US"
    public static TestIdentity GS = new TestIdentity(new CordaX500Name("Goldman Sachs", "Bank",
            "Goldman Sachs", "New York", null, "US"));
    // "CN=HSBC,OU=Bank,O=HSBC,L=London,C=GB"
    public static TestIdentity HSBC = new TestIdentity(new CordaX500Name("HSBC", "Bank",
            "HSBC", "London", null, "GB"));
    // "CN=MAS,OU=Observer,O=MAS,L=Singapore,C=SG"

    public static TestIdentity OBSERVER = new TestIdentity(new CordaX500Name("MAS", "Observer",
            "MAS", "Singapore", null, "SG"));
    // "CN=SGX Notary,OU=Notary,O=SGX,L=Singapore,C=SG"

    public static TestIdentity NOTARY = new TestIdentity(new CordaX500Name("SGX Notary", "Notary",
            "SGX", "Singapore", null, "SG"));
    public static TestIdentity CB = new TestIdentity(new CordaX500Name("Central Bank", "CBDC",
            "Central Bank", "Singapore", null, "SG"));

    @Before
    public void setup() throws ExecutionException, InterruptedException {
        network = new MockNetwork(new MockNetworkParameters()
                .withNetworkParameters(new NetworkParameters(
                        4,
                        emptyList(),
                        1000000000,
                        1000000000,
                        Instant.now(),
                        1,
                        emptyMap()))
                .withNotarySpecs(ImmutableList.of(new MockNetworkNotarySpec(NOTARY.getName())))
                .withCordappsForAllNodes(ImmutableList.of(
                        TestCordapp.findCordapp("com.jtk.corda.contracts"),
                        TestCordapp.findCordapp("com.jtk.corda.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")
                ))
                .withThreadPerNode(false)
        );
        gsNode = network.createNode(GS.getName());
        gsParty = gsNode.getInfo().getLegalIdentities().get(0);
        cbNode = network.createNode(CB.getName());
        cbParty = cbNode.getInfo().getLegalIdentities().get(0);
        hsbcNode = network.createNode(HSBC.getName());
        hsbcParty = hsbcNode.getInfo().getLegalIdentities().get(0);
        observerNode = network.createNode(OBSERVER.getName());
        notaryNode = network.getDefaultNotaryNode();
        notaryParty = notaryNode.getInfo().getLegalIdentities().get(0);
        network.startNodes();

        /**
         * Create Terms for testing
         */
        CordaFuture<String> future = gsNode.startFlow(new CreateAndIssueTermFlow("RFB-GS-TEST1",3.2,1000,
                1000,"20270806", "CB", "USD", "AAA",2));
        network.runNetwork();
        String response = future.get();
        log.info("Response to term creation: {}",response);
    }
    @After
    public void tearDown() {
        network.stopNodes();
    }

    @Test
    public void testQueryingBondsByRating() throws ExecutionException, InterruptedException {
        log.info("Starting test1...");
        CordaFuture<String> bondByRating = observerNode.startFlow(new QueryBondTermsFlow.GetBondTermsByRating("AAA"));
        String jsonStr = bondByRating.get();
        log.info("jsonStr {}", jsonStr);
        JSONArray jArray = (JSONArray) new JSONTokener(jsonStr).nextValue();
        log.info("testQueryingBondsByRating output: \n {}", jArray.toString(2));
        assertEquals(1, jArray.length());
        assertEquals("AAA",jArray.getJSONObject(0).get("creditRating"));
    }

    @Test
    public void testCreateAndIssueTermShouldNotifyBankAndObserversOfNewTerm() throws ExecutionException, InterruptedException {
        CordaFuture<String> future = gsNode.startFlow(new CreateAndIssueTermFlow("RFB-GS-TEST2",5,100,
                1600,"20240806", "CB", "SGD", "BB",2));
        network.runNetwork();
        String response = future.get().split(">")[1];
        JSONObject json = (JSONObject) new JSONTokener(response).nextValue();
        String termLinearId = json.getString("linearId");
        log.info("Response to term: {} ->:\n {}",termLinearId, json.toString(2));


        CordaFuture<String> bondByLinearId = observerNode.startFlow(new QueryBondTermsFlow.GetBondTermByTermStateLinearID(UniqueIdentifier.Companion.fromString(termLinearId)));
        JSONObject jsonStr = (JSONObject) new JSONTokener(bondByLinearId.get()).nextValue();
        log.info("QueryResponse ->:\n {}",json.toString(2));
        assertEquals(termLinearId, jsonStr.getString("linearId"));


        bondByLinearId = hsbcNode.startFlow(new QueryBondTermsFlow.GetBondTermByTermStateLinearID(UniqueIdentifier.Companion.fromString(termLinearId)));
        jsonStr = (JSONObject) new JSONTokener(bondByLinearId.get()).nextValue();
        log.info("QueryResponse ->:\n {}",json.toString(2));
        assertEquals(termLinearId, jsonStr.getString("linearId"));
    }

    @Test
    public void testRequestForBondFlow() throws ExecutionException, InterruptedException {
        CordaFuture<String> bondByRating = hsbcNode.startFlow(new QueryBondTermsFlow.GetBondTermsByRating("AAA"));
        String jsonStr = bondByRating.get();
        log.info("jsonStr {}", jsonStr);
        JSONArray jArray = (JSONArray) new JSONTokener(jsonStr).nextValue();
        JSONObject termObject = jArray.getJSONObject(0);
        assertEquals("AAA", termObject.get("creditRating"));
        String termLinearId = termObject.getString("linearId");

        // Get Bond

        CordaFuture<String> future = hsbcNode.startFlow(new RequestForBondInitiatorFlow
                (UniqueIdentifier.Companion.fromString(termLinearId), 50));
        network.runNetwork();
        String jsonToken = future.get();
        JSONObject json = (JSONObject) new JSONTokener(jsonToken).nextValue();

        assertEquals(50,json.getLong("amount"));
        assertEquals("FungibleToken",json.getString("tokenType"));
        assertEquals("BondState",json.getString("name"));
        assertEquals("Goldman Sachs",json.getString("issuer"));
        assertEquals("HSBC",json.getString("holder"));

        String identifier = json.getString("tokenIdentifier");

        String bondStateJson = hsbcNode.startFlow(new QueryBondsFlow.GetBondByTermStateLinearID(UniqueIdentifier.Companion.fromString(termLinearId)))
                .get();
        JSONArray jsonArray = (JSONArray) new JSONTokener(bondStateJson).nextValue();
        log.info("Bond States JSON {}", jsonArray);
        json = jsonArray.getJSONObject(0);
        assertEquals("RFB-GS-TEST1",json.getString("bondName"));
        assertEquals(termLinearId, json.getString("termStateLinearID"));
    }
    @Test
    public void testIssueCash() throws ExecutionException, InterruptedException {
        CordaFuture<String> future = cbNode.startFlow(new CreateCashFlow("2123456789.987654","INR",79.63));
        network.runNetwork();
        String response = future.get().split(">")[1];
        JSONObject json = (JSONObject) new JSONTokener(response).nextValue();
        assertEquals("INR",json.getString("currencyCode"));

        BigDecimal total = cbNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("INR")).get();
        assertEquals(new BigDecimal("2123456789.98"),total); // becuase the fractionDigits is 2 for currencies
        total = gsNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("INR")).get();
        assertEquals(new BigDecimal("0.0"),total);

        total = observerNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("INR")).get();
        assertEquals(new BigDecimal("0.0"),total);
    }

    @Test
    public void testIssueCashPublishMultipleToken() throws ExecutionException, InterruptedException {
        CordaFuture<String> future = cbNode.startFlow(new CreateCashFlow("10000","SGD",79.63));
        network.runNetwork();
        String response = future.get().split(">")[1];
        JSONObject json = (JSONObject) new JSONTokener(response).nextValue();
        assertEquals("10000.00",json.getString("amount"));

        CordaFuture<String> future1 = cbNode.startFlow(new CreateCashFlow("20000","SGD",79.63));
        network.runNetwork();
        response = future1.get().split(">")[1];
        json = (JSONObject) new JSONTokener(response).nextValue();
        assertEquals("20000.00",json.getString("amount"));

        BigDecimal total = cbNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("SGD")).get();
        assertEquals(0, new BigDecimal("30000.00").compareTo(total)); // because the fractionDigits is 2 for currencies
    }


    @Test
    public void testTransferTokensToBankFromCB() throws ExecutionException, InterruptedException {
        CordaFuture<String> future = cbNode.startFlow(new CreateCashFlow("10000","USD",1.37));
        network.runNetwork();
        String response = future.get().split(">")[1];
        JSONObject json = (JSONObject) new JSONTokener(response).nextValue();
        assertEquals("10000.00",json.getString("amount"));

        CordaFuture<String> transferTogsF = cbNode.startFlow(new TransferTokenFlow.TransferTokenInitiator("2000", "USD", gsParty));
        network.runNetwork();
        response = transferTogsF.get().split(">")[1];
        json = (JSONObject) new JSONTokener(response).nextValue();
        assertEquals("2000.00",json.getString("amount"));

        BigDecimal total = gsNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("USD")).get();
        assertEquals(0, new BigDecimal("2000.00").compareTo(total)); // because the fractionDigits is 2 for currencies

        total = cbNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("USD")).get();
        assertEquals(0, new BigDecimal("8000.00").compareTo(total)); // because the fractionDigits is 2 for currencies

    }

    @Test
    public void testTransferTokensToBankFromBank() throws ExecutionException, InterruptedException {
        CordaFuture<String> future = cbNode.startFlow(new CreateCashFlow("10000","EUR",0.97));
        network.runNetwork();
        String response = future.get().split(">")[1];
        JSONObject json = (JSONObject) new JSONTokener(response).nextValue();
        assertEquals("10000.00",json.getString("amount"));

        CordaFuture<String> transferTogsF = cbNode.startFlow(new TransferTokenFlow.TransferTokenInitiator("5000", "EUR", gsParty));
        network.runNetwork();
        response = transferTogsF.get().split(">")[1];
        json = (JSONObject) new JSONTokener(response).nextValue();
        assertEquals("5000.00",json.getString("amount"));

        BigDecimal total = gsNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("EUR")).get();
        assertEquals(0, new BigDecimal("5000.00").compareTo(total)); // because the fractionDigits is 2 for currencies

        total = cbNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("EUR")).get();
        assertEquals(0, new BigDecimal("5000.00").compareTo(total)); // because the fractionDigits is 2 for currencies

        CordaFuture<String> transferTohsbcF  = gsNode.startFlow(new TransferTokenFlow.TransferTokenInitiator("2000", "EUR", hsbcParty));
        network.runNetwork();
        response = transferTohsbcF.get().split(">")[1];
        json = (JSONObject) new JSONTokener(response).nextValue();
        assertEquals("2000.00",json.getString("amount"));

        total = gsNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("EUR")).get();
        assertEquals(0, new BigDecimal("3000.00").compareTo(total)); // because the fractionDigits is 2 for currencies

        total = cbNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("EUR")).get();
        assertEquals(0, new BigDecimal("5000.00").compareTo(total)); // because the fractionDigits is 2 for currencies


    }
}