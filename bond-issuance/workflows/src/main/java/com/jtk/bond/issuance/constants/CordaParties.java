package com.jtk.bond.issuance.constants;

import net.corda.core.serialization.CordaSerializable;

@CordaSerializable
public enum CordaParties {
    NOTARY("CN=SGX Notary,OU=Notary,O=SGX,L=Singapore,C=SG"),
    OBSERVER("CN=MAS,OU=Observer,O=MAS,L=Singapore,C=SG"),
    CENTRAL_BANK("CN=Central Bank,OU=CBDC,O=Central Bank,L=Singapore,C=SG");
    private String cordaX500Name;
    CordaParties(String cordaX500Name) {
        this.cordaX500Name = cordaX500Name;
    }

    public String getCordaX500Name() {
        return cordaX500Name;
    }
}
