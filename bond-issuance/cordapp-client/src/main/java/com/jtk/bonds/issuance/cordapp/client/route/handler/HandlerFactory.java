package com.jtk.bonds.issuance.cordapp.client.route.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.validation.RequestParameters;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandlerFactory {
    private static final Logger log = LoggerFactory.getLogger(HandlerFactory.class);
    public static void buildRoutes(Vertx vertx, RouterBuilder routerBuilder) {
        routerBuilder
                .operation("get-corda-details")
                .handler(routingContext -> {
                    RequestParameters params = routingContext.get("parsedParameters");
                    String apiId = params.pathParameter("apiId").getString();
                   JsonObject msg = new JsonObject();
                   msg.put("url",apiId);
                   vertx.eventBus()
                           .request("CORDA-API",msg, handleCordaAPI(routingContext));
                });
        routerBuilder
                .operation("issue-bond-terms")
                .handler(routingContext -> {
                    RequestParameters params = routingContext.get("parsedParameters");
                    JsonObject createBT = params.body().getJsonObject();
                    createBT.put("url","issue-bond-terms");
                    vertx.eventBus()
                            .request("CORDA-API",createBT, handleCordaAPI(routingContext));
                });
        routerBuilder
                .operation("query-bond-terms")
                .handler(routingContext -> {
                    RequestParameters params = routingContext.get("parsedParameters");
                    JsonObject createBT = params.body().getJsonObject();
                    createBT.put("url","query-bond-terms");
                    vertx.eventBus()
                            .request("CORDA-API",createBT, handleCordaAPI(routingContext));
                });
        routerBuilder
                .operation("query-bonds")
                .handler(routingContext -> {
                    RequestParameters params = routingContext.get("parsedParameters");
                    JsonObject createBT = params.body().getJsonObject();
                    createBT.put("url","query-bonds");
                    vertx.eventBus()
                            .request("CORDA-API",createBT, handleCordaAPI(routingContext));
                });
        routerBuilder
                .operation("query-cash")
                .handler(routingContext -> {
                    RequestParameters params = routingContext.get("parsedParameters");
                    JsonObject queryCash = params.body().getJsonObject();
                    queryCash.put("url","query-cash");
                    vertx.eventBus()
                            .request("CORDA-API",queryCash, handleCordaAPI(routingContext));
                });
        routerBuilder
                .operation("issue-bond")
                .handler(routingContext -> {
                    RequestParameters params = routingContext.get("parsedParameters");
                    JsonObject createBT = params.body().getJsonObject();
                    createBT.put("url","issue-bond");
                    vertx.eventBus()
                            .request("CORDA-API",createBT, handleCordaAPI(routingContext));
                });
        routerBuilder.operation("get-bond-tokens")
                .handler(routingContext -> {
                    RequestParameters params = routingContext.get("parsedParameters");
                    String bondId = params.pathParameter("termId").getString();
                    JsonObject msg = new JsonObject();
                    msg.put("url","get-bond-tokens");
                    msg.put("termId",bondId);
                    vertx.eventBus()
                            .request("CORDA-API",msg, handleCordaAPI(routingContext));
                });
        routerBuilder.operation("bond-coupon")
                .handler(routingContext -> {
                    RequestParameters params = routingContext.get("parsedParameters");
                    JsonObject couponsJson = params.body().getJsonObject();
                    couponsJson.put("url","bond-coupon");
                    vertx.eventBus()
                            .request("CORDA-API",couponsJson, handleCordaAPI(routingContext));
                });
        routerBuilder.operation("issue-cash")
                .handler(routingContext -> {
                    RequestParameters params = routingContext.get("parsedParameters");
                    JsonObject cashJson = params.body().getJsonObject();
                    cashJson.put("url","issue-cash");
                    vertx.eventBus()
                            .request("CORDA-API",cashJson, handleCordaAPI(routingContext));
                });
        routerBuilder.operation("transfer-cash")
                .handler(routingContext -> {
                    RequestParameters params = routingContext.get("parsedParameters");
                    JsonObject cashJson = params.body().getJsonObject();
                    cashJson.put("url","transfer-cash");
                    vertx.eventBus()
                            .request("CORDA-API",cashJson, handleCordaAPI(routingContext));
                });


    }

    @NotNull
    private static Handler<AsyncResult<Message<JsonObject>>> handleCordaAPI(RoutingContext routingContext) {
        return event -> {
            if (event.succeeded()) {
                JsonObject json = event.result().body();
                String response = json.encodePrettily();
                if(json.containsKey("msg")){
                    response = json.getString("msg");
                }
                routingContext
                        .response()
                        .setStatusCode(HttpResponseStatus.OK.code())
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .end(response);

            } else {
                log.error("Error response from corda");
                routingContext.response()
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .end("{'msg':'Record not found','code':" + HttpResponseStatus.INTERNAL_SERVER_ERROR.code() + "}");
            }
        };
    }
}
