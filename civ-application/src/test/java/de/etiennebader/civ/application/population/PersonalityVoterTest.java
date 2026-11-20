package de.etiennebader.civ.application.population;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.party.FoundParty;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalityVoterTest {

    private final ApplicationFixture fixture = new ApplicationFixture();

    @Test
    @DisplayName("partyAffinity(): ohne Loyalitätsangabe wie bisher voller Bonus für die eigene Partei")
    void withoutLoyaltyFullBonusForOwnParty() {
        PolityId partyId = foundParty();
        PolityView party = fixture.app.queries().polity(partyId).orElseThrow();
        Personality personality = new Personality(0, 0, 0);

        double withoutLoyalty = PersonalityVoter.partyAffinity(personality, Optional.of(partyId), Optional.of(party));
        double emptyLoyalty = PersonalityVoter.partyAffinity(personality, Optional.of(partyId), Optional.of(party),
                Optional.empty());
        assertEquals(withoutLoyalty, emptyLoyalty, 1e-9);
    }

    @Test
    @DisplayName("partyAffinity(): niedrige Loyalität zur eigenen Partei schwächt deren Bonus ab, hohe stärkt ihn auf das volle Maß")
    void loyaltyScalesOwnPartyBonus() {
        PolityId partyId = foundParty();
        PolityView party = fixture.app.queries().polity(partyId).orElseThrow();
        Personality personality = new Personality(0, 0, 0);

        double fullBonus = PersonalityVoter.partyAffinity(personality, Optional.of(partyId), Optional.of(party));
        double lowLoyalty = PersonalityVoter.partyAffinity(personality, Optional.of(partyId), Optional.of(party),
                Optional.of(-100));
        double highLoyalty = PersonalityVoter.partyAffinity(personality, Optional.of(partyId), Optional.of(party),
                Optional.of(100));

        assertTrue(lowLoyalty < fullBonus, "wenig loyale Mitglieder folgen der eigenen Partei schwächer");
        assertEquals(fullBonus, highLoyalty, 1e-9, "volle Loyalität entspricht dem alten vollen Bonus");
        assertEquals(fullBonus * 0.4, lowLoyalty, 1e-9, "minimale Loyalität lässt nur 40% des Bonus übrig");
    }

    private PolityId foundParty() {
        PlayerRef founder = fixture.player();
        return fixture.app.foundParty().execute(new FoundParty.Command(
                PolityName.of("Testpartei"), founder, new PartyProfile(0, 0, PartyProfile.DEFAULT_EMBLEM), CurrencyId.EMERALD, DAY));
    }
}
