package com.jtk.corda.workflows.utils;
import com.jtk.corda.states.cash.issuance.CashState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.node.ServiceHub;

import java.util.List;
import java.util.stream.Collectors;

public class CustomQuery {

    public static List<CashState> queryCashStateByCurrency(String currencyCode, ServiceHub serviceHub) {
        List<StateAndRef<CashState>> statesAndRef = serviceHub.getVaultService().queryBy(CashState.class).getStates();
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(CashState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(cashState-> cashState.getCurrencyCode().equals(currencyCode))
                .collect(Collectors.toList());
    }

}
