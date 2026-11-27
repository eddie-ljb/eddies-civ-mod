package de.etiennebader.civ.domain.civic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Hymne einer Polity: Titel, Instrument und Noten in einfacher Schreibweise.
 *
 * <p>Noten: {@code C4 D4 E4 F#4 G4/2 - A4*2}: Ton mit Oktave (Notenblock-Bereich F#3 bis F#5), {@code #} oder
 * {@code b} für Halbtöne, {@code -} für eine Pause; {@code *2} verlängert, {@code /2} verkürzt. Ein Schlag dauert vier
 * Ticks (Fünftelsekunde).
 *
 * <p>Optional hängt an der Hymne eine hochgeladene Aufnahme ({@code audio}: SHA-256 der OGG-Datei, leer ohne). Die
 * Noten bleiben als Ersatz für Spieler ohne Ressourcenpaket bestehen.
 */
public record Anthem(String title, String instrument, String notes, String audio) {

    public static final int MAX_TITLE = 48;
    public static final int MAX_NOTES = 400;
    public static final int TICKS_PER_BEAT = 4;
    /** Größte erlaubte Aufnahme (OGG) in Bytes. */
    public static final int MAX_AUDIO_BYTES = 4 * 1024 * 1024;
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");
    public static final List<String> INSTRUMENTS = List.of("harp", "bell", "flute", "chime", "guitar", "xylophone",
            "iron_xylophone", "pling", "banjo", "bit", "didgeridoo", "cow_bell", "bass");

    private static final Pattern TOKEN = Pattern.compile("([A-Ga-g])([#b]?)(\\d)((?:[*/]\\d+)?)|-((?:[*/]\\d+)?)");
    private static final List<String> NAMES = List.of("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B");

    /** Ein Ton oder eine Pause. @param semitones 0 = F#3 … 24 = F#5; -1 für eine Pause */
    public record Note(int semitones, int ticks) {

        public boolean rest() {
            return semitones < 0;
        }

        /** Tonhöhe für den Notenblock-Klang (0,5 bis 2). */
        public float pitch() {
            return (float) Math.pow(2, (semitones - 12) / 12.0);
        }
    }

    public Anthem(String title, String instrument, String notes) {
        this(title, instrument, notes, "");
    }

    public Anthem {
        audio = audio == null ? "" : audio.strip().toLowerCase(Locale.ROOT);
        if (!audio.isEmpty() && !HASH.matcher(audio).matches()) {
            throw new IllegalArgumentException("Ungültige Aufnahme-Kennung");
        }
        title = Objects.requireNonNull(title, "title").strip();
        instrument = Objects.requireNonNull(instrument, "instrument").strip().toLowerCase(Locale.ROOT);
        notes = Objects.requireNonNull(notes, "notes").strip();
        if (title.isEmpty() || title.length() > MAX_TITLE) {
            throw new IllegalArgumentException("Titel leer oder zu lang");
        }
        if (!INSTRUMENTS.contains(instrument)) {
            throw new IllegalArgumentException("Unbekanntes Instrument: " + instrument);
        }
        if (notes.length() > MAX_NOTES) {
            throw new IllegalArgumentException("Zu viele Noten");
        }
        if (parse(notes).isEmpty()) {
            throw new IllegalArgumentException("Keine lesbaren Noten: " + notes);
        }
    }

    public boolean hasAudio() {
        return !audio.isEmpty();
    }

    /** Dieselbe Hymne mit anderer Aufnahme ({@code ""} entfernt sie). */
    public Anthem withAudio(String hash) {
        return new Anthem(title, instrument, notes, hash);
    }

    /** Name des Klangs im Ressourcenpaket; stabil je Aufnahme. */
    public String soundName() {
        return "anthem." + audio.substring(0, Math.min(16, audio.length()));
    }

    /** Die Noten in Spielreihenfolge. */
    public List<Note> melody() {
        return parse(notes);
    }

    /** Liest Noten; unlesbare oder außerhalb des Notenblock-Bereichs werfen eine Ausnahme. */
    public static List<Note> parse(String text) {
        List<Note> melody = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            Matcher matcher = TOKEN.matcher(token);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Unlesbare Note: " + token);
            }
            if (matcher.group(1) == null) {
                melody.add(new Note(-1, ticks(matcher.group(5))));
                continue;
            }
            String name = matcher.group(1).toUpperCase(Locale.ROOT);
            int index = NAMES.indexOf(name);
            if ("#".equals(matcher.group(2))) {
                index++;
            } else if ("b".equals(matcher.group(2))) {
                index--;
            }
            int octave = Integer.parseInt(matcher.group(3));
            int midi = (octave + 1) * 12 + index;
            int semitones = midi - 54;
            if (semitones < 0 || semitones > 24) {
                throw new IllegalArgumentException("Außerhalb F#3–F#5: " + token);
            }
            melody.add(new Note(semitones, ticks(matcher.group(4))));
        }
        return melody;
    }

    private static int ticks(String modifier) {
        if (modifier == null || modifier.isEmpty()) {
            return TICKS_PER_BEAT;
        }
        int factor = Integer.parseInt(modifier.substring(1));
        if (factor < 1 || factor > 8) {
            throw new IllegalArgumentException("Dauer außerhalb 1–8: " + modifier);
        }
        return modifier.charAt(0) == '*' ? TICKS_PER_BEAT * factor : Math.max(1, TICKS_PER_BEAT / factor);
    }

    /** Dauer in Ticks. */
    public int ticks() {
        return melody().stream().mapToInt(Note::ticks).sum();
    }
}
