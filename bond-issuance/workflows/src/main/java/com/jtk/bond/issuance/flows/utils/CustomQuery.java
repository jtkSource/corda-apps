package com.jtk.bond.issuance.flows.utils;

import com.jtk.bond.issuance.contract.contants.BondState;
import com.jtk.bond.issuance.state.TermState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.node.ServiceHub;

import java.util.List;
import java.util.stream.Collectors;

public class CustomQuery {
    public static List<TermState> queryTermsPointerByCurrency(String currency, ServiceHub serviceHub) {
        List<StateAndRef<TermState>> statesAndRef = serviceHub.getVaultService().queryBy(TermState.class).getStates();
        /*return statesAndRef.stream()
                        .filter(sr-> sr.getState().getData().getCurrency().equals(currency) &&
                                sr.getState().getData().getBondState().equals(BondState.ACTIVE.name()))
                                .map(sr->sr.getState().getData().toPointer(TermState.class))
                .collect(Collectors.toList());*/
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(TermState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(ts-> ts.getCurrency().equals(currency) &&
                        ts.getBondState().equals(BondState.ACTIVE.name()))
                .collect(Collectors.toList());

    }
}
