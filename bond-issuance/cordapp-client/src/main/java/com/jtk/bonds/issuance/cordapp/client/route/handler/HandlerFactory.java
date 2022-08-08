package com.jtk.bonds.issuance.cordapp.client.route.handler;

import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

public class HandlerFactory {
    private static final Logger log = LoggerFactory.getLogger(HandlerFactory.class);
    public static void buildRoutes(Vertx vertx, RouterBuilder routerBuilder) {
        routerBuilder
                .operation("api-ping-hello")
                .handler(routingContext -> {
                    log.info("api-ping-hello API called");
                    Context vertxOrCreateContext = vertx.getOrCreateContext();
                    JsonObject helloObject = new JsonObject();
                    helloObject.put("message", vertxOrCreateContext.config().getString("welcome.message"));
                    helloObject.put("date", LocalDate.now().toString());
                    routingContext
                            .response()
                            .setStatusCode(200)
                            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                            .end(helloObject.encodePrettily());
                });
    }
}
