package de.etiennebader.civ.domain.contract;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.land.PlotId;
import de.etiennebader.civ.domain.shared.ContractId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.Money;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.Objects;
import java.util.Optional;

/**
 * Ein Vertrag zwischen zwei Parteien.
 *
 * <p>Wer zahlt, ist {@link #payer()}; wer bekommt, {@link #payee()}. Bei Miete zahlt der Mieter dem Inhaber des
 * Grundstücks, bei Arbeit der Arbeitgeber dem Arbeitnehmer.
 *
 * @param number     fortlaufende Nummer zum Anzeigen, z. B. „Vertrag 12“
 * @param offeror    wer den Vertrag angeboten hat (unterschreibt mit dem Angebot)
 * @param counterparty wer annehmen muss
 * @param perDay     tägliche Zahlung; null bei Bündnissen und freien Verträgen ohne laufende Zahlung
 * @param once       einmalige Zahlung bei Unterschrift (nicht erstattet)
 * @param deposit    Kaution bei Unterschrift; zurück bei regulärem Ende, verfällt bei Vertragsbruch
 * @param plot       das gemietete Grundstück (nur Miete)
 * @param termDays   Laufzeit in Tagen; 0 heißt unbefristet
 * @param endsOn     letzter Tag: aus Laufzeit oder Kündigung
 */
public record Contract(ContractId id, int number, ContractKind kind, ActorRef offeror, ActorRef counterparty,
                       ActorRef payer, ActorRef payee, Money perDay, Money once, Money deposit,
                       Optional<PlotRef> plot, String title, String text, long termDays, Day offeredOn,
                       Optional<Day> startedOn, Optional<Day> endsOn, ContractStatus status,
                       Optional<EndReason> endReason) {

    public static final int MAX_TITLE = 48;
    public static final int MAX_TEXT = 1000;

    /** Ein Grundstück einer Gemeinde. */
    public record PlotRef(PolityId polity, PlotId plot) {

        public PlotRef {
            Objects.requireNonNull(polity, "polity");
            Objects.requireNonNull(plot, "plot");
        }
    }

    public Contract {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(offeror, "offeror");
        Objects.requireNonNull(counterparty, "counterparty");
        Objects.requireNonNull(payer, "payer");
        Objects.requireNonNull(payee, "payee");
        Objects.requireNonNull(perDay, "perDay");
        Objects.requireNonNull(once, "once");
        Objects.requireNonNull(deposit, "deposit");
        Objects.requireNonNull(plot, "plot");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(offeredOn, "offeredOn");
        Objects.requireNonNull(startedOn, "startedOn");
        Objects.requireNonNull(endsOn, "endsOn");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(endReason, "endReason");
        title = title.strip();
        text = text.strip();
        if (title.isEmpty() || title.length() > MAX_TITLE) {
            throw new IllegalArgumentException("Vertragstitel muss 1 bis " + MAX_TITLE + " Zeichen haben");
        }
        if (text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("Vertragstext zu lang");
        }
        if (offeror.equals(counterparty)) {
            throw new IllegalArgumentException("Vertrag mit sich selbst");
        }
        if (perDay.isNegative() || once.isNegative() || deposit.isNegative() || termDays < 0) {
            throw new IllegalArgumentException("Negative Beträge oder Laufzeit");
        }
        if ((kind == ContractKind.LEASE) != plot.isPresent()) {
            throw new IllegalArgumentException("Nur Mietverträge haben ein Grundstück");
        }
        if (!(payer.equals(offeror) && payee.equals(counterparty) || payer.equals(counterparty) && payee.equals(offeror))) {
            throw new IllegalArgumentException("Zahler und Empfänger müssen die beiden Parteien sein");
        }
    }

    /** Ob der Akteur Partei des Vertrags ist. */
    public boolean involves(ActorRef actor) {
        return offeror.equals(actor) || counterparty.equals(actor);
    }

    /** Die andere Partei. */
    public ActorRef otherParty(ActorRef actor) {
        return offeror.equals(actor) ? counterparty : offeror;
    }

    public boolean active() {
        return status == ContractStatus.ACTIVE;
    }

    Contract signed(Day day) {
        Optional<Day> end = termDays > 0 ? Optional.of(day.plus(termDays)) : Optional.empty();
        return new Contract(id, number, kind, offeror, counterparty, payer, payee, perDay, once, deposit, plot, title,
                text, termDays, offeredOn, Optional.of(day), end, ContractStatus.ACTIVE, Optional.empty());
    }

    Contract withNotice(Day lastDay) {
        Day end = endsOn.filter(current -> current.isBefore(lastDay)).orElse(lastDay);
        return new Contract(id, number, kind, offeror, counterparty, payer, payee, perDay, once, deposit, plot, title,
                text, termDays, offeredOn, startedOn, Optional.of(end), status, Optional.empty());
    }

    Contract ended(EndReason reason) {
        return new Contract(id, number, kind, offeror, counterparty, payer, payee, perDay, once, deposit, plot, title,
                text, termDays, offeredOn, startedOn, endsOn, ContractStatus.ENDED, Optional.of(reason));
    }

    Contract declined() {
        return new Contract(id, number, kind, offeror, counterparty, payer, payee, perDay, once, deposit, plot, title,
                text, termDays, offeredOn, startedOn, endsOn, ContractStatus.DECLINED, Optional.empty());
    }
}
