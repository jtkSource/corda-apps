package com.jtk.bond.issuance.contract.contants;

import net.corda.core.serialization.CordaSerializable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

@CordaSerializable
public enum BondCreditRating {
    AAA("AAA"),
    AA_PLUS("AA+"),
    AA("AA"),
    AA_MINUS("AA-"),
    A_PLUS("A+"),

    BBB_PLUS("BBB+"),
    BBB("BBB"),
    BBB_MINUS("BBB-"),
    BB_PLUS("BB+"),
    BB("BB"),
    BB_MINUS("BB-"),
    B_PLUS("B+"),
    B("B"),
    B_MINUS("B-"),
    CCC("CCC"),

    DDD("DDD"),
    DD("DD"),
    D("D"), NA("None");
    private static HashMap<String, BondCreditRating> lookup = new HashMap<>();

    static {
        Arrays.stream(BondCreditRating.values())
                .forEach(bondCreditRating -> {
                    lookup.put(bondCreditRating.value, bondCreditRating);
                });
    }
    public static Optional<BondCreditRating> lookupRating(String rating){
        return Optional.ofNullable(lookup.get(rating));
    }
    private String value;

    BondCreditRating(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }
}
