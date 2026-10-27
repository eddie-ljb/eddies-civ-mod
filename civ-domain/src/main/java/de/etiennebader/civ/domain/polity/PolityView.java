package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.Collection;
import java.util.Optional;

/**
 * Lesender Zugriff auf eine Polity, z. B. für Abfragen außerhalb der Use Cases.
 */
public interface PolityView {

    PolityId id();

    PolityName name();

    PolityKind kind();

    SettlementTier tier();

    CurrencyId currency();

    Day foundedOn();

    /** Standort des Gemeindehauses; leer bei Polities ohne Sitz. */
    Optional<BlockPosition> seat();

    boolean isMember(ActorRef actor);

    Optional<Membership> membershipOf(ActorRef actor);

    /** Alle Mitgliedschaften in Beitrittsreihenfolge. */
    Collection<Membership> members();

    int memberCount();

    LawView laws();

    PolityRef asActor();
}
