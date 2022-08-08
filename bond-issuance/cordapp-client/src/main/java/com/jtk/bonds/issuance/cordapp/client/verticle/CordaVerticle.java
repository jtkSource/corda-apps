package com.jtk.bonds.issuance.cordapp.client.verticle;

import com.jtk.bond.issuance.flows.CreateAndIssueTermFlow;
import com.jtk.bond.issuance.flows.QueryBondTermsFlow;
import com.jtk.bond.issuance.flows.QueryBondsFlow;
import com.jtk.bond.issuance.flows.RequestForBondInitiatorFlow;
import com.jtk.bonds.issuance.cordapp.client.utils.NodeRPCConnection;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.node.NodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CordaVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(CordaVerticle.class);
    private NodeRPCConnection nodeRPC;
    private CordaX500Name me;


    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        nodeRPC = NodeRPCConnection.getInstance(config());
        this.me = this.nodeRPC.proxy().nodeInfo().getLegalIdentities().get(0).getName();

        vertx.eventBus()
                .consumer("CORDA-API", new CordaHandler());
        startPromise.complete();
        log.info("Completed deploying CordaVerticle...");
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        if (nodeRPC != null) {
            nodeRPC.close();
        }
        stopPromise.complete();
        log.info("Undeployed CordaVerticle...");
    }

    class CordaHandler implements Handler<Message<JsonObject>> {

        @Override
        public void handle(Message<JsonObject> event) {
            // /addresses
            JsonObject json = event.body();
            String url = json.getString("url");
            JsonObject responseJson = new JsonObject();
            vertx.executeBlocking(e1 ->
            {
                        switch (url) {
                            case "addresses":
                                responseJson.put("msg", nodeRPC.proxy().nodeInfo().getAddresses().toString());
                                break;
                            case "identities":
                                responseJson.put("msg", nodeRPC.proxy().nodeInfo().getLegalIdentities().toString());
                                break;
                            case "platformversion":
                                responseJson.put("msg", nodeRPC.proxy().nodeInfo().getPlatformVersion());
                                break;
                            case "peers":
                                responseJson.put("msg", getPeers());
                                break;
                            case "notaries":
                                responseJson.put("msg", nodeRPC.proxy().notaryIdentities().toString());
                                break;
                            case "flows":
                                responseJson.put("msg", nodeRPC.proxy().registeredFlows().toString());
                                break;
                            case "me":
                                responseJson.put("msg", whoami());
                                break;
                            case "states":
                                responseJson.put("msg", nodeRPC.proxy().vaultQuery(ContractState.class).getStates().toString());
                                break;
                            case "create-bond-terms":
                                try {
                                    String returnMsg = nodeRPC.proxy().startTrackedFlowDynamic(CreateAndIssueTermFlow.class,
                                            json.getString("bondName"),
                                            json.getInteger("couponPaymentLeft"),
                                            json.getDouble("interestRate"),
                                            json.getDouble("purchasePrice"),
                                            json.getInteger("unitsAvailable"),
                                            json.getString("maturityDate"),
                                            json.getString("bondType"),
                                            json.getString("currency"),
                                            json.getString("creditRating")).getReturnValue().get();
                                    String response = returnMsg.split(">")[1];
                                    responseJson.put("msg", response);
                                } catch (InterruptedException e) {
                                    log.error("Exception query Corda", e);
                                    e1.fail(e);
                                    return;
                                } catch (ExecutionException e) {
                                    log.error("Exception query Corda", e);
                                    e1.fail(e);
                                    return;
                                }
                                break;
                            case "query-bond-terms":
                                queryBondTerms(json, responseJson);
                                break;
                            case "query-bonds":
                                queryBond(json, responseJson);
                                break;
                            case "request-for-bond":
                                String teamStateLinearID = json.getString("teamStateLinearID");
                                int unitsOfBonds = json.getInteger("unitsOfBonds");
                                try {
                                    String returnMsg = nodeRPC.proxy()
                                            .startTrackedFlowDynamic(RequestForBondInitiatorFlow.class,
                                                    UniqueIdentifier.Companion.fromString(teamStateLinearID),
                                            unitsOfBonds).getReturnValue().get();
                                    responseJson.put("msg", returnMsg);
                                } catch (InterruptedException e) {
                                    log.error("Exception query Corda", e);
                                    e1.fail(e);
                                    return;
                                } catch (ExecutionException e) {
                                    log.error("Exception query Corda", e);
                                    e1.fail(e);
                                    return;
                                }
                                break;
                            default:
                                responseJson.put("msg", "API NOT-FOUND");
                                break;
                        }
                        e1.complete(responseJson);
                    })
                    .onComplete(result -> {
                        if (result.succeeded()) {
                            event.reply(result.result());
                        } else {
                            event.fail(500, "Couldnt successfully process request...");
                        }
                    });
        }

        private void queryBondTerms(JsonObject json, JsonObject responseJson) {
            String qType = json.getString("queryType");
            String jsonResponse = "";

            if(qType.equalsIgnoreCase("byCurrency")){
                String currency = json.getString("queryValue");
                try {
                    jsonResponse = nodeRPC.proxy().startTrackedFlowDynamic
                            (QueryBondTermsFlow.GetBondTermsByCurrency.class, currency)
                            .getReturnValue().get();
                } catch (InterruptedException e) {
                    log.error("Exception query Corda", e);
                } catch (ExecutionException e) {
                    log.error("Exception query Corda", e);
                }
                responseJson.put("msg", jsonResponse);

            } else if (qType.equalsIgnoreCase("byRating")) {
                String creditRating = json.getString("queryValue");
                try {
                    jsonResponse = nodeRPC.proxy().startTrackedFlowDynamic
                                    (QueryBondTermsFlow.GetBondTermsByRating.class, creditRating)
                            .getReturnValue().get();
                } catch (InterruptedException e) {
                    log.error("Exception query Corda", e);
                } catch (ExecutionException e) {
                    log.error("Exception query Corda", e);
                }
                responseJson.put("msg", jsonResponse);

            } else if (qType.equalsIgnoreCase("lessThanMaturityDate")) {
                String maturityDate = json.getString("queryValue");
                try {
                    jsonResponse = nodeRPC.proxy().startTrackedFlowDynamic
                                    (QueryBondTermsFlow.GetBondTermsLessThanMaturityDate.class, maturityDate)
                            .getReturnValue().get();
                } catch (InterruptedException e) {
                    log.error("Exception query Corda", e);
                } catch (ExecutionException e) {
                    log.error("Exception query Corda", e);
                }
                responseJson.put("msg", jsonResponse);
            } else if (qType.equalsIgnoreCase("greaterThanMaturityDate")) {
                String maturityDate = json.getString("queryValue");
                try {
                    jsonResponse = nodeRPC.proxy().startTrackedFlowDynamic
                                    (QueryBondTermsFlow.GetBondTermsGreaterThanMaturityDate.class, maturityDate)
                            .getReturnValue().get();
                } catch (InterruptedException e) {
                    log.error("Exception query Corda", e);
                } catch (ExecutionException e) {
                    log.error("Exception query Corda", e);
                }
                responseJson.put("msg", jsonResponse);

            } else if (qType.equalsIgnoreCase("byTeamStateLinearID")) {
                String teamStateLinearID = json.getString("queryValue");
                try {
                    jsonResponse = nodeRPC.proxy().startTrackedFlowDynamic
                                    (QueryBondTermsFlow.GetBondTermByTeamStateLinearID.class, teamStateLinearID)
                            .getReturnValue().get();
                } catch (InterruptedException e) {
                    log.error("Exception query Corda", e);
                } catch (ExecutionException e) {
                    log.error("Exception query Corda", e);
                }
                responseJson.put("msg", jsonResponse);
            }
        }
        private void queryBond(JsonObject json, JsonObject responseJson) {
            String qType = json.getString("queryType");
            String jsonResponse = "";

            if(qType.equalsIgnoreCase("byCurrency")){
                String currency = json.getString("queryValue");
                try {
                    jsonResponse = nodeRPC.proxy().startTrackedFlowDynamic
                                    (QueryBondsFlow.GetBondsByCurrency.class, currency)
                            .getReturnValue().get();
                } catch (InterruptedException e) {
                    log.error("Exception query Corda", e);
                } catch (ExecutionException e) {
                    log.error("Exception query Corda", e);
                }
                responseJson.put("msg", jsonResponse);

            } else if (qType.equalsIgnoreCase("byRating")) {
                String creditRating = json.getString("queryValue");
                try {
                    jsonResponse = nodeRPC.proxy().startTrackedFlowDynamic
                                    (QueryBondsFlow.GetBondsByRating.class, creditRating)
                            .getReturnValue().get();
                } catch (InterruptedException e) {
                    log.error("Exception query Corda", e);
                } catch (ExecutionException e) {
                    log.error("Exception query Corda", e);
                }
                responseJson.put("msg", jsonResponse);

            } else if (qType.equalsIgnoreCase("lessThanMaturityDate")) {
                String maturityDate = json.getString("queryValue");
                try {
                    jsonResponse = nodeRPC.proxy().startTrackedFlowDynamic
                                    (QueryBondsFlow.GetBondLessThanMaturityDate.class, maturityDate)
                            .getReturnValue().get();
                } catch (InterruptedException e) {
                    log.error("Exception query Corda", e);
                } catch (ExecutionException e) {
                    log.error("Exception query Corda", e);
                }
                responseJson.put("msg", jsonResponse);
            } else if (qType.equalsIgnoreCase("greaterThanMaturityDate")) {
                String maturityDate = json.getString("queryValue");
                try {
                    jsonResponse = nodeRPC.proxy().startTrackedFlowDynamic
                                    (QueryBondsFlow.GetBondGreaterThanMaturityDate.class, maturityDate)
                            .getReturnValue().get();
                } catch (InterruptedException e) {
                    log.error("Exception query Corda", e);
                } catch (ExecutionException e) {
                    log.error("Exception query Corda", e);
                }
                responseJson.put("msg", jsonResponse);

            } else if (qType.equalsIgnoreCase("byTeamStateLinearID")) {
                String teamStateLinearID = json.getString("queryValue");
                try {
                    jsonResponse = nodeRPC.proxy().startTrackedFlowDynamic
                                    (QueryBondsFlow.GetBondByTermStateLinearID.class, teamStateLinearID)
                            .getReturnValue().get();
                } catch (InterruptedException e) {
                    log.error("Exception query Corda", e);
                } catch (ExecutionException e) {
                    log.error("Exception query Corda", e);
                }
                responseJson.put("msg", jsonResponse);
            }
        }

        private HashMap<String, String> whoami() {
            HashMap<String, String> myMap = new HashMap<>();
            myMap.put("me", me.toString());
            return myMap;
        }

        public HashMap<String, List<String>> getPeers() {
            HashMap<String, List<String>> myMap = new HashMap<>();

            // Find all nodes that are not notaries, ourself, or the network map.
            Stream<NodeInfo> filteredNodes = nodeRPC.proxy().networkMapSnapshot().stream()
                    .filter(el -> !isNotary(el) && !isMe(el) && !isNetworkMap(el));
            // Get their names as strings
            List<String> nodeNames = filteredNodes.map(el -> el.getLegalIdentities().get(0).getName().toString())
                    .collect(Collectors.toList());

            myMap.put("peers", nodeNames);
            return myMap;
        }

        private boolean isNotary(NodeInfo nodeInfo) {
            return !nodeRPC.proxy()
                    .notaryIdentities()
                    .stream().filter(el -> nodeInfo.isLegalIdentity(el))
                    .collect(Collectors.toList()).isEmpty();
        }

        private boolean isMe(NodeInfo nodeInfo) {
            return nodeInfo.getLegalIdentities().get(0).getName().equals(me);
        }

        private boolean isNetworkMap(NodeInfo nodeInfo) {
            return nodeInfo.getLegalIdentities().get(0).getName().getOrganisation().equals("Network Map Service");
        }


    }
}