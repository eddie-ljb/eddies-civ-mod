package de.etiennebader.civ.server.command;

import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.SettlementTier;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.ClaimType;

import java.util.Objects;
import java.util.UUID;

/**
 * Macht aus Werten der Domäne Argumente für {@link Message}: Namen statt UUIDs, übersetzbare
 * Begriffe statt Enum-Konstanten.
 */
public final class Texts {

    private static final int SHORT_ID_LENGTH = 8;

    private final WorldQueries queries;
    private final PlayerDirectory players;

    public Texts(WorldQueries queries, PlayerDirectory players) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.players = Objects.requireNonNull(players, "players");
    }

    /** Die Fehlermeldung zu einer Domänenausnahme, mit lesbaren Argumenten. */
    public Message error(DomainException exception) {
        return Message.of(exception.code().translationKey(), exception.arguments().stream().map(this::argument).toArray());
    }

    /**
     * Ein lesbarer Name: Spielername, Gemeindename oder für Unbekannte und Bewohner die ersten
     * Zeichen der UUID mit vorangestelltem {@code #}.
     */
    public String actor(ActorRef actor) {
        return switch (actor) {
            case PlayerRef player -> players.nameOf(player).orElseGet(() -> shortId(player.uuid()));
            case PersonRef person -> shortId(person.uuid());
            case PolityRef polity -> polityName(polity.id());
        };
    }

    public String polityName(PolityId polity) {
        return queries.polity(polity).map(found -> found.name().value()).orElseGet(() -> shortId(polity.value()));
    }

    public Message money(Money money) {
        return Message.of(TranslationKeys.MONEY, money.amount(), Message.of(TranslationKeys.currency(money.currency())));
    }

    /** Der Wert einer Regelung: Wahrheitswerte als „ja“/„nein“, sonst als Zahl oder Text. */
    public Object lawValue(Object value) {
        return value instanceof Boolean bool ? Message.of(TranslationKeys.yesNo(bool)) : value.toString();
    }

    public Message tier(SettlementTier tier) {
        return Message.of(TranslationKeys.tier(tier));
    }

    public Message claimType(ClaimType type) {
        return Message.of(TranslationKeys.claimType(type));
    }

    private Object argument(Object value) {
        return switch (value) {
            case String text -> text;
            case Number number -> number.toString();
            case Message message -> message;
            case ActorRef actor -> actor(actor);
            case Money money -> money(money);
            case PolityId polity -> polityName(polity);
            case PolityView polity -> polity.name().value();
            case SettlementTier tier -> tier(tier);
            case ClaimType type -> claimType(type);
            default -> value.toString();
        };
    }

    private static String shortId(UUID id) {
        return "#" + id.toString().substring(0, SHORT_ID_LENGTH);
    }
}
