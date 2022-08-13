package com.jtk.corda.workflows;

import net.corda.core.identity.CordaX500Name;

public enum CordaParties {
    NOTARY(CordaX500Name.parse("CN=SGX Notary,OU=Notary,O=SGX,L=Singapore,C=SG")),
    OBSERVER(CordaX500Name.parse("CN=MAS,OU=Observer,O=MAS,L=Singapore,C=SG")),
    CENTRAL_BANK(CordaX500Name.parse("CN=Central Bank,OU=CBDC,O=Central Bank,L=Singapore,C=SG"));
    private CordaX500Name cordaX500Name;
    CordaParties(CordaX500Name cordaX500Name) {
        this.cordaX500Name = cordaX500Name;
    }

    public CordaX500Name getCordaX500Name() {
        return cordaX500Name;
    }
}
