package com.jtk.corda.contants;

import net.corda.core.serialization.CordaSerializable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

@CordaSerializable
public enum BondType {
    TREASURY_BONDS("TB"),
    GOV_BONDS("GB"),
    CORPORATE_BONDS("CB"),
    NA("NONE");
    private static HashMap<String, BondType> lookup = new HashMap<>();

    static {
        Arrays.stream(BondType.values())
                .forEach(bondType -> {
                    lookup.put(bondType.value, bondType);
                });
    }
    public static Optional<BondType> lookup(String bondType){
        return Optional.ofNullable(lookup.get(bondType));
    }
    private String value;

    BondType(String bondType) {
        this.value = bondType;
    }

    public String getValue(){
        return this.value;
    }
}
