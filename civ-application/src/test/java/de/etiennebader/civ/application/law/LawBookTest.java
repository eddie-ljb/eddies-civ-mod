package de.etiennebader.civ.application.law;

import de.etiennebader.civ.application.ApplicationFixture;
import de.etiennebader.civ.application.constitution.ProclaimConstitution;
import de.etiennebader.civ.application.federation.FoundFederation;
import de.etiennebader.civ.application.polity.AmendLaw;
import de.etiennebader.civ.application.polity.CastVote;
import de.etiennebader.civ.application.polity.JoinPolity;
import de.etiennebader.civ.application.polity.VoteOutcome;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.law.Statute;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.protection.ProtectedAction;
import de.etiennebader.civ.domain.protection.Hazard;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PolityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static de.etiennebader.civ.application.ApplicationFixture.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LawBookTest {

    private final ApplicationFixture fixture = new ApplicationFixture();
    private final PlayerRef alice = fixture.player();
    private final PlayerRef bob = fixture.player();
    private final PlayerRef stranger = fixture.player();
    private final PolityId nordhain = fixture.found("Nordhain", alice, 0, 0);

    @Test
    @DisplayName("Spitze erlässt einen Paragraphen: erkannte Regelungen gelten, Rest bleibt Text; nur die Spitze verordnet")
    void decree() {
        join(bob);
        EnactParagraph.Result result = enact(alice, "Hausordnung",
                "Explosionen sind verboten. Fremde dürfen Türen benutzen. Wer stiehlt, wird verbannt.");
        assertTrue(result.statute().isPresent());
        assertFalse(town().laws().valueOf(StandardParameters.EXPLOSIONS_ALLOWED));
        assertTrue(town().laws().valueOf(StandardParameters.OUTSIDERS_MAY_INTERACT));
        Statute statute = town().laws().inForce().getLast().statute();
        assertEquals("Hausordnung", statute.title());
        assertTrue(statute.text().contains("verbannt"));
        assertEquals(List.of("Wer stiehlt", "wird verbannt."), result.recognition().unrecognized());

        assertEquals(ErrorCode.NOT_OFFICE_HOLDER, code(() -> enact(bob, "Bobs Gesetz", "Feuer ist erlaubt.")));
        assertEquals(ErrorCode.STATUTE_WITHOUT_VALUES, code(() -> enact(alice, "Leer", " ")));

        fixture.app.repealParagraph().execute(new RepealParagraph.Command(nordhain, alice, statute.id(), DAY));
        assertTrue(town().laws().valueOf(StandardParameters.EXPLOSIONS_ALLOWED) == false,
                "Standardwert: Explosionen ohnehin aus");
        assertTrue(town().laws().inForce().stream().noneMatch(law -> law.statute().id().equals(statute.id())));
    }

    @Test
    @DisplayName("Mit Abstimmungspflicht wird ein Paragraph zum Antrag; Annahme erlässt, Aufhebung ebenso per Antrag")
    void byVote() {
        join(bob);
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, alice,
                new ParameterValue<>(StandardParameters.LAWS_BY_VOTE, true), DAY));
        EnactParagraph.Result result = enact(bob, "Steuergesetz", "Die Umsatzsteuer beträgt 10 %.");
        assertTrue(result.statute().isEmpty());
        assertEquals(VoteOutcome.OPEN, result.proposal().orElseThrow().outcome());
        assertInstanceOf(Motion.Enact.class, town().proposals().iterator().next().motion());

        assertEquals(VoteOutcome.ACCEPTED, fixture.app.castVote().execute(new CastVote.Command(nordhain,
                result.proposal().get().proposal(), alice, true, DAY)));
        assertEquals(10L, town().laws().valueOf(StandardParameters.SALES_TAX_PERCENT));
        Statute statute = town().laws().inForce().getLast().statute();
        assertEquals("Steuergesetz", statute.title());

        var repeal = fixture.app.repealParagraph().execute(new RepealParagraph.Command(nordhain, bob, statute.id(), DAY));
        assertTrue(repeal.isPresent());
        fixture.app.castVote().execute(new CastVote.Command(nordhain, repeal.get().proposal(), alice, true, DAY));
        assertEquals(0L, town().laws().valueOf(StandardParameters.SALES_TAX_PERCENT));
    }

    @Test
    @DisplayName("Ein Paragraph mit Verfassungsartikel braucht nach dem Ausrufen die Verfassungsmehrheit")
    void constitutionalParagraph() {
        join(bob);
        fixture.app.proclaimConstitution().execute(new ProclaimConstitution.Command(nordhain, alice, DAY));
        EnactParagraph.Result result = enact(alice, "Wahlrecht", "Dorfbewohner wählen mit 50 %. Feuer ist verboten.");
        assertTrue(result.proposal().isPresent(), "Verfassungsartikel: keine Verordnung");
        assertEquals(VoteOutcome.OPEN, result.proposal().get().outcome(), "1 von 2 sind keine zwei Drittel");
    }

    @Test
    @DisplayName("Bundesrecht bricht Gemeinderecht, wenn der Bund es vorsieht; Verfahrensregeln bleiben eigene Sache")
    void federalPrecedence() {
        PolityId land = fixture.app.foundFederation().execute(new FoundFederation.Command(PolityName.of("Westmark"),
                "land", nordhain, alice, DAY));
        fixture.app.amendLaw().execute(new AmendLaw.Command(nordhain, alice,
                new ParameterValue<>(StandardParameters.EXPLOSIONS_ALLOWED, true), DAY));
        assertTrue(effective(nordhain).valueOf(StandardParameters.EXPLOSIONS_ALLOWED));

        enact(alice, land, "Landesfrieden", "Explosionen sind verboten. Abstimmungen dauern 5 Tage.");
        assertFalse(effective(nordhain).valueOf(StandardParameters.EXPLOSIONS_ALLOWED));
        assertEquals(Optional.of(fixture.app.queries().polity(land).orElseThrow()),
                effective(nordhain).overriddenBy(StandardParameters.EXPLOSIONS_ALLOWED));
        assertEquals(2L, effective(nordhain).valueOf(StandardParameters.VOTE_DAYS), "Verfahren bleibt Gemeindesache");
        assertFalse(fixture.app.queries().allows(Hazard.EXPLOSION, ApplicationFixture.chunk(0, 0).center(70)),
                "Schutz richtet sich nach Bundesrecht");
        assertTrue(fixture.app.queries().access(stranger, ProtectedAction.BUILD, ApplicationFixture.chunk(0, 0).center(70))
                .isPresent());

        fixture.app.amendLaw().execute(new AmendLaw.Command(land, alice,
                new ParameterValue<>(StandardParameters.FEDERAL_LAW_PREVAILS, false), DAY));
        assertTrue(effective(nordhain).valueOf(StandardParameters.EXPLOSIONS_ALLOWED), "ohne Vorrang gilt Gemeinderecht");
    }

    private EnactParagraph.Result enact(PlayerRef author, String title, String text) {
        return enact(author, nordhain, title, text);
    }

    private EnactParagraph.Result enact(PlayerRef author, PolityId polity, String title, String text) {
        return fixture.app.enactParagraph().execute(new EnactParagraph.Command(polity, author, title, text, DAY));
    }

    private EffectiveLaws effective(PolityId polity) {
        return fixture.app.queries().effectiveLaws(fixture.app.queries().polity(polity).orElseThrow());
    }

    private void join(PlayerRef player) {
        fixture.app.joinPolity().execute(new JoinPolity.Command(nordhain, player, MembershipType.CITIZEN, DAY));
    }

    private PolityView town() {
        return fixture.app.queries().polity(nordhain).orElseThrow();
    }

    private static ErrorCode code(Runnable action) {
        return assertThrows(DomainException.class, action::run).code();
    }
}
