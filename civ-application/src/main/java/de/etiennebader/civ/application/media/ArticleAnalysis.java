package de.etiennebader.civ.application.media;

import de.etiennebader.civ.application.population.Campaign;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.media.Article;
import de.etiennebader.civ.domain.polity.Platform;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest aus einem Artikel heraus, worüber er berichtet und wie.
 *
 * <ul>
 *   <li><b>Gegenstand:</b> der bekannte Name (Spieler, Partei, Gemeinde, Firma), der am häufigsten vorkommt.</li>
 *   <li><b>Tendenz:</b> lobende minus kritische Wörter, auf -2 bis 2 begrenzt; ohne Gegenstand 0.</li>
 *   <li><b>Thema (Framing):</b> wie bei Reden aus Stichworten, z. B. „Sicherheit“ → Ordnung.</li>
 * </ul>
 *
 * @param mentions wie oft der Gegenstand genannt wird
 * @param score    lobende minus kritische Wörter
 */
public record ArticleAnalysis(Optional<ActorRef> subject, int tendency, Platform topic, int mentions, int score) {

    private static final List<String> POSITIVE = List.of("gut", "stark", "erfolg", "lob", "überzeug", "ueberzeug",
            "verlässlich", "verlaesslich", "kompetent", "mutig", "ehrlich", "fleißig", "fleissig", "klug", "sicher",
            "gelungen", "großartig", "grossartig", "vorbild", "hervorragend", "zufrieden", "versprechen gehalten",
            "gewinnt", "beliebt", "hilft", "good", "great", "strong", "honest", "success", "praise");
    private static final List<String> NEGATIVE = List.of("schlecht", "schwach", "versag", "skandal", "korrupt",
            "lüge", "luege", "lügt", "luegt", "gebrochen", "chaos", "teuer", "unfähig", "unfaehig", "kritik",
            "scheiter", "gescheitert", "verschwend", "unzufrieden", "betrug", "fehler", "zweifel", "vorwurf",
            "verliert", "unbeliebt", "bad", "weak", "scandal", "corrupt", "lie", "fail");

    public ArticleAnalysis {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(topic, "topic");
    }

    /**
     * @param names bekannte Namen und wofür sie stehen; Groß- und Kleinschreibung zählt nicht
     */
    public static ArticleAnalysis of(String title, String body, Map<String, ActorRef> names) {
        String text = (title + "\n" + body).toLowerCase(Locale.ROOT);
        ActorRef best = null;
        int bestCount = 0;
        for (Map.Entry<String, ActorRef> entry : names.entrySet()) {
            String name = entry.getKey().strip().toLowerCase(Locale.ROOT);
            if (name.length() < 3) {
                continue;
            }
            int count = count(text, Pattern.compile("(?<![\\p{L}\\d_])" + Pattern.quote(name) + "(?![\\p{L}\\d_])"));
            if (title.toLowerCase(Locale.ROOT).contains(name)) {
                count++;
            }
            if (count > bestCount) {
                best = entry.getValue();
                bestCount = count;
            }
        }
        int score = words(text, POSITIVE) - words(text, NEGATIVE);
        int tendency = best == null ? 0 : score >= 3 ? 2 : score >= 1 ? 1 : score <= -3 ? -2 : score <= -1 ? -1 : 0;
        return new ArticleAnalysis(Optional.ofNullable(best), tendency, Campaign.topicOf(text), bestCount, score);
    }

    private static int words(String text, List<String> stems) {
        int sum = 0;
        for (String stem : stems) {
            sum += count(text, Pattern.compile("(?<![\\p{L}])" + Pattern.quote(stem)));
        }
        return sum;
    }

    private static int count(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /** Für Anzeigen: „scharf kritisch“ bis „lobend“. */
    public static String tendencyWord(int tendency) {
        return switch (Math.max(Article.MIN_TENDENCY, Math.min(Article.MAX_TENDENCY, tendency))) {
            case -2 -> "scharf kritisch";
            case -1 -> "kritisch";
            case 1 -> "wohlwollend";
            case 2 -> "lobend";
            default -> "neutral";
        };
    }
}
