package com.jtk.corda.workflows.utils;

import com.jtk.corda.contants.BondStatus;
import com.jtk.corda.states.bond.issuance.BondState;
import com.jtk.corda.states.bond.issuance.TermState;
import com.jtk.corda.states.cash.issuance.CashState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.node.ServiceHub;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
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
                    return termMaturityDate.isBefore(queryMaturityDate) || termMaturityDate.isEqual(queryMaturityDate);
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
                    return termMaturityDate.isAfter(queryMaturityDate) || termMaturityDate.isEqual(queryMaturityDate);
                })
                .collect(Collectors.toList());
    }

    public static StateAndRef<TermState> queryTermsByTermStateLinearID(UniqueIdentifier teamStateLinearID, ServiceHub serviceHub) {
        List<StateAndRef<TermState>> statesAndRef = serviceHub.getVaultService().queryBy(TermState.class).getStates();
        return statesAndRef.stream()
                .filter(sr -> sr.getState().getData().getLinearId().equals(teamStateLinearID))
                .filter(sr -> sr.getState().getData().getBondStatus().equals(BondStatus.ACTIVE.name()))
                .findAny()
                .orElseThrow(()-> new IllegalArgumentException("TeamStateLinearID ="+teamStateLinearID.toString()+ " not found from vault"));
    }

    public static StateAndRef<TermState> queryInActiveTermsByTermStateLinearID(UniqueIdentifier teamStateLinearID, ServiceHub serviceHub) {
        List<StateAndRef<TermState>> statesAndRef = serviceHub.getVaultService().queryBy(TermState.class).getStates();
        return statesAndRef.stream()
                .filter(sr -> sr.getState().getData().getLinearId().equals(teamStateLinearID))
                .filter(sr -> !sr.getState().getData().getBondStatus().equals(BondStatus.ACTIVE.name()))
                .findAny()
                .orElseThrow(()-> new IllegalArgumentException("TeamStateLinearID ="+teamStateLinearID.toString()+ " not found from vault"));
    }

    public static List<BondState> queryBondByTermStateLinearID(UniqueIdentifier uniqueIdentifier, ServiceHub serviceHub) {
        List<StateAndRef<BondState>> statesAndRef = serviceHub.getVaultService().queryBy(BondState.class).getStates();
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(BondState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(bs-> bs.getBondStatus().equals(BondStatus.ACTIVE.name()))
                .filter(bs-> bs.getTermStateLinearID().equals(uniqueIdentifier))
                .collect(Collectors.toList());
    }

    public static List<BondState> queryInActiveBondByTermStateLinearID(UniqueIdentifier uniqueIdentifier, ServiceHub serviceHub) {
        List<StateAndRef<BondState>> statesAndRef = serviceHub.getVaultService().queryBy(BondState.class).getStates();
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(BondState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(bs-> !bs.getBondStatus().equals(BondStatus.ACTIVE.name()))
                .filter(bs-> bs.getTermStateLinearID().equals(uniqueIdentifier))
                .collect(Collectors.toList());
    }


    public static StateAndRef<BondState> queryBondByLinearID(UniqueIdentifier bondStateLinearID, ServiceHub serviceHub) {
        List<StateAndRef<BondState>> statesAndRef = serviceHub.getVaultService().queryBy(BondState.class).getStates();
        return statesAndRef.stream()
                .filter(sr-> sr.getState().getData().getBondStatus().equals(BondStatus.ACTIVE.name()))
                .filter(sr-> sr.getState().getData().getLinearId().equals(bondStateLinearID))
                .findAny()
                .orElse(null);
    }

    public static Collection<BondState> queryBondsPointerGreaterThanMaturityDate(String maturityDate, ServiceHub serviceHub) {
        List<StateAndRef<BondState>> statesAndRef = serviceHub.getVaultService().queryBy(BondState.class).getStates();
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(BondState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(ts-> ts.getBondStatus().equals(BondStatus.ACTIVE.name()))
                .filter(ts-> {
                    LocalDate bondMaturityDate = LocalDate.parse(ts.getMaturityDate(), locateDateformat);
                    LocalDate queryMaturityDate = LocalDate.parse(maturityDate, locateDateformat);
                    return bondMaturityDate.isAfter(queryMaturityDate);
                })
                .collect(Collectors.toList());
    }
    public static Collection<BondState> queryBondsPointerEqualMaturityDate(String maturityDate, ServiceHub serviceHub) {
        List<StateAndRef<BondState>> statesAndRef = serviceHub.getVaultService().queryBy(BondState.class).getStates();
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(BondState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(ts-> ts.getBondStatus().equals(BondStatus.ACTIVE.name()))
                .filter(ts-> {
                    LocalDate bondMaturityDate = LocalDate.parse(ts.getMaturityDate(), locateDateformat);
                    LocalDate queryMaturityDate = LocalDate.parse(maturityDate, locateDateformat);
                    return bondMaturityDate.isEqual(queryMaturityDate);
                })
                .collect(Collectors.toList());
    }
    public static List<BondState> queryBondsPointerWithCouponDate(String couponDate, ServiceHub serviceHub) {
        List<StateAndRef<BondState>> statesAndRef = serviceHub.getVaultService().queryBy(BondState.class).getStates();
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(BondState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(ts-> ts.getBondStatus().equals(BondStatus.ACTIVE.name()))
                .filter(ts-> {
                    LocalDate nextCouponDate = LocalDate.parse(ts.getNextCouponDate(), locateDateformat);
                    LocalDate todayCouponDate = LocalDate.parse(couponDate, locateDateformat);
                    return nextCouponDate.isEqual(todayCouponDate);
                })
                .collect(Collectors.toList());
    }


    public static Collection<BondState> queryBondsPointerLessThanMaturityDate(String maturityDate, ServiceHub serviceHub) {
        List<StateAndRef<BondState>> statesAndRef = serviceHub.getVaultService().queryBy(BondState.class).getStates();
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(BondState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(ts-> ts.getBondStatus().equals(BondStatus.ACTIVE.name()))
                .filter(ts-> {
                    LocalDate bondMaturityDate = LocalDate.parse(ts.getMaturityDate(), locateDateformat);
                    LocalDate queryMaturityDate = LocalDate.parse(maturityDate, locateDateformat);
                    return bondMaturityDate.isBefore(queryMaturityDate);
                })
                .collect(Collectors.toList());
    }

    public static List<BondState> queryBondsPointerByCurrency(String currency, ServiceHub serviceHub) {
        List<StateAndRef<BondState>> statesAndRef = serviceHub.getVaultService().queryBy(BondState.class).getStates();
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(BondState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(ts-> ts.getCurrency().equals(currency) &&
                        ts.getBondStatus().equals(BondStatus.ACTIVE.name()))
                .collect(Collectors.toList());
    }

    public static List<BondState> queryBondPointerByCreditRating(String rating, ServiceHub serviceHub) {
        List<StateAndRef<BondState>> statesAndRef = serviceHub.getVaultService().queryBy(BondState.class).getStates();
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(BondState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(ts-> ts.getCreditRating().equals(rating) &&
                        ts.getBondStatus().equals(BondStatus.ACTIVE.name()))
                .collect(Collectors.toList());
    }

    public static List<CashState> queryCashStateByCurrency(String currencyCode, ServiceHub serviceHub) {
        List<StateAndRef<CashState>> statesAndRef = serviceHub.getVaultService().queryBy(CashState.class).getStates();
        return statesAndRef.stream()
                .map(sr->sr.getState().getData().toPointer(CashState.class))
                .map(p->p.getPointer().resolve(serviceHub).getState().getData())
                .filter(cashState-> cashState.getCurrencyCode().equals(currencyCode))
                .collect(Collectors.toList());
    }
}
