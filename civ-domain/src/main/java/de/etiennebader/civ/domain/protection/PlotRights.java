package de.etiennebader.civ.domain.protection;

import de.etiennebader.civ.domain.actor.ActorRef;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Was auf einem Grundstück erlaubt ist, so wie der Inhaber es einstellt. Unveränderlich.
 *
 * <ul>
 *   <li><b>Mitglieder</b> der Gemeinde, auf deren Gebiet das Grundstück liegt, und <b>Fremde</b> haben je eine Menge
 *       erlaubter Handlungen ({@link ProtectedAction}: Bauen, Benutzen, Behälter öffnen).</li>
 *   <li><b>Gäste</b> sind einzelne Spieler oder Dorfbewohner mit eigener Menge erlaubter Handlungen.</li>
 *   <li><b>Regeln</b> ({@link PlotFlag}) wie PvP, Monster, Explosionen, Feuer und Tierschutz kann der Inhaber abweichend
 *       vom Gesetz einstellen ({@link Stance}).</li>
 * </ul>
 *
 * <p>Standard ({@link #DEFAULT}): Mitglieder dürfen Türen und Knöpfe benutzen, Fremde nichts; alle Regeln gelten wie
 * im Gesetz. Der Inhaber selbst darf immer alles.
 */
public final class PlotRights {

    /** Der Standard: wie es sich ohne Einstellungen verhält. */
    public static final PlotRights DEFAULT = new PlotRights(EnumSet.of(ProtectedAction.INTERACT),
            EnumSet.noneOf(ProtectedAction.class), Map.of(), Map.of());

    /** Höchstzahl der Gäste je Grundstück. */
    public static final int MAX_GUESTS = 32;

    private final Set<ProtectedAction> members;
    private final Set<ProtectedAction> outsiders;
    private final Map<ActorRef, Set<ProtectedAction>> guests;
    private final Map<PlotFlag, Stance> flags;

    public PlotRights(Set<ProtectedAction> members, Set<ProtectedAction> outsiders,
                      Map<ActorRef, Set<ProtectedAction>> guests, Map<PlotFlag, Stance> flags) {
        this.members = copy(members);
        this.outsiders = copy(outsiders);
        Map<ActorRef, Set<ProtectedAction>> guestCopy = new LinkedHashMap<>();
        Objects.requireNonNull(guests, "guests").forEach((actor, actions) -> {
            if (!actions.isEmpty()) {
                guestCopy.put(Objects.requireNonNull(actor, "guest"), copy(actions));
            }
        });
        if (guestCopy.size() > MAX_GUESTS) {
            throw new IllegalArgumentException("Höchstens " + MAX_GUESTS + " Gäste");
        }
        this.guests = Collections.unmodifiableMap(guestCopy);
        EnumMap<PlotFlag, Stance> flagCopy = new EnumMap<>(PlotFlag.class);
        Objects.requireNonNull(flags, "flags").forEach((flag, stance) -> {
            if (stance != Stance.INHERIT) {
                flagCopy.put(flag, stance);
            }
        });
        this.flags = Collections.unmodifiableMap(flagCopy);
    }

    private static Set<ProtectedAction> copy(Set<ProtectedAction> actions) {
        Set<ProtectedAction> copy = EnumSet.noneOf(ProtectedAction.class);
        copy.addAll(Objects.requireNonNull(actions, "actions"));
        return Collections.unmodifiableSet(copy);
    }

    /** Ob diese Einstellung dem Standard entspricht und deshalb nicht gespeichert werden muss. */
    public boolean isDefault() {
        return equals(DEFAULT);
    }

    /** Erlaubte Handlungen der Mitglieder der Gemeinde. */
    public Set<ProtectedAction> members() {
        return members;
    }

    /** Erlaubte Handlungen aller anderen. */
    public Set<ProtectedAction> outsiders() {
        return outsiders;
    }

    public Map<ActorRef, Set<ProtectedAction>> guests() {
        return guests;
    }

    /** Die Haltung zu einer Regel; ohne Einstellung {@link Stance#INHERIT}. */
    public Stance stance(PlotFlag flag) {
        return flags.getOrDefault(flag, Stance.INHERIT);
    }

    public Map<PlotFlag, Stance> flags() {
        return flags;
    }

    /**
     * Ob der Akteur die Handlung auf dem Grundstück ausführen darf, ohne Inhaber zu sein.
     *
     * @param member ob er der Gemeinde angehört, auf deren Gebiet das Grundstück liegt
     */
    public boolean permits(ActorRef actor, boolean member, ProtectedAction action) {
        Set<ProtectedAction> guest = guests.get(actor);
        if (guest != null && guest.contains(action)) {
            return true;
        }
        return (member ? members : outsiders).contains(action);
    }

    public PlotRights withMembers(ProtectedAction action, boolean allowed) {
        return new PlotRights(toggle(members, action, allowed), outsiders, guests, flags);
    }

    public PlotRights withOutsiders(ProtectedAction action, boolean allowed) {
        return new PlotRights(members, toggle(outsiders, action, allowed), guests, flags);
    }

    /** Gibt einem Gast die Handlung oder nimmt sie zurück. */
    public PlotRights withGuest(ActorRef guest, ProtectedAction action, boolean allowed) {
        Map<ActorRef, Set<ProtectedAction>> updated = new LinkedHashMap<>(guests);
        updated.put(guest, toggle(guests.getOrDefault(guest, Set.of()), action, allowed));
        return new PlotRights(members, outsiders, updated, flags);
    }

    /** Entfernt einen Gast samt allen seinen Rechten. */
    public PlotRights withoutGuest(ActorRef guest) {
        Map<ActorRef, Set<ProtectedAction>> updated = new LinkedHashMap<>(guests);
        updated.remove(guest);
        return new PlotRights(members, outsiders, updated, flags);
    }

    public PlotRights withStance(PlotFlag flag, Stance stance) {
        Map<PlotFlag, Stance> updated = new EnumMap<>(PlotFlag.class);
        updated.putAll(flags);
        updated.put(flag, Objects.requireNonNull(stance, "stance"));
        return new PlotRights(members, outsiders, guests, updated);
    }

    private static Set<ProtectedAction> toggle(Set<ProtectedAction> actions, ProtectedAction action, boolean allowed) {
        Set<ProtectedAction> updated = EnumSet.noneOf(ProtectedAction.class);
        updated.addAll(actions);
        if (allowed) {
            updated.add(action);
        } else {
            updated.remove(action);
        }
        return updated;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PlotRights rights && members.equals(rights.members)
                && outsiders.equals(rights.outsiders) && guests.equals(rights.guests) && flags.equals(rights.flags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(members, outsiders, guests, flags);
    }

    @Override
    public String toString() {
        return "PlotRights[members=" + members + ", outsiders=" + outsiders + ", guests=" + guests.size()
                + ", flags=" + flags + "]";
    }
}
