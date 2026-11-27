package de.etiennebader.civ.domain.government;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ErrorCode;

import java.util.Objects;
import java.util.Optional;

/**
 * Eine Förderung: täglicher Betrag aus dem Etat eines Ministeriums an alle Bürger oder an einen Empfänger
 * (Person, Firma, Gemeinde), zeitlich begrenzt und optional nach Vermögen gestaffelt.
 *
 * @param key        Kurzname
 * @param name       Anzeigename
 * @param recipient  Empfänger; leer = alle Bürger der Ebene
 * @param ministry   zahlendes Ministerium (Schlüssel)
 * @param amount     voller Tagesbetrag je Empfänger
 * @param until      letzter Tag; leer = unbefristet
 * @param fullUpTo   bis zu diesem Vermögen gibt es den vollen Betrag; 0 = keine Bedürftigkeitsprüfung
 * @param taperUntil ab {@code fullUpTo} sinkt der Betrag linear bis 0 bei diesem Vermögen
 * @param active     ob beschlossen; Förderungen bei sitzendem Parlament brauchen dessen Mehrheit
 */
public record Subsidy(String key, String name, Optional<ActorRef> recipient, String ministry, long amount,
                      Optional<Day> until, long fullUpTo, long taperUntil, boolean active) {

    public Subsidy {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(ministry, "ministry");
        Objects.requireNonNull(until, "until");
        name = name.strip();
        if (key.isEmpty() || name.isEmpty() || name.length() > Ministry.MAX_NAME || amount <= 0 || fullUpTo < 0
                || taperUntil < 0) {
            throw new GovernmentException(ErrorCode.INVALID_GOVERNMENT_VALUE, name);
        }
    }

    public static Subsidy create(String name, Optional<ActorRef> recipient, String ministry, long amount,
                                 Optional<Day> until, long fullUpTo, long taperUntil, boolean active) {
        return new Subsidy(Ministry.keyOf(name), name, recipient, ministry, amount, until, fullUpTo, taperUntil, active);
    }

    public boolean forAllCitizens() {
        return recipient.isEmpty();
    }

    public Subsidy activated() {
        return new Subsidy(key, name, recipient, ministry, amount, until, fullUpTo, taperUntil, true);
    }

    /** Ob die Förderung am Tag noch läuft. */
    public boolean runsOn(Day day) {
        return active && until.map(last -> !day.isAfter(last)).orElse(true);
    }

    /** Tagesbetrag für jemanden mit diesem Vermögen. */
    public long amountFor(long wealth) {
        if (fullUpTo <= 0 || wealth <= fullUpTo) {
            return amount;
        }
        if (taperUntil <= fullUpTo || wealth >= taperUntil) {
            return 0;
        }
        return amount * (taperUntil - wealth) / (taperUntil - fullUpTo);
    }
}
