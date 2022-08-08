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
                .operation("create-bond-terms")
                .handler(routingContext -> {
                    RequestParameters params = routingContext.get("parsedParameters");
                    JsonObject createBT = params.body().getJsonObject();
                    createBT.put("url","create-bond-terms");
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
                .operation("request-for-bond")
                .handler(routingContext -> {
                    RequestParameters params = routingContext.get("parsedParameters");
                    JsonObject createBT = params.body().getJsonObject();
                    createBT.put("url","request-for-bond");
                    vertx.eventBus()
                            .request("CORDA-API",createBT, handleCordaAPI(routingContext));
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
