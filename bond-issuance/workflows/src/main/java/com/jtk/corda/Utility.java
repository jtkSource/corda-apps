package com.jtk.corda;

import net.corda.core.identity.Party;
import net.corda.core.node.services.IdentityService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Utility {

    private Utility(){}

    public static List<Party>getLegalIdentitiesByOU(IdentityService identityService, String organizationalUnit){
        List<Party> parties = new ArrayList<>();
        identityService.getAllIdentities().forEach(partyAndCertificate -> {
            Party party = partyAndCertificate.getParty();
            String ou = party.getName().getOrganisationUnit();
            if(ou != null && ou.equals(organizationalUnit)){
                parties.add(party);
            }
        });
        return parties;
    }

    public static List<Party> getLegalIdentities(IdentityService identityService,
                                                 List<String> names,
                                                 boolean exactMatch){
        List<Party> observers = new ArrayList<>();
        for(String observerName : names){
            Set<Party> observerSet = identityService.partiesFromName(observerName, exactMatch);
            if (observerSet.size() != 1) {
                final String errMsg = String.format("Found %d identities ", observerSet.size());
                throw new IllegalStateException(errMsg);
            }
            observers.add(observerSet.iterator().next());
        }
        return observers;
    }

}
