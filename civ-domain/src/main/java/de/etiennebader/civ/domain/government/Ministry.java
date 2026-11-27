package de.etiennebader.civ.domain.government;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein Ministerium oder Amt: genau ein Minister, beliebig viele Angestellte, ein Etat aus dem Haushalt.
 *
 * @param key          eindeutiger Kurzname (klein geschrieben, ohne Leerzeichen)
 * @param name         Anzeigename
 * @param mandate      Zuständigkeitsbereich
 * @param selection    Wahl oder Ernennung des Ministers
 * @param minister     Minister; leer, solange unbesetzt
 * @param staff        Angestellte
 * @param ministerWage Tagesgehalt des Ministers
 * @param staffWage    Tagesgehalt je Angestelltem
 * @param etat         noch verfügbarer Etat dieser Haushaltsperiode
 */
public record Ministry(String key, String name, String mandate, Selection selection, Optional<ActorRef> minister,
                       List<ActorRef> staff, long ministerWage, long staffWage, long etat) {

    public static final int MAX_NAME = 40;
    public static final int MAX_MANDATE = 200;
    public static final int MAX_STAFF = 20;

    public Ministry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mandate, "mandate");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(minister, "minister");
        name = name.strip();
        mandate = mandate.strip();
        if (!key.equals(keyOf(key)) || key.isEmpty() || name.isEmpty() || name.length() > MAX_NAME
                || mandate.length() > MAX_MANDATE || ministerWage < 0 || staffWage < 0 || etat < 0) {
            throw new GovernmentException(ErrorCode.INVALID_GOVERNMENT_VALUE, name);
        }
        staff = List.copyOf(staff);
    }

    /** Normalisiert einen Namen zum Schlüssel. */
    public static String keyOf(String text) {
        return text.strip().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-|-$", "");
    }

    public static Ministry create(String name, String mandate, Selection selection) {
        return new Ministry(keyOf(name), name, mandate, selection, Optional.empty(), List.of(), 0, 0, 0);
    }

    public boolean employs(ActorRef actor) {
        return minister.filter(actor::equals).isPresent() || staff.contains(actor);
    }

    public Ministry withMandate(String newMandate) {
        return new Ministry(key, name, newMandate, selection, minister, staff, ministerWage, staffWage, etat);
    }

    public Ministry withSelection(Selection newSelection) {
        return new Ministry(key, name, mandate, newSelection, minister, staff, ministerWage, staffWage, etat);
    }

    public Ministry withMinister(Optional<ActorRef> newMinister) {
        return new Ministry(key, name, mandate, selection, newMinister, staff, ministerWage, staffWage, etat);
    }

    public Ministry withWages(long newMinisterWage, long newStaffWage) {
        return new Ministry(key, name, mandate, selection, minister, staff, newMinisterWage, newStaffWage, etat);
    }

    public Ministry withEtat(long newEtat) {
        return new Ministry(key, name, mandate, selection, minister, staff, ministerWage, staffWage, newEtat);
    }

    public Ministry hire(ActorRef employee) {
        if (employs(employee)) {
            throw new GovernmentException(ErrorCode.ALREADY_EMPLOYED, name);
        }
        if (staff.size() >= MAX_STAFF) {
            throw new GovernmentException(ErrorCode.INVALID_GOVERNMENT_VALUE, name);
        }
        List<ActorRef> next = new ArrayList<>(staff);
        next.add(employee);
        return new Ministry(key, name, mandate, selection, minister, next, ministerWage, staffWage, etat);
    }

    public Ministry fire(ActorRef employee) {
        if (!staff.contains(employee)) {
            throw new GovernmentException(ErrorCode.NOT_EMPLOYED, name);
        }
        List<ActorRef> next = new ArrayList<>(staff);
        next.remove(employee);
        return new Ministry(key, name, mandate, selection, minister, next, ministerWage, staffWage, etat);
    }
}
