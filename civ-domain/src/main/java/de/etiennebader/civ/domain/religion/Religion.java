package de.etiennebader.civ.domain.religion;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Eine Religion: eigen gegründet, mit Schrift, Gotteshaus, Sprache und Konfessionen, einem Klerus-Oberhaupt mit
 * Machtgrad, und optional einer laufenden Klerus-Wahl.
 *
 * <p>Bewusst keine Polity: Religionen haben kein Gebiet, keine Kasse und kein Recht, und mehrere Mitglieder
 * verschiedener Gemeinden/Nationen sollen gleichzeitig verschiedenen Religionen angehören können, unabhängig von
 * ihrer politischen Zugehörigkeit. Eine volle Integration in das Polity-Aggregat (eigene {@code PolityKind}, eigenes
 * Amt über die bestehende Wahl-Maschinerie) wäre ein großer, eigener Umbau; dieses Modell ist bewusst leichtgewichtig
 * gehalten, siehe {@code docs/issue-13-religion.md} für die Abwägung und was bewusst nicht umgesetzt ist.
 *
 * @param clergyHead  wer aktuell das Klerus-Oberhaupt ist; leer, wenn das Amt vakant ist
 * @param clergyPower wie viel politischer Einfluss dem Klerus-Oberhaupt zugestanden wird (0–100)
 * @param election    eine laufende Wahl um das Klerusamt, falls eine ausgerufen ist
 */
public record Religion(ReligionId id, String name, ActorRef founder, boolean monotheistic, String scriptureName,
                       String templeName, String language, List<Denomination> denominations,
                       Optional<ActorRef> clergyHead, int clergyPower, Optional<ClergyElection> election,
                       Day foundedOn) {

    public static final int MAX_NAME = 48;
    public static final int MAX_FIELD = 48;
    public static final int MAX_DENOMINATIONS = 12;
    public static final int MAX_POWER = 100;

    public Religion {
        name = requireField(name, "Name der Religion", MAX_NAME);
        Objects.requireNonNull(founder, "founder");
        scriptureName = requireField(scriptureName, "Name der heiligen Schrift", MAX_FIELD);
        templeName = requireField(templeName, "Name des Gotteshauses", MAX_FIELD);
        language = requireField(language, "Sprache", MAX_FIELD);
        denominations = List.copyOf(denominations);
        if (denominations.size() > MAX_DENOMINATIONS) {
            throw new IllegalArgumentException("Höchstens " + MAX_DENOMINATIONS + " Konfessionen");
        }
        Objects.requireNonNull(clergyHead, "clergyHead");
        if (clergyPower < 0 || clergyPower > MAX_POWER) {
            throw new IllegalArgumentException("clergyPower muss zwischen 0 und " + MAX_POWER + " liegen: " + clergyPower);
        }
        Objects.requireNonNull(election, "election");
        Objects.requireNonNull(foundedOn, "foundedOn");
    }

    /** Gründet eine Religion; der Gründer wird zunächst auch Klerus-Oberhaupt mit mittlerem Machtgrad. */
    public static Religion found(ReligionId id, String name, ActorRef founder, boolean monotheistic,
                                 String scriptureName, String templeName, String language, Day foundedOn) {
        return new Religion(id, name, founder, monotheistic, scriptureName, templeName, language, List.of(),
                Optional.of(founder), MAX_POWER / 2, Optional.empty(), foundedOn);
    }

    /** @throws IllegalArgumentException wenn der Name schon vergeben oder {@link #MAX_DENOMINATIONS} erreicht ist */
    Religion withDenomination(Denomination denomination) {
        Objects.requireNonNull(denomination, "denomination");
        if (denominations.stream().anyMatch(existing -> existing.name().equalsIgnoreCase(denomination.name()))) {
            throw new IllegalArgumentException("Konfession gibt es schon: " + denomination.name());
        }
        if (denominations.size() >= MAX_DENOMINATIONS) {
            throw new IllegalStateException("Höchstens " + MAX_DENOMINATIONS + " Konfessionen");
        }
        List<Denomination> updated = new ArrayList<>(denominations);
        updated.add(denomination);
        return new Religion(id, name, founder, monotheistic, scriptureName, templeName, language, updated,
                clergyHead, clergyPower, election, foundedOn);
    }

    Religion withTexts(String scripture, String temple, String lang) {
        return new Religion(id, name, founder, monotheistic, scripture, temple, lang, denominations,
                clergyHead, clergyPower, election, foundedOn);
    }

    Religion withClergy(Optional<ActorRef> head, int power) {
        return new Religion(id, name, founder, monotheistic, scriptureName, templeName, language, denominations,
                head, power, election, foundedOn);
    }

    Religion withElection(Optional<ClergyElection> updatedElection) {
        return new Religion(id, name, founder, monotheistic, scriptureName, templeName, language, denominations,
                clergyHead, clergyPower, updatedElection, foundedOn);
    }

    private static String requireField(String value, String label, int max) {
        String stripped = Objects.requireNonNull(value, label).strip();
        if (stripped.isEmpty() || stripped.length() > max) {
            throw new IllegalArgumentException(label + " leer oder zu lang: " + stripped);
        }
        return stripped;
    }
}
