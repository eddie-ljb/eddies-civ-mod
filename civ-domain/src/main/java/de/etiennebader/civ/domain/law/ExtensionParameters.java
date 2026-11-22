package de.etiennebader.civ.domain.law;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Verzeichnis der Gesetzesparameter, die fremde Mods beisteuern (Third-Party-API, #51).
 *
 * <p>Fremde Parameter haben eine ID mit Namensraum {@code modid.name} (Kleinbuchstaben, Ziffern und
 * Unterstriche), damit sie nicht mit den {@link StandardParameters} oder mit anderen Mods kollidieren.
 * Ein Punkt statt Doppelpunkt, weil die ID auch als Chat-Befehlsargument ({@code /civ recht setzen}) dient.
 *
 * <p>Registriert wird beim Start des Mods, bevor ein Server entsteht; danach ist das Verzeichnis
 * {@linkplain #freeze() eingefroren}, damit alle Kataloge einer Spielsitzung gleich aussehen.
 * Doppelte Registrierung desselben Schlüssels ist ein Fehler, wiederholte Registrierung mit gleichem
 * Schlüssel (gleiche ID, Typ, Standardwert) ist erlaubt und liefert den ersten Schlüssel zurück.
 */
public final class ExtensionParameters {

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*");

    private static final Map<String, ParameterKey<?>> REGISTERED = new LinkedHashMap<>();
    private static boolean frozen;

    private ExtensionParameters() {
    }

    /**
     * Meldet einen Parameter an.
     *
     * @return der geltende Schlüssel dieser ID (bei erneuter, identischer Anmeldung der bereits bekannte)
     * @throws IllegalArgumentException wenn die ID keinen Namensraum hat, einen Standardparameter überdeckt
     *                                  oder mit anderem Typ/Standardwert schon vergeben ist
     * @throws IllegalStateException    nach {@link #freeze()}
     */
    @SuppressWarnings("unchecked")
    public static synchronized <T> ParameterKey<T> register(ParameterKey<T> key) {
        Objects.requireNonNull(key, "key");
        if (!ID.matcher(key.id()).matches()) {
            throw new IllegalArgumentException(
                    "ID '%s' ist ungültig: erwartet wird modid.name (a-z, 0-9, _)".formatted(key.id()));
        }
        if (StandardParameters.all().contains(key)) {
            throw new IllegalArgumentException("ID kollidiert mit einem Standardparameter: " + key.id());
        }
        ParameterKey<?> known = REGISTERED.get(key.id());
        if (known != null) {
            if (known.type() != key.type() || !known.defaultValue().equals(key.defaultValue())
                    || !known.options().equals(key.options())) {
                throw new IllegalArgumentException("ID ist schon anders vergeben: " + key.id());
            }
            return (ParameterKey<T>) known;
        }
        if (frozen) {
            throw new IllegalStateException(
                    "Gesetzesparameter müssen beim Mod-Start angemeldet werden, nicht danach: " + key.id());
        }
        REGISTERED.put(key.id(), key);
        return key;
    }

    /** Alle angemeldeten Parameter in der Reihenfolge der Anmeldung. */
    public static synchronized List<ParameterKey<?>> all() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTERED.values()));
    }

    /** Beendet die Anmeldephase; wird aufgerufen, sobald ein Server startet. */
    public static synchronized void freeze() {
        frozen = true;
    }

    /** Nur für Tests: leert das Verzeichnis und öffnet die Anmeldephase wieder. */
    static synchronized void resetForTests() {
        REGISTERED.clear();
        frozen = false;
    }
}
