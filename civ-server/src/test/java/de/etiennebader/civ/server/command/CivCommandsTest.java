package de.etiennebader.civ.server.command;

import de.etiennebader.civ.application.day.DayReport;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.shared.ErrorCode;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.shared.SeededIdGenerator;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.ChunkCoord;
import de.etiennebader.civ.domain.territory.ClaimType;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.server.CivServerFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CivCommandsTest {

    private final SeededIdGenerator ids = new SeededIdGenerator(5);
    private final PlayerRef alice = new PlayerRef(PlayerId.generate(ids));
    private final PlayerRef bob = new PlayerRef(PlayerId.generate(ids));
    private final CivServerFixture server = CivServerFixture.start(ids);
    private final CivCommands commands = server.commands();

    @Nested
    @DisplayName("Gründen, Beitreten, Austreten")
    class Membership {

        @Test
        @DisplayName("Gründen meldet Erfolg und macht den Spieler zum Mitglied")
        void foundsMunicipality() {
            Reply reply = commands.found(at(alice, 0, 0), "  Nordhain ");

            assertEquals(Reply.success(Message.of(MessageKey.FOUNDED, "Nordhain")), reply);
            assertEquals("Nordhain", server.queries().municipalityOf(alice).orElseThrow().name().value());
        }

        @Test
        @DisplayName("Ungültiger Name wird mit übersetzbarem Fehler abgelehnt")
        void rejectsInvalidName() {
            Reply reply = commands.found(at(alice, 0, 0), "X");

            assertFalse(reply.success());
            assertEquals(new Message(ErrorCode.INVALID_POLITY_NAME.translationKey(), List.of("X", "2", "32")),
                    reply.lines().getFirst());
        }

        @Test
        @DisplayName("Beitreten über den Namen in beliebiger Schreibweise")
        void joinsByName() {
            commands.found(at(alice, 0, 0), "Nordhain");

            Reply reply = commands.join(at(bob, 5, 5), "nordhain");

            assertEquals(Reply.success(Message.of(MessageKey.JOINED, "Nordhain")), reply);
        }

        @Test
        @DisplayName("Unbekannter Name → eigene Meldung mit dem gesuchten Namen")
        void rejectsUnknownName() {
            Reply reply = commands.join(at(bob, 0, 0), " Südhain ");

            assertEquals(Reply.failure(Message.of(MessageKey.UNKNOWN_POLITY_NAMED, "Südhain")), reply);
        }

        @Test
        @DisplayName("Fehlermeldungen zeigen Spielernamen statt UUIDs")
        void errorsShowPlayerNames() {
            server.players().record(bob, "Bob");
            commands.found(at(alice, 0, 0), "Nordhain");
            commands.found(at(bob, 9, 9), "Eichenfurt");

            Reply reply = commands.join(at(bob, 0, 0), "Nordhain");

            assertEquals(Reply.failure(new Message(ErrorCode.MEMBER_OF_OTHER_POLITY.translationKey(),
                    List.of("Bob", "Eichenfurt"))), reply);
        }

        @Test
        @DisplayName("Austreten ohne Gemeinde → NOT_IN_MUNICIPALITY; als letztes Mitglied → LAST_MEMBER")
        void leaveRules() {
            assertEquals(Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY)), commands.leave(at(alice, 0, 0)));
            commands.found(at(alice, 0, 0), "Nordhain");

            assertEquals(ErrorCode.LAST_MEMBER.translationKey(), commands.leave(at(alice, 0, 0)).lines().getFirst().key());

            commands.join(at(bob, 0, 0), "Nordhain");
            assertEquals(Reply.success(Message.of(MessageKey.LEFT, "Nordhain")), commands.leave(at(bob, 0, 0)));
        }
    }

    @Nested
    @DisplayName("Gebiet")
    class Territory {

        @Test
        @DisplayName("Angrenzender Chunk wird Kerngebiet, entfernter nur als Außenposten")
        void claimsAdjacentAndOutpost() {
            commands.found(at(alice, 0, 0), "Nordhain");

            Reply core = commands.claim(at(alice, 1, 0), ClaimType.CORE);
            Reply far = commands.claim(at(alice, 5, 5), ClaimType.CORE);

            assertEquals(Reply.success(Message.of(MessageKey.CLAIMED, 1, 0, "Nordhain",
                    Message.of(TranslationKeys.claimType(ClaimType.CORE)))), core);
            assertEquals(Reply.failure(Message.of(ErrorCode.CLAIM_NOT_ADJACENT.translationKey(), 5, 5)), far);
        }

        @Test
        @DisplayName("Ohne Gemeinde kann niemand Land beanspruchen")
        void claimRequiresMunicipality() {
            assertEquals(Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY)), commands.claim(at(bob, 0, 0), ClaimType.CORE));
        }

        @Test
        @DisplayName("Freigeben eines fremden Chunks → NOT_SOVEREIGN")
        void releaseForeignChunk() {
            commands.found(at(alice, 0, 0), "Nordhain");
            commands.found(at(bob, 9, 9), "Eichenfurt");

            Reply reply = commands.release(at(bob, 0, 0));

            assertEquals(Reply.failure(Message.of(ErrorCode.NOT_SOVEREIGN.translationKey(), 0, 0)), reply);
            assertEquals(Reply.success(Message.of(MessageKey.RELEASED, 1, 0, "Nordhain")),
                    claimThenRelease(at(alice, 1, 0)));
        }

        private Reply claimThenRelease(Caller caller) {
            commands.claim(caller, ClaimType.CORE);
            return commands.release(caller);
        }
    }

    @Nested
    @DisplayName("Übersichten")
    class Overviews {

        @Test
        @DisplayName("Info zeigt Gemeinde, Chunk-Besitzer, Kontostand und Tag")
        void infoForMember() {
            commands.found(at(alice, 0, 0), "Nordhain");

            Reply reply = commands.info(at(alice, 0, 0));

            assertEquals(List.of(
                    Message.of(MessageKey.INFO_MUNICIPALITY, "Nordhain"),
                    Message.of(MessageKey.INFO_CHUNK_CLAIMED, 0, 0, "Nordhain", Message.of(TranslationKeys.claimType(ClaimType.CORE))),
                    Message.of(MessageKey.INFO_BALANCE, money(0)),
                    Message.of(MessageKey.INFO_DAY, 1, 1)), reply.lines());
        }

        @Test
        @DisplayName("Info ohne Gemeinde in der Wildnis")
        void infoForNewcomer() {
            Reply reply = commands.info(at(bob, -3, 7));

            assertEquals(Message.of(MessageKey.INFO_NO_MUNICIPALITY), reply.lines().get(0));
            assertEquals(Message.of(MessageKey.INFO_CHUNK_WILDERNESS, -3, 7), reply.lines().get(1));
        }

        @Test
        @DisplayName("Steckbrief listet Mitglieder mit Namen und das Gebiet")
        void describesPolity() {
            server.players().record(alice, "Alice");
            commands.found(at(alice, 0, 0), "Nordhain");
            commands.join(at(bob, 0, 0), "Nordhain");
            commands.claim(at(alice, 1, 0), ClaimType.CORE);

            Reply own = commands.describe(at(bob, 0, 0), Optional.empty());

            assertEquals(Message.of(MessageKey.POLITY_HEADER, "Nordhain"), own.lines().get(0));
            assertEquals(Message.of(MessageKey.POLITY_MEMBERS, 2, "Alice, #" + bob.uuid().toString().substring(0, 8)),
                    own.lines().get(2));
            assertEquals(Message.of(MessageKey.POLITY_TERRITORY, 2, 2, 0), own.lines().get(3));
            assertEquals(own, commands.describe(at(alice, 9, 9), Optional.of("NORDHAIN")));
        }

        @Test
        @DisplayName("Liste aller Gemeinden, leer mit eigenem Hinweis")
        void listsPolities() {
            assertEquals(Reply.success(Message.of(MessageKey.LIST_EMPTY)), commands.list());
            commands.found(at(alice, 0, 0), "Nordhain");
            commands.claim(at(alice, 0, 1), ClaimType.CORE);

            assertEquals(List.of(Message.of(MessageKey.LIST_HEADER, 1), Message.of(MessageKey.LIST_ENTRY, "Nordhain", 1, 2)),
                    commands.list().lines());
        }
    }

    @Nested
    @DisplayName("Gemeindehaus")
    class TownHall {

        private final BlockPosition townHall = new BlockPosition(DimensionId.OVERWORLD, 5, 64, 9);

        @Test
        @DisplayName("Ein benannter Gemeindehaus-Block gründet die Gemeinde mit dem Block als Sitz")
        void foundsWithTownHall() {
            Reply reply = commands.foundWithTownHall(at(alice, 0, 0), Optional.of("Nordhain"), townHall);

            assertEquals(Reply.success(Message.of(MessageKey.FOUNDED, "Nordhain")), reply);
            assertEquals(Optional.of(townHall), server.queries().municipalityOf(alice).orElseThrow().seat());
            assertEquals(Message.of(MessageKey.POLITY_HEADER, "Nordhain"),
                    commands.describeTownHall(townHall).lines().getFirst());
        }

        @Test
        @DisplayName("Ohne Namen aus dem Amboss wird nichts gegründet")
        void requiresName() {
            assertEquals(Reply.failure(Message.of(MessageKey.TOWN_HALL_NEEDS_NAME)),
                    commands.foundWithTownHall(at(alice, 0, 0), Optional.empty(), townHall));
            assertEquals(Reply.failure(Message.of(MessageKey.TOWN_HALL_NEEDS_NAME)),
                    commands.foundWithTownHall(at(alice, 0, 0), Optional.of("  "), townHall));
            assertTrue(server.queries().polities().isEmpty());
        }

        @Test
        @DisplayName("Ein Gemeindehaus ohne Gemeinde meldet das")
        void orphanedTownHall() {
            assertEquals(Reply.failure(Message.of(MessageKey.TOWN_HALL_ORPHANED)), commands.describeTownHall(townHall));
        }
    }

    @Nested
    @DisplayName("Recht")
    class Laws {

        @Test
        @DisplayName("Die Rechtsübersicht zeigt jede Regelung mit Namen, Kennung und Wert")
        void listsLaws() {
            commands.found(at(alice, 0, 0), "Nordhain");

            Reply reply = commands.laws(at(alice, 0, 0), Optional.empty());

            assertEquals(Message.of(MessageKey.LAWS_HEADER, "Nordhain"), reply.lines().getFirst());
            assertTrue(reply.lines().contains(Message.of(MessageKey.LAW_ENTRY,
                    Message.of(TranslationKeys.law(StandardParameters.OUTSIDERS_MAY_BUILD)), "outsiders_may_build",
                    Message.of(TranslationKeys.yesNo(false)))));
            assertEquals(StandardParameters.all().size() + 1, reply.lines().size());
        }

        @Test
        @DisplayName("Ein Bürger setzt eine Regelung; ja/nein werden als Wahrheitswert verstanden")
        void amendsLaw() {
            commands.found(at(alice, 0, 0), "Nordhain");

            Reply reply = commands.amendLaw(at(alice, 0, 0), "outsiders_may_interact", "ja");

            assertEquals(Reply.success(Message.of(MessageKey.LAW_AMENDED, "Nordhain",
                    Message.of(TranslationKeys.law(StandardParameters.OUTSIDERS_MAY_INTERACT)),
                    Message.of(TranslationKeys.yesNo(true)))), reply);
            assertTrue(server.queries().municipalityOf(alice).orElseThrow().laws()
                    .valueOf(StandardParameters.OUTSIDERS_MAY_INTERACT));
        }

        @Test
        @DisplayName("Unbekannte Regelung, unlesbarer oder unzulässiger Wert werden abgelehnt")
        void rejectsInvalidInput() {
            commands.found(at(alice, 0, 0), "Nordhain");

            assertEquals(Reply.failure(Message.of(MessageKey.UNKNOWN_LAW, "fliegen")),
                    commands.amendLaw(at(alice, 0, 0), "fliegen", "ja"));
            assertEquals(Reply.failure(Message.of(MessageKey.INVALID_LAW_VALUE, "viele", "max_outposts")),
                    commands.amendLaw(at(alice, 0, 0), "max_outposts", "viele"));
            assertEquals(ErrorCode.INVALID_PARAMETER_VALUE.translationKey(),
                    commands.amendLaw(at(alice, 0, 0), "max_outposts", "-1").lines().getFirst().key());
        }

        @Test
        @DisplayName("Nicht-Bürger dürfen das Recht einer Gemeinde nicht ändern")
        void outsiderCannotAmend() {
            commands.found(at(alice, 0, 0), "Nordhain");

            assertEquals(Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY)),
                    commands.amendLaw(at(bob, 0, 0), "outsiders_may_build", "ja"));
        }
    }

    private static Message money(long amount) {
        return Message.of(TranslationKeys.MONEY, amount, Message.of("eddies-civ-mod.currency.emerald"));
    }

    private static Caller at(PlayerRef player, int chunkX, int chunkZ) {
        return new Caller(player, "Spieler", new ChunkCoord(DimensionId.OVERWORLD, chunkX, chunkZ).center(70));
    }

    @Test
    @DisplayName("Ankündigung des neuen Tages nennt den beginnenden Tag und die eingezogene Grundsteuer")
    void announcesNewDay() {
        DayReport finished = server.tickUntilDayEnds();

        assertEquals(Message.of(MessageKey.NEW_DAY, 2, money(0)), commands.newDay(finished));
    }
}
