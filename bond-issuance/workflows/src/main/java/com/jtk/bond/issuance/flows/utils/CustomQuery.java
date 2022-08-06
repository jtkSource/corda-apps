package com.jtk.bond.issuance.flows.utils;

import com.jtk.bond.issuance.contract.contants.BondStatus;
import com.jtk.bond.issuance.state.TermState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.node.ServiceHub;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class CustomQuery {
    private final static DateTimeFormatter locateDateformat = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static List<TermState> queryTermsPointerByCurrency(String currency, ServiceHub serviceHub) {
        List<StateAndRef<TermState>> statesAndRef = serviceHub.getVaultService().queryBy(TermState.class).getStates();
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(TermState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(ts-> ts.getCurrency().equals(currency) &&
                        ts.getBondStatus().equals(BondStatus.ACTIVE.name()))
                .collect(Collectors.toList());
    }

    public static List<TermState> queryTermsPointerByCreditRating(String rating, ServiceHub serviceHub) {
        List<StateAndRef<TermState>> statesAndRef = serviceHub.getVaultService().queryBy(TermState.class).getStates();
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(TermState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(ts-> ts.getCreditRating().equals(rating) &&
                        ts.getBondStatus().equals(BondStatus.ACTIVE.name()))
                .collect(Collectors.toList());
    }

    public static List<TermState> queryTermsPointerLessThanMaturityDate(String maturityDate, ServiceHub serviceHub) {
        List<StateAndRef<TermState>> statesAndRef = serviceHub.getVaultService().queryBy(TermState.class).getStates();
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(TermState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(ts-> ts.getBondStatus().equals(BondStatus.ACTIVE.name()))
                .filter(ts-> {
                    LocalDate termMaturityDate = LocalDate.parse(ts.getMaturityDate(), locateDateformat);
                    LocalDate queryMaturityDate = LocalDate.parse(maturityDate, locateDateformat);
                    return termMaturityDate.isBefore(queryMaturityDate);
                })
                .collect(Collectors.toList());
    }
    public static List<TermState> queryTermsPointerGreaterThanMaturityDate(String maturityDate, ServiceHub serviceHub) {
        List<StateAndRef<TermState>> statesAndRef = serviceHub.getVaultService().queryBy(TermState.class).getStates();
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(TermState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(ts-> ts.getBondStatus().equals(BondStatus.ACTIVE.name()))
                .filter(ts-> {
                    LocalDate termMaturityDate = LocalDate.parse(ts.getMaturityDate(), locateDateformat);
                    LocalDate queryMaturityDate = LocalDate.parse(maturityDate, locateDateformat);
                    return termMaturityDate.isAfter(queryMaturityDate);
                })
                .collect(Collectors.toList());
    }

    public static StateAndRef<TermState> queryTermsByTeamStateLinearID(UniqueIdentifier teamStateLinearID, ServiceHub serviceHub) {
        List<StateAndRef<TermState>> statesAndRef = serviceHub.getVaultService().queryBy(TermState.class).getStates();
        return statesAndRef.stream()
                .filter(sr-> sr.getState().getData().getBondStatus().equals(BondStatus.ACTIVE.name()))
                .filter(sr-> sr.getState().getData().getLinearId().equals(teamStateLinearID))
                .findAny()
                .orElseThrow(()-> new IllegalArgumentException("TeamStateLinearID ="+teamStateLinearID.toString()+ " not found from vault"));
    }
}
