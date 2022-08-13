package com.jtk.corda.contants;

import net.corda.core.serialization.CordaSerializable;

@CordaSerializable
public enum BondStatus {
    ACTIVE,
    MATURED,
    INACTIVE;
}
