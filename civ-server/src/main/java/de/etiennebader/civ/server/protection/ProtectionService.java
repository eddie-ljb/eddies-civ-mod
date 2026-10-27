package de.etiennebader.civ.server.protection;

import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.protection.Denial;
import de.etiennebader.civ.domain.protection.ProtectedAction;
import de.etiennebader.civ.domain.territory.BlockPosition;
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
