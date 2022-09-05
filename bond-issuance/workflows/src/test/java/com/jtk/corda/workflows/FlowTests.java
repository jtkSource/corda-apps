package com.jtk.corda.workflows;

import com.google.common.collect.ImmutableList;
import com.jtk.corda.states.bond.issuance.BondState;
import com.jtk.corda.workflows.bond.coupons.CouponPaymentFlow;
import com.jtk.corda.workflows.bond.issuance.CreateAndIssueTermFlow;
import com.jtk.corda.workflows.bond.issuance.InterBankBondTransferFlow;
import com.jtk.corda.workflows.bond.issuance.QueryBondTermsFlow;
import com.jtk.corda.workflows.bond.issuance.QueryBondToken;
import com.jtk.corda.workflows.bond.issuance.QueryBondsFlow;
import com.jtk.corda.workflows.bond.issuance.RequestForBondInitiatorFlow;
import com.jtk.corda.workflows.cash.issuance.CreateCashFlow;
import com.jtk.corda.workflows.cash.issuance.QueryCashTokenFlow;
import com.jtk.corda.workflows.cash.issuance.TransferTokenFlow;
import com.jtk.corda.workflows.utils.CustomQuery;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.node.NetworkParameters;
import net.corda.core.transactions.SignedTransaction;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class FlowTests {

    private static final Logger log = LoggerFactory.getLogger(FlowTests.class);
    protected MockNetwork network;
    protected Party notaryParty;
    private Party gsParty;
    private Party cbParty;
    private Party hsbcParty;
    private Party citiParty;
    protected StartedMockNode observerNode;
    protected StartedMockNode notaryNode;
    protected StartedMockNode gsNode;
    protected StartedMockNode hsbcNode;
    private StartedMockNode citiNode;
    private StartedMockNode cbNode;

    // "CN=Goldman Sachs,OU=Bank,O=Goldman Sachs,L=New York,C=US"
    public static TestIdentity GS = new TestIdentity(new CordaX500Name("Goldman Sachs", "Bank",
            "Goldman Sachs", "New York", null, "US"));
    // "CN=HSBC,OU=Bank,O=HSBC,L=London,C=GB"
    public static TestIdentity HSBC = new TestIdentity(new CordaX500Name("HSBC", "Bank",
            "HSBC", "London", null, "GB"));

    public static TestIdentity CITI = new TestIdentity(new CordaX500Name("CITI", "Bank",
            "CITI", "New York", null, "US"));

    // "CN=MAS,OU=Observer,O=MAS,L=Singapore,C=SG"

    public static TestIdentity OBSERVER = new TestIdentity(new CordaX500Name("MAS", "Observer",
            "MAS", "Singapore", null, "SG"));
    // "CN=SGX Notary,OU=Notary,O=SGX,L=Singapore,C=SG"

    public static TestIdentity NOTARY = new TestIdentity(new CordaX500Name("SGX Notary", "Notary",
            "SGX", "Singapore", null, "SG"));
    public static TestIdentity CB = new TestIdentity(new CordaX500Name("Central Bank", "CBDC",
            "Central Bank", "Singapore", null, "SG"));
    private final static DateTimeFormatter locateDateformat = DateTimeFormatter.ofPattern("yyyyMMdd");

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

        citiNode = network.createNode(CITI.getName());
        citiParty = citiNode.getInfo().getLegalIdentities().get(0);

        observerNode = network.createNode(OBSERVER.getName());
        notaryNode = network.getDefaultNotaryNode();
        notaryParty = notaryNode.getInfo().getLegalIdentities().get(0);
        network.startNodes();

        /**
         * Create Terms for testing
         */
        // 10 billion GBP issue to CB
        cbNode.startFlow(new CreateCashFlow("10000000000","GBP",0.82));
        network.runNetwork();
        cbNode.startFlow(new CreateCashFlow("10000000000","PHP",55.72));
        network.runNetwork();
        cbNode.startFlow(new CreateCashFlow("10000000000","ARS",55.72));
        network.runNetwork();

        CordaFuture<String> future = gsNode.startFlow(new CreateAndIssueTermFlow("RFB-GS-TEST1",3.2,1000,
                1000,"20270806", "CB", "USD", "AAA",2));
        network.runNetwork();
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
        // move 2 million to HSBC
        cbNode.startFlow(new TransferTokenFlow.TransferTokenInitiator("2000000", "GBP", hsbcParty));
        network.runNetwork();
        // Create Term
        CordaFuture<String> future = gsNode.startFlow(new CreateAndIssueTermFlow("RFB-GS-TEST-BOND",3.2,1000,
                1000,"20270806", "CB", "GBP", "AAA",2));
        network.runNetwork();

        String response = future.get().split(">")[1];
        JSONObject json = (JSONObject) new JSONTokener(response).nextValue();
        String termLinearId = json.getString("linearId");

        // Request for Bond Issue
        CordaFuture<String> future1 = hsbcNode.startFlow(new RequestForBondInitiatorFlow(UniqueIdentifier.Companion.fromString(termLinearId), 50));
        network.runNetwork();
        String jsonToken = future1.get();
        json = (JSONObject) new JSONTokener(jsonToken).nextValue();

        assertEquals(50,json.getLong("amount"));
        assertEquals("FungibleToken",json.getString("tokenType"));
        assertEquals("BondToken",json.getString("name"));
        assertEquals("Goldman Sachs",json.getString("issuer"));
        assertEquals("HSBC",json.getString("holder"));
        String identifier = json.getString("tokenIdentifier");
        assertEquals(termLinearId, identifier);

        String bondStateJson = hsbcNode.startFlow(new QueryBondsFlow.
                        GetBondByTermStateLinearID(UniqueIdentifier.Companion.fromString(termLinearId))).get();
        JSONArray jsonArray = (JSONArray) new JSONTokener(bondStateJson).nextValue();
        log.info("Bond States JSON {}", jsonArray);
        json = jsonArray.getJSONObject(0);
        assertEquals("RFB-GS-TEST-BOND",json.getString("bondName"));
        assertEquals(termLinearId, json.getString("termStateLinearID"));

        BigDecimal total = gsNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("GBP")).get();
        assertEquals(0, new BigDecimal("50000.00").compareTo(total)); // because the fractionDigits is 2 for currencies
        total = hsbcNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("GBP")).get();
        assertEquals(0, new BigDecimal("1950000.00").compareTo(total)); // because the fractionDigits is 2 for currencies
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

        CordaFuture<SignedTransaction> transferTogsF = cbNode.startFlow(new TransferTokenFlow.TransferTokenInitiator("2000", "USD", gsParty));
        network.runNetwork();
        SignedTransaction tStxTran = transferTogsF.get();
        assertNotNull(tStxTran);
        List<ContractState> outputStates = tStxTran.getTx().getOutputStates();
        assertEquals("There should be 2 output states",2, outputStates.size());
        FungibleToken gsToken = outputStates.stream()
                .map(contractState -> (FungibleToken) contractState)
                .filter(ft -> ft.getHolder().nameOrNull().getCommonName().equals("Goldman Sachs"))
                .findAny().get();
        FungibleToken cbToken = outputStates.stream()
                .map(contractState -> (FungibleToken) contractState)
                .filter(ft -> ft.getHolder().nameOrNull().getCommonName().equals("Central Bank"))
                .findAny().get();

        double gsAmount = gsToken.getAmount().getQuantity() * gsToken.getAmount().getDisplayTokenSize().doubleValue();
        double cbAmount = cbToken.getAmount().getQuantity() * cbToken.getAmount().getDisplayTokenSize().doubleValue();
        assertEquals("There should be 2000 going to Goldman Sachs",0 ,
                Double.compare(2000.00,gsAmount));
        assertEquals("There should be 8000 going to Central Bank",0,
                Double.compare(8000.00,cbAmount));

    }


    @Test
    public void testTransferTokensToBankFromBank() throws ExecutionException, InterruptedException {
        // Create 10,000 euro in CB
        CordaFuture<String> future = cbNode.startFlow(new CreateCashFlow("10000","EUR",0.97));
        network.runNetwork();
        String response = future.get().split(">")[1];
        JSONObject json = (JSONObject) new JSONTokener(response).nextValue();
        assertEquals("10000.00",json.getString("amount"));

        // Transfer 5,000 EUR to Goldman Sachs
        CordaFuture<SignedTransaction> transferTogsF  = cbNode.startFlow(new TransferTokenFlow.TransferTokenInitiator("5000", "EUR", gsParty));
        network.runNetwork();
        SignedTransaction tStxTran = transferTogsF.get();
        assertNotNull(tStxTran);
        List<ContractState> outputStates = tStxTran.getTx().getOutputStates();
        assertEquals("There should be 2 output states",2, outputStates.size());
        FungibleToken gsToken = outputStates.stream()
                .map(contractState -> (FungibleToken) contractState)
                .filter(ft -> ft.getHolder().nameOrNull().getCommonName().equals("Goldman Sachs"))
                .findAny().get();
        FungibleToken cbToken = outputStates.stream()
                .map(contractState -> (FungibleToken) contractState)
                .filter(ft -> ft.getHolder().nameOrNull().getCommonName().equals("Central Bank"))
                .findAny().get();

        double gsAmount = gsToken.getAmount().getQuantity() * gsToken.getAmount().getDisplayTokenSize().doubleValue();
        double cbAmount = cbToken.getAmount().getQuantity() * cbToken.getAmount().getDisplayTokenSize().doubleValue();
        assertEquals("There should be 5000 going to Goldman Sachs",0 ,
                Double.compare(5000.00,gsAmount));
        assertEquals("There should be 5000 in the Central Bank",0,
                Double.compare(5000.00,cbAmount));


        BigDecimal total = gsNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("EUR")).get();
        assertEquals(0, new BigDecimal("5000.00").compareTo(total)); // because the fractionDigits is 2 for currencies

        total = cbNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("EUR")).get();
        assertEquals(0, new BigDecimal("5000.00").compareTo(total)); // because the fractionDigits is 2 for currencies

        // Goldman Sachs Transfer 2,000 EUR to HSBC
        CordaFuture<SignedTransaction> transferTohsbcF  = gsNode.startFlow(new TransferTokenFlow.TransferTokenInitiator("2000", "EUR", hsbcParty));
        network.runNetwork();
        tStxTran = transferTohsbcF.get();
        assertNotNull(tStxTran);
        outputStates = tStxTran.getTx().getOutputStates();
        assertEquals("There should be 2 output states",2, outputStates.size());
        gsToken = outputStates.stream()
                .map(contractState -> (FungibleToken) contractState)
                .filter(ft -> ft.getHolder().nameOrNull().getCommonName().equals("Goldman Sachs"))
                .findAny().get();
        FungibleToken hsbcToken = outputStates.stream()
                .map(contractState -> (FungibleToken) contractState)
                .filter(ft -> ft.getHolder().nameOrNull().getCommonName().equals("HSBC"))
                .findAny().get();

        gsAmount = gsToken.getAmount().getQuantity() * gsToken.getAmount().getDisplayTokenSize().doubleValue();
        double hsbcAmount = hsbcToken.getAmount().getQuantity() * hsbcToken.getAmount().getDisplayTokenSize().doubleValue();
        assertEquals("There should be 3000 going to Goldman Sachs",0 ,
                Double.compare(3000.00, gsAmount));
        assertEquals("There should be 2000 in the HSBC",0,
                Double.compare(2000.00, hsbcAmount));

        total = gsNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("EUR")).get();
        assertEquals(0, new BigDecimal("3000.00").compareTo(total)); // because the fractionDigits is 2 for currencies

        total = hsbcNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("EUR")).get();
        assertEquals(0, new BigDecimal("2000.00").compareTo(total)); // because the fractionDigits is 2 for currencies

        total = cbNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("EUR")).get();
        assertEquals(0, new BigDecimal("5000.00").compareTo(total)); // because the fractionDigits is 2 for currencies

    }

    @Test
    public void testCouponPayment() throws ExecutionException, InterruptedException {
        // move 2 million to HSBC
        cbNode.startFlow(new TransferTokenFlow.TransferTokenInitiator("2000000", "PHP", hsbcParty));
        network.runNetwork();
        // move 2 million to GS
        cbNode.startFlow(new TransferTokenFlow.TransferTokenInitiator("2000000", "PHP", gsParty));
        network.runNetwork();

        // Create Term
        LocalDate maturityDate = LocalDate.now().plusYears(1);
        CordaFuture<String> future = gsNode.startFlow(new CreateAndIssueTermFlow("RFB-GS-TEST-COUPON",3.2,1000,
                1000, maturityDate.format(locateDateformat), "CB", "PHP", "AAA",2));
        network.runNetwork();
        String response = future.get().split(">")[1];
        JSONObject json = (JSONObject) new JSONTokener(response).nextValue();
        String termLinearId = json.getString("linearId");

        CordaFuture<String> future1 = hsbcNode.startFlow(new RequestForBondInitiatorFlow(UniqueIdentifier.Companion.fromString(termLinearId), 50));
        network.runNetwork();
        String jsonToken = future1.get();
        json = (JSONObject) new JSONTokener(jsonToken).nextValue();
        String bondTokenId_50 = json.getString("bondIdentifier");
        BondState bond50 = CustomQuery.queryBondByLinearID(UniqueIdentifier.Companion.fromString(bondTokenId_50), gsNode.getServices())
                .getState().getData();
        long couponLeftForBond50 = bond50.getCouponPaymentLeft();
        String nextCouponDateForBond50 = bond50.getNextCouponDate();

        long amountGS = 2000000 + (50*1000);
        BigDecimal totalGsAmount = gsNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("PHP")).get();
        assertEquals(0, new BigDecimal(amountGS).compareTo(totalGsAmount));
        long amountHSBC = 2000000 - (50*1000);
        BigDecimal totalHSBCAmount = hsbcNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("PHP")).get();
        assertEquals(0, new BigDecimal(amountHSBC).compareTo(totalHSBCAmount));

        future1 = hsbcNode.startFlow(new RequestForBondInitiatorFlow(UniqueIdentifier.Companion.fromString(termLinearId), 100));
        network.runNetwork();
        jsonToken = future1.get();
        json = (JSONObject) new JSONTokener(jsonToken).nextValue();
        String bondTokenId_100 = json.getString("bondIdentifier");
        assertEquals(bondTokenId_50, bondTokenId_100);
        BondState bond100 = CustomQuery.queryBondByLinearID(UniqueIdentifier.Companion.fromString(bondTokenId_100), gsNode.getServices())
                .getState().getData();
        assertEquals(bond50.getNextCouponDate(), bond100.getNextCouponDate());

        amountGS = amountGS + (100 * 1000);
        totalGsAmount = gsNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("PHP")).get();
        assertEquals(0, new BigDecimal(amountGS).compareTo(totalGsAmount));
        amountHSBC = amountHSBC - (100*1000);
        totalHSBCAmount = hsbcNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("PHP")).get();
        assertEquals(0, new BigDecimal(amountHSBC).compareTo(totalHSBCAmount));

        CordaFuture<String> gsCouponFuture = gsNode.startFlow(new CouponPaymentFlow(bond50.getNextCouponDate()));
        network.runNetwork();
        String msg = gsCouponFuture.get();
        String assertString = String.format("{ " +
                        "\"issuer\":\"%s\"," +
                        "\"couponDate\":\"%s\"" +
                        "}",
                bond50.getIssuer().getName().getCommonName(),
                bond50.getNextCouponDate());
        assertEquals(assertString, msg);
        double coupon1 = 80000.0;
        BigDecimal totalWithCoupon = gsNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("PHP")).get();
        assertEquals(totalGsAmount.doubleValue() - (coupon1 ), totalWithCoupon.doubleValue(), 0.01); // because the fractionDigits is 2 for currencies
        totalWithCoupon = hsbcNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("PHP")).get();
        assertEquals(totalHSBCAmount.doubleValue() + (coupon1 ), totalWithCoupon.doubleValue(),0.01); // because the fractionDigits is 2 for currencies

        // check Bond States // coupon date and coupon left
        bond50 = CustomQuery.queryBondByLinearID(UniqueIdentifier.Companion.fromString(bondTokenId_50), gsNode.getServices())
                .getState().getData();
        assertEquals(couponLeftForBond50-1, bond50.getCouponPaymentLeft());
        String nnCouponDate = LocalDate.parse(nextCouponDateForBond50, locateDateformat)
                .plusMonths(bond50.getPaymentFrequencyInMonths())
                .format(locateDateformat);
        assertNotEquals(nnCouponDate, bond50.getNextCouponDate());
    }




    @Test
    public void testTransferBondTokenAndPerformCouponPayment() throws ExecutionException, InterruptedException {
        final Integer totalBankAccount = new Integer("2000000");
        cbNode.startFlow(new TransferTokenFlow.TransferTokenInitiator(totalBankAccount.toString(), "ARS", gsParty));
        network.runNetwork();
        cbNode.startFlow(new TransferTokenFlow.TransferTokenInitiator(totalBankAccount.toString(), "ARS", hsbcParty));
        network.runNetwork();
        cbNode.startFlow(new TransferTokenFlow.TransferTokenInitiator(totalBankAccount.toString(), "ARS", citiParty));
        network.runNetwork();

        // Create Term
        CordaFuture<String> future = gsNode.startFlow(new CreateAndIssueTermFlow(
                "Evita-GS-TEST-BOND",3.2, 1000,
                1000, "20270806", "CB",
                "ARS", "AAA",2));
        network.runNetwork();

        String response = future.get().split(">")[1];
        JSONObject json = (JSONObject) new JSONTokener(response).nextValue();
        String termLinearId = json.getString("linearId");

        // Request for Bond Issue
        CordaFuture<String> future1 = hsbcNode.startFlow(new RequestForBondInitiatorFlow(UniqueIdentifier.Companion.fromString(termLinearId), 50));
        network.runNetwork();

        citiNode.startFlow(new InterBankBondTransferFlow.InterBankBondTransferFlowInitiator(UniqueIdentifier.Companion.fromString(termLinearId),
                5,200, hsbcParty ));
        network.runNetwork();

        CordaFuture<Long> fut = citiNode.startFlow(new QueryBondToken.GetTokenBalance(termLinearId));
        Long citiAmount =  fut.get();
        assertEquals(5L, citiAmount.longValue());

        fut = hsbcNode.startFlow(new QueryBondToken.GetTokenBalance(termLinearId));
        Long hsbcAmount = fut.get();
        assertEquals((50L - 5L), hsbcAmount.longValue());

        CordaFuture<String> queryBondsFut = hsbcNode.startFlow(new QueryBondsFlow.GetBondByTermStateLinearID(UniqueIdentifier.Companion.fromString(termLinearId)));
        String jsonToken = queryBondsFut.get();
        JSONObject jsonHsbc = new JSONArray(jsonToken).getJSONObject(0);

        queryBondsFut = citiNode.startFlow(new QueryBondsFlow.GetBondByTermStateLinearID(UniqueIdentifier.Companion.fromString(termLinearId)));
        jsonToken = queryBondsFut.get();
        JSONObject jsonCiti = new JSONArray(jsonToken).getJSONObject(0);

        assertEquals(jsonCiti.getString("termStateLinearID"), jsonHsbc.getString("termStateLinearID"));
        assertNotEquals(jsonCiti.getString("linearId"), jsonHsbc.getString("linearId"));


        CordaFuture<String> transferFut = citiNode.startFlow(new InterBankBondTransferFlow.
                InterBankBondTransferFlowInitiator(UniqueIdentifier.Companion.fromString(termLinearId), 50, 200, hsbcParty));
        network.runNetwork();

        String message = transferFut.get();
        JSONObject err = new JSONObject(message);
        assertEquals(err.getString("errorMsg"), "Failed validation for Bond Transfer request - NotEnoughBonds");
        fut = hsbcNode.startFlow(new QueryBondToken.GetTokenBalance(termLinearId));
        hsbcAmount = fut.get();
        assertEquals((50L - 5L), hsbcAmount.longValue());

        fut = citiNode.startFlow(new QueryBondToken.GetTokenBalance(termLinearId));
        citiAmount =  fut.get();
        assertEquals(5L, citiAmount.longValue());

        List<BondState> bonds = CustomQuery.
                queryBondByTermStateLinearID(UniqueIdentifier.Companion.fromString(termLinearId), gsNode.getServices());
        String nCouponDate = bonds.stream()
                .map(BondState::getNextCouponDate)
                .findAny().get();

        BigDecimal totalGsAmount = gsNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("ARS")).get();
        BigDecimal totalHsbcAmount = hsbcNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("ARS")).get();
        BigDecimal totalCitiAmount = citiNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("ARS")).get();

        CordaFuture<String> gsCouponFuture = gsNode.startFlow(new CouponPaymentFlow(nCouponDate));
        network.runNetwork();


        // coupon1 = 24000.0
        //coupon2 = 2666.666666666667

        double coupon1 = 24000.0;
        double coupon2 = 2666.666666666667;
        BigDecimal totalWithCoupon = gsNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("ARS")).get();
        assertEquals(totalGsAmount.doubleValue() - (coupon1+coupon2), totalWithCoupon.doubleValue(), 0.01); // because the fractionDigits is 2 for currencies

        totalWithCoupon = hsbcNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("ARS")).get();
        assertEquals(totalHsbcAmount.doubleValue() + (coupon1), totalWithCoupon.doubleValue(),0.01); // because the fractionDigits is 2 for currencies

        totalWithCoupon = citiNode.startFlow(new QueryCashTokenFlow.GetTokenBalance("ARS")).get();
        assertEquals(totalCitiAmount.doubleValue() + (coupon2), totalWithCoupon.doubleValue(),0.01); // because the fractionDigits is 2 for currencies


/**
 * totalGsAmount = {BigDecimal@18709} "2050200.0" -> 2023533.34
 * totalHsbcAmount = {BigDecimal@18710} "1950000.0" -> 1974000.0
 * totalCitiAmount = {BigDecimal@18711} "1999800.0"
 */

    }

}