package de.etiennebader.civ.server.plot;

import de.etiennebader.civ.application.directory.PlayerDirectory;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.protection.PlotFlag;
import de.etiennebader.civ.domain.protection.PlotRights;
import de.etiennebader.civ.domain.protection.PlotRightsSource;
import de.etiennebader.civ.domain.protection.ProtectedAction;
import de.etiennebader.civ.domain.protection.Stance;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Granulares Grundstücksrecht: Der Inhaber stellt ein, was auf seinem Grundstück erlaubt ist.
 *
 * <ul>
 *   <li>{@code /civ grundstueck rechte}: zeigt die Einstellungen des Grundstücks, auf dem man steht.</li>
 *   <li>{@code rechte mitglieder|fremde <bauen|benutzen|behaelter> <ja|nein>}: was Mitglieder der Gemeinde und
 *       alle anderen dürfen.</li>
 *   <li>{@code rechte gast <Spieler> <bauen|benutzen|behaelter> <ja|nein>} und {@code rechte gastentfernen <Spieler>}:
 *       Rechte einzelner Spieler.</li>
 *   <li>{@code rechte regel <pvp|monster|explosionen|feuer|tiere> <gesetz|erlauben|verbieten>}: Regeln, die sonst das
 *       Gesetz der Gemeinde, des Landes und der Nation festlegt. Das gilt nur, wo das Gesetz
 *       {@code plot_rights_override} Grundstücksinhabern erlaubt, Recht zu überschreiben.</li>
 *   <li>{@code rechte zuruecksetzen}: zurück zum Standard.</li>
 * </ul>
 *
 * <p>Die Einstellungen gelten für den Inhaber, der sie gesetzt hat; wechselt das Grundstück den Besitzer, gilt wieder
 * der Standard.
 */
public final class PlotRightsService implements PlotRightsSource {

    private final WorldQueries queries;
    private final PlayerDirectory players;
    private final Texts texts;
    private final PlotRightsStore store;
    private final Map<PlotId, PlotRightsStore.Entry> entries;

    public PlotRightsService(WorldQueries queries, PlayerDirectory players, Texts texts, Optional<Path> file) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.players = Objects.requireNonNull(players, "players");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.store = new PlotRightsStore(file);
        this.entries = new LinkedHashMap<>(store.load());
    }

    /** Die Rechte eines Grundstücks, falls der aktuelle Inhaber sie eingestellt hat. */
    @Override
    public synchronized Optional<PlotRights> rightsOf(PolityId polity, PlotId plot) {
        PlotRightsStore.Entry entry = entries.get(plot);
        if (entry == null || !entry.polity().equals(polity)) {
            return Optional.empty();
        }
        return queries.land(polity).plot(plot).filter(found -> found.holder().equals(entry.holder()))
                .map(found -> entry.rights());
    }

    /** Die Rechte des Grundstücks, immer mit Standard als Rückfall. */
    public synchronized PlotRights rights(PolityId polity, PlotId plot) {
        return rightsOf(polity, plot).orElse(PlotRights.DEFAULT);
    }

    /** Ob Grundstücksinhaber hier Gesetze überschreiben dürfen. */
    public boolean overrideAllowed(PolityView polity) {
        return queries.effectiveLaws(polity).valueOf(StandardParameters.PLOT_RIGHTS_OVERRIDE);
    }

    // ------------------------------------------------------------------ Ansicht

    /** Eine Regel mit eigener Haltung und dem Ergebnis, das tatsächlich gilt. */
    public record RuleView(PlotFlag flag, Stance stance, boolean lawAllows, boolean effective) {
    }

    /** Alles zu den Rechten eines Grundstücks für Menü und Webseite. */
    public record View(PolityId town, String townName, PlotId plot, boolean canManage, boolean overrideAllowed,
                       PlotRights rights, List<RuleView> rules, List<String> guestNames) {
    }

    /** Das Grundstück, auf dem der Spieler steht, mit seinen Rechten. */
    public synchronized Optional<View> viewAt(PlayerRef viewer, de.etiennebader.civ.domain.territory.BlockPosition at) {
        return plotAt(at).map(found -> view(viewer, found.polity(), found.plot()));
    }

    public synchronized Optional<View> view(PlayerRef viewer, PolityId town, PlotId plotId) {
        return queries.polity(town).flatMap(polity -> queries.land(town).plot(plotId)
                .map(plot -> view(viewer, polity, plot)));
    }

    private View view(PlayerRef viewer, PolityView polity, Plot plot) {
        PlotRights rights = rights(polity.id(), plot.id());
        LawView laws = queries.effectiveLaws(polity);
        boolean override = laws.valueOf(StandardParameters.PLOT_RIGHTS_OVERRIDE);
        List<RuleView> rules = new ArrayList<>();
        for (PlotFlag flag : PlotFlag.values()) {
            boolean law = laws.valueOf(flag.law());
            rules.add(new RuleView(flag, rights.stance(flag), law,
                    override ? rights.stance(flag).resolve(law) : law));
        }
        List<String> guests = rights.guests().keySet().stream().map(texts::actor).toList();
        return new View(polity.id(), polity.name().value(), plot.id(), mayManage(viewer, polity, plot), override, rights,
                rules, guests);
    }

    private record Found(PolityView polity, Plot plot) {
    }

    private Optional<Found> plotAt(de.etiennebader.civ.domain.territory.BlockPosition position) {
        return queries.territory().sovereignOf(position.chunk()).flatMap(queries::polity).flatMap(polity ->
                queries.land(polity.id()).plotAt(position.dimension(), position.x(), position.z())
                        .map(plot -> new Found(polity, plot)));
    }

    /** Wer die Rechte einstellen darf: der Inhaber, bei einer Firma als Inhaber deren Leitung. */
    private boolean mayManage(PlayerRef player, PolityView town, Plot plot) {
        ActorRef holder = plot.holder();
        if (holder.equals(town.asActor())) {
            return false; // Gemeindeland: darüber entscheidet das Recht der Gemeinde
        }
        if (holder.equals(player)) {
            return true;
        }
        return holder instanceof PolityRef org && queries.polity(org.id()).map(p -> p.holds(player, Office.MAYOR))
                .orElse(false);
    }

    // ------------------------------------------------------------------ Befehle

    /** {@code /civ grundstueck rechte}: Einstellungen zeigen. */
    public synchronized Reply show(Caller caller) {
        Optional<Found> found = plotAt(caller.position());
        if (found.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PLOT_NONE_HERE));
        }
        View view = view(caller.player(), found.get().polity(), found.get().plot());
        if (found.get().plot().holder().equals(found.get().polity().asActor())) {
            return Reply.failure(Message.of(MessageKey.PLOT_RIGHTS_TOWN_LAND, view.townName()));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.PLOT_RIGHTS_HEADER, texts.actor(found.get().plot().holder()), view.townName(),
                Message.of(view.overrideAllowed() ? MessageKey.PLOT_RIGHTS_OVERRIDE_ON
                        : MessageKey.PLOT_RIGHTS_OVERRIDE_OFF)));
        lines.add(Message.of(MessageKey.PLOT_RIGHTS_MEMBERS, actionList(view.rights().members())));
        lines.add(Message.of(MessageKey.PLOT_RIGHTS_OUTSIDERS, actionList(view.rights().outsiders())));
        view.rights().guests().forEach((guest, actions) -> lines.add(Message.of(MessageKey.PLOT_RIGHTS_GUEST,
                texts.actor(guest), actionList(actions))));
        for (RuleView rule : view.rules()) {
            lines.add(Message.of(MessageKey.PLOT_RIGHTS_RULE, ruleName(rule.flag()), stanceName(rule.stance()),
                    Message.of(rule.effective() ? MessageKey.PLOT_RIGHTS_ALLOWED : MessageKey.PLOT_RIGHTS_FORBIDDEN)));
        }
        return Reply.success(lines);
    }

    /** Mitglieder oder Fremde: eine Handlung erlauben oder verbieten. */
    public synchronized Reply setAudience(Caller caller, boolean members, String actionInput, String allowInput) {
        Optional<ProtectedAction> action = parseAction(actionInput);
        Optional<Boolean> allowed = parseYesNo(allowInput);
        if (action.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PLOT_RIGHTS_UNKNOWN_ACTION, actionInput.strip()));
        }
        if (allowed.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PLOT_RIGHTS_UNKNOWN_ANSWER, allowInput.strip()));
        }
        return change(caller, rights -> members ? rights.withMembers(action.get(), allowed.get())
                : rights.withOutsiders(action.get(), allowed.get()));
    }

    /** Einem Spieler als Gast eine Handlung erlauben oder verbieten. */
    public synchronized Reply setGuest(Caller caller, String playerName, String actionInput, String allowInput) {
        Optional<PlayerRef> guest = players.findByName(playerName.strip());
        if (guest.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, playerName.strip()));
        }
        Optional<ProtectedAction> action = parseAction(actionInput);
        Optional<Boolean> allowed = parseYesNo(allowInput);
        if (action.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PLOT_RIGHTS_UNKNOWN_ACTION, actionInput.strip()));
        }
        if (allowed.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PLOT_RIGHTS_UNKNOWN_ANSWER, allowInput.strip()));
        }
        return change(caller, rights -> rights.guests().size() >= PlotRights.MAX_GUESTS
                && !rights.guests().containsKey(guest.get()) ? null
                : rights.withGuest(guest.get(), action.get(), allowed.get()));
    }

    public synchronized Reply removeGuest(Caller caller, String playerName) {
        Optional<PlayerRef> guest = players.findByName(playerName.strip());
        if (guest.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_RECIPIENT, playerName.strip()));
        }
        return change(caller, rights -> rights.withoutGuest(guest.get()));
    }

    /** Eine Regel abweichend vom Gesetz einstellen. */
    public synchronized Reply setRule(Caller caller, String ruleInput, String stanceInput) {
        Optional<PlotFlag> flag = parseFlag(ruleInput);
        Optional<Stance> stance = parseStance(stanceInput);
        if (flag.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PLOT_RIGHTS_UNKNOWN_RULE, ruleInput.strip()));
        }
        if (stance.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PLOT_RIGHTS_UNKNOWN_STANCE, stanceInput.strip()));
        }
        return change(caller, rights -> rights.withStance(flag.get(), stance.get()));
    }

    public synchronized Reply reset(Caller caller) {
        return change(caller, rights -> PlotRights.DEFAULT);
    }

    /**
     * Ändert die Rechte, wenn der Aufrufer sie einstellen darf.
     *
     * @param change liefert die neuen Rechte, oder {@code null}, wenn die Änderung nicht möglich ist
     */
    public synchronized Reply change(Caller caller, UnaryOperator<PlotRights> change) {
        Optional<Found> found = plotAt(caller.position());
        if (found.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PLOT_NONE_HERE));
        }
        return change(caller.player(), found.get().polity(), found.get().plot(), change);
    }

    /** Wie {@link #change(Caller, UnaryOperator)} für ein bestimmtes Grundstück, z. B. aus dem Menü. */
    public synchronized Reply change(PlayerRef player, PolityId town, PlotId plotId, UnaryOperator<PlotRights> change) {
        Optional<PolityView> polity = queries.polity(town);
        Optional<Plot> plot = queries.land(town).plot(plotId);
        if (polity.isEmpty() || plot.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.PLOT_NONE_HERE));
        }
        return change(player, polity.get(), plot.get(), change);
    }

    private Reply change(PlayerRef player, PolityView polity, Plot plot, UnaryOperator<PlotRights> change) {
        if (plot.holder().equals(polity.asActor())) {
            return Reply.failure(Message.of(MessageKey.PLOT_RIGHTS_TOWN_LAND, polity.name().value()));
        }
        if (!mayManage(player, polity, plot)) {
            return Reply.failure(Message.of(MessageKey.PLOT_RIGHTS_NOT_HOLDER));
        }
        PlotRights updated;
        try {
            updated = change.apply(rights(polity.id(), plot.id()));
        } catch (IllegalArgumentException e) {
            updated = null;
        }
        if (updated == null) {
            return Reply.failure(Message.of(MessageKey.PLOT_RIGHTS_TOO_MANY_GUESTS, PlotRights.MAX_GUESTS));
        }
        if (updated.isDefault()) {
            entries.remove(plot.id());
        } else {
            entries.put(plot.id(), new PlotRightsStore.Entry(polity.id(), plot.holder(), updated));
        }
        store.save(entries);
        return Reply.success(Message.of(MessageKey.PLOT_RIGHTS_SET));
    }

    /** Ein Grundstück wurde aufgelöst: seine Einstellungen werden nicht mehr gebraucht. */
    public synchronized void forgetMissing() {
        boolean changed = entries.entrySet().removeIf(entry ->
                queries.land(entry.getValue().polity()).plot(entry.getKey()).isEmpty());
        if (changed) {
            store.save(entries);
        }
    }

    // ------------------------------------------------------------------ Texte

    private static Message actionList(java.util.Set<ProtectedAction> actions) {
        return Message.of(MessageKey.PLOT_RIGHTS_ACTIONS,
                Message.of(actions.contains(ProtectedAction.BUILD) ? MessageKey.PLOT_RIGHTS_ALLOWED
                        : MessageKey.PLOT_RIGHTS_FORBIDDEN),
                Message.of(actions.contains(ProtectedAction.INTERACT) ? MessageKey.PLOT_RIGHTS_ALLOWED
                        : MessageKey.PLOT_RIGHTS_FORBIDDEN),
                Message.of(actions.contains(ProtectedAction.OPEN_CONTAINER) ? MessageKey.PLOT_RIGHTS_ALLOWED
                        : MessageKey.PLOT_RIGHTS_FORBIDDEN));
    }

    public static Message ruleName(PlotFlag flag) {
        return Message.of(switch (flag) {
            case PVP -> MessageKey.PLOT_RULE_PVP;
            case HOSTILE_MOBS -> MessageKey.PLOT_RULE_HOSTILE_MOBS;
            case EXPLOSIONS -> MessageKey.PLOT_RULE_EXPLOSIONS;
            case FIRE_SPREAD -> MessageKey.PLOT_RULE_FIRE_SPREAD;
            case OUTSIDERS_HURT_ANIMALS -> MessageKey.PLOT_RULE_OUTSIDERS_HURT_ANIMALS;
        });
    }

    public static Message stanceName(Stance stance) {
        return Message.of(switch (stance) {
            case INHERIT -> MessageKey.PLOT_STANCE_INHERIT;
            case ALLOW -> MessageKey.PLOT_STANCE_ALLOW;
            case DENY -> MessageKey.PLOT_STANCE_DENY;
        });
    }

    // ------------------------------------------------------------------ Eingaben

    /** Namen der Handlungen für Befehle. */
    public static List<String> actionNames() {
        return List.of("bauen", "benutzen", "behaelter");
    }

    public static List<String> ruleNames() {
        return List.of("pvp", "monster", "explosionen", "feuer", "tiere");
    }

    public static List<String> stanceNames() {
        return List.of("gesetz", "erlauben", "verbieten");
    }

    static Optional<ProtectedAction> parseAction(String input) {
        return Optional.ofNullable(switch (input.strip().toLowerCase(Locale.ROOT)) {
            case "bauen", "build" -> ProtectedAction.BUILD;
            case "benutzen", "interact", "tueren", "türen" -> ProtectedAction.INTERACT;
            case "behaelter", "behälter", "container", "kisten" -> ProtectedAction.OPEN_CONTAINER;
            default -> null;
        });
    }

    static Optional<PlotFlag> parseFlag(String input) {
        return Optional.ofNullable(switch (input.strip().toLowerCase(Locale.ROOT)) {
            case "pvp", "kampf" -> PlotFlag.PVP;
            case "monster", "mobs" -> PlotFlag.HOSTILE_MOBS;
            case "explosionen", "explosion" -> PlotFlag.EXPLOSIONS;
            case "feuer", "brand" -> PlotFlag.FIRE_SPREAD;
            case "tiere", "tierschutz" -> PlotFlag.OUTSIDERS_HURT_ANIMALS;
            default -> null;
        });
    }

    static Optional<Stance> parseStance(String input) {
        return Optional.ofNullable(switch (input.strip().toLowerCase(Locale.ROOT)) {
            case "gesetz", "inherit", "standard" -> Stance.INHERIT;
            case "erlauben", "allow", "ja" -> Stance.ALLOW;
            case "verbieten", "deny", "nein" -> Stance.DENY;
            default -> null;
        });
    }

    static Optional<Boolean> parseYesNo(String input) {
        return Optional.ofNullable(switch (input.strip().toLowerCase(Locale.ROOT)) {
            case "ja", "yes", "true", "an", "erlauben" -> Boolean.TRUE;
            case "nein", "no", "false", "aus", "verbieten" -> Boolean.FALSE;
            default -> null;
        });
    }
}
