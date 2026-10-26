package de.etiennebader.civ.application.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityRepository;

import java.util.Objects;

/**
 * Ein Akteur gehört höchstens einer Gemeinde an, wie bei Towny. Die Regel betrifft alle
 * Polities zugleich und liegt deshalb in der Application-Schicht.
 */
final class MunicipalMembershipRule {

    private final PolityRepository polities;

    MunicipalMembershipRule(PolityRepository polities) {
        this.polities = Objects.requireNonNull(polities, "polities");
    }

    /**
     * @throws MemberOfOtherPolityException wenn der Akteur bereits einer Gemeinde angehört
     */
    void requireNoMunicipality(ActorRef actor) {
        polities.findAll().stream()
                .filter(polity -> polity.kind() == PolityKind.TERRITORIAL)
                .filter(polity -> polity.isMember(actor))
                .findFirst()
                .ifPresent(polity -> {
                    throw new MemberOfOtherPolityException(actor, polity);
                });
    }
}
