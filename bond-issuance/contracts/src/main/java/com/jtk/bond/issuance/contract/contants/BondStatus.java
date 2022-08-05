package com.jtk.bond.issuance.contract.contants;

import net.corda.core.serialization.CordaSerializable;

@CordaSerializable
public enum BondStatus {
    ACTIVE,
    MATURED,
    INACTIVE;
}
