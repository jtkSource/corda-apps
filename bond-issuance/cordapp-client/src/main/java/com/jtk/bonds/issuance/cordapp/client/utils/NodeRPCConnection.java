package com.jtk.bonds.issuance.cordapp.client.utils;

import io.vertx.core.json.JsonObject;
import net.corda.client.rpc.CordaRPCClient;
import net.corda.client.rpc.CordaRPCConnection;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.utilities.NetworkHostAndPort;

public class NodeRPCConnection implements AutoCloseable{

    // The RPC port of the node we are connecting to.
    private String host;

    // The username for logging into the RPC client.
    private String username;

    // The password for logging into the RPC client.
    private char[] password;

    private int rpcPort;

    private CordaRPCConnection rpcConnection;

    private CordaRPCOps proxy;

    synchronized public static NodeRPCConnection getInstance(JsonObject config){
        if(instance == null){
            instance = new NodeRPCConnection();
            instance.host = config.getString("vertx.corda.rpc.host");
            instance.rpcPort = config.getInteger("vertx.corda.rpc.port");
            instance.username = config.getString("vertx.corda.rpc.username");
            instance.password = config.getString("vertx.corda.rpc.password").toCharArray();
            NetworkHostAndPort rpcAddress = new NetworkHostAndPort(instance.host, instance.rpcPort);
            CordaRPCClient rpcClient = new CordaRPCClient(rpcAddress);
            instance.rpcConnection = rpcClient.start(instance.username, new String(instance.password));
            instance.proxy = instance.rpcConnection.getProxy();
        }
        return instance;
    }

    private static NodeRPCConnection instance;

    public CordaRPCOps proxy() {
        return proxy;
    }

    @Override
    public void close() throws Exception {
        rpcConnection.notifyServerAndClose();
        instance = null;
    }
}
