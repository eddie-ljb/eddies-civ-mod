package de.etiennebader.civ.server.protection;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.protection.Denial;
import de.etiennebader.civ.domain.protection.Hazard;
import de.etiennebader.civ.domain.protection.ProtectedAction;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.Claim;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Texts;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Prüft Handlungen von Spielern gegen den Gebietsschutz und liefert bei Verweigerung den Hinweis,
 * den der Spieler sieht. Admins können den Schutz für sich vorübergehend umgehen, etwa um ein
 * Gemeindehaus zu entfernen; das gilt bis zum Neustart des Servers.
 */
public final class ProtectionService {

    private final WorldQueries queries;
    private final Texts texts;
    private final Set<PlayerRef> bypassing = new HashSet<>();

    public ProtectionService(WorldQueries queries, Texts texts) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.texts = Objects.requireNonNull(texts, "texts");
    }

    /**
     * @return der Hinweis für den Spieler, falls die Handlung verweigert wird; leer, wenn sie erlaubt ist
     */
    public Optional<Message> check(PlayerRef player, ProtectedAction action, BlockPosition position) {
        Objects.requireNonNull(player, "player");
        if (bypassing.contains(player)) {
            return Optional.empty();
        }
        return queries.access(player, action, position).map(this::describe);
    }

    /** Ob eine Naturgewalt an diesem Block wirken darf, z. B. ob eine Explosion ihn zerstört. */
    public boolean allows(Hazard hazard, BlockPosition position) {
        return queries.allows(hazard, position);
    }

    /**
     * Ob ein Gesetz einer fremden Mod die Handlung erlaubt: liest den Wahrheitswert des Parameters aus dem
     * geltenden Recht der Gemeinde, die {@code scope} bestimmt. Gilt kein Recht (Wildnis, ohne Gemeinde),
     * ist die Handlung erlaubt; Admins mit Umgehung ({@code /civ admin umgehen}) unterliegen keinem Gesetz.
     * Der Parameter sollte {@code true} als „erlaubt“ lesen, damit der Standardwert nichts verbietet.
     *
     * @param key      mit {@link de.etiennebader.civ.domain.law.ExtensionParameters} angemeldeter Schalter
     * @param position der Ort der Handlung; nur für {@link LawScope#TERRITORY} nötig
     */
    public boolean permits(PlayerRef player, ParameterKey<Boolean> key, LawScope scope, BlockPosition position) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(scope, "scope");
        if (bypassing.contains(player)) {
            return true;
        }
        Optional<PolityView> polity = switch (scope) {
            case TERRITORY -> townAt(Objects.requireNonNull(position, "position"));
            case CITIZENSHIP -> queries.municipalityOf(player).map(PolityView.class::cast);
        };
        return polity.map(found -> queries.effectiveLaws(found).valueOf(key)).orElse(true);
    }

    /** Ob Spieler einander hier verletzen dürfen ({@code pvp_allowed}); in der Wildnis immer. */
    public boolean allowsPvp(BlockPosition position) {
        return lawAt(position, StandardParameters.PVP_ALLOWED).orElse(true);
    }

    /** Ob feindliche Monster hier leben dürfen ({@code hostile_mobs_allowed}); in der Wildnis immer. */
    public boolean allowsHostileMobs(BlockPosition position) {
        return lawAt(position, StandardParameters.HOSTILE_MOBS_ALLOWED).orElse(true);
    }

    /**
     * Ob ein Spieler hier Tiere verletzen darf: Mitglieder der Gemeinde immer, Fremde nur mit
     * {@code outsiders_may_hurt_animals}; in der Wildnis jeder.
     */
    public boolean mayHurtAnimal(PlayerRef player, BlockPosition position) {
        if (bypassing.contains(player)) {
            return true;
        }
        Optional<PolityView> town = townAt(position);
        if (town.isEmpty() || town.get().isMember(player)) {
            return true;
        }
        return queries.effectiveLaws(town.get()).valueOf(StandardParameters.OUTSIDERS_MAY_HURT_ANIMALS);
    }

    /** Ob dieser Spieler hier etwas angreifen darf, das einem anderen Spieler gehört (PvP). */
    public Optional<Message> checkPvp(PlayerRef attacker, BlockPosition position) {
        if (bypassing.contains(attacker) || allowsPvp(position)) {
            return Optional.empty();
        }
        return Optional.of(Message.of(MessageKey.PVP_FORBIDDEN, townAt(position).map(town -> town.name().value())
                .orElse("-")));
    }

    private Optional<PolityView> townAt(BlockPosition position) {
        return queries.territory().claimAt(position.chunk()).map(Claim::polity).flatMap(queries::polity);
    }

    private Optional<Boolean> lawAt(BlockPosition position, ParameterKey<Boolean> key) {
        return townAt(position).map(town -> queries.effectiveLaws(town).valueOf(key));
    }

    /**
     * Schaltet die Umgehung für einen Admin um.
     *
     * @return ob der Spieler den Schutz jetzt umgeht
     */
    public boolean toggleBypass(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        if (bypassing.remove(player)) {
            return false;
        }
        bypassing.add(player);
        return true;
    }

    private Message describe(Denial denial) {
        String polity = texts.polityName(denial.polity());
        return switch (denial.reason()) {
            case FOREIGN_TERRITORY -> Message.of(MessageKey.PROTECTED_TERRITORY, polity);
            case FOREIGN_PLOT -> Message.of(MessageKey.PROTECTED_PLOT, texts.actor(denial.owner()));
            case SEAT -> Message.of(MessageKey.PROTECTED_SEAT, polity);
        };
    }
}
