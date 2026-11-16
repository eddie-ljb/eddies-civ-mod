package de.etiennebader.civ.application.media;

import de.etiennebader.civ.application.population.Satisfaction;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.media.Article;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Dorfbewohner in Redaktionen schreiben aus Vorlagen, etwa an jedem dritten Tag einen Artikel: über einen Kandidaten
 * einer laufenden Wahl in ihrer Gemeinde (Haltung nach seinem Programm und ihrer Persönlichkeit) oder sonst über die
 * Stimmung in der Gemeinde (Haltung nach ihrer eigenen Zufriedenheit).
 */
public final class VillagerEditors {

    private final Supplier<WorldQueries> queries;
    private final PressUseCases press;
    private final Function<ActorRef, String> nameOf;

    public VillagerEditors(Supplier<WorldQueries> queries, PressUseCases press, Function<ActorRef, String> nameOf) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.press = Objects.requireNonNull(press, "press");
        this.nameOf = Objects.requireNonNull(nameOf, "nameOf");
    }

    /** Tagesende: neue Artikel der Dorfbewohner-Redakteure. */
    public List<Article> execute(Day day) {
        WorldQueries world = queries.get();
        List<Article> written = new ArrayList<>();
        for (PolityView paper : world.newspapers()) {
            List<PersonRef> editors = paper.members().stream().map(Membership::member)
                    .filter(PersonRef.class::isInstance).map(PersonRef.class::cast)
                    .sorted(Comparator.comparing(PersonRef::uuid)).toList();
            for (PersonRef editor : editors) {
                if (Math.floorMod(editor.uuid().hashCode() + day.value(), 3) != 0) {
                    continue;
                }
                Optional<PolityView> home = world.municipalityOf(editor);
                if (home.isEmpty()) {
                    continue;
                }
                try {
                    written.add(write(paper, editor, home.get(), world, day));
                } catch (DomainException e) {
                    break;
                }
            }
        }
        return written;
    }

    private Article write(PolityView paper, PersonRef editor, PolityView town, WorldQueries world, Day day) {
        Personality personality = Personality.of(editor);
        int variant = Math.floorMod(editor.uuid().hashCode() * 31 + (int) day.value(), 2);
        Optional<Election> election = town.elections().stream().filter(found -> !found.candidates().isEmpty())
                .findFirst();
        String townName = town.name().value();
        if (election.isPresent()) {
            List<ActorRef> candidates = List.copyOf(election.get().candidates());
            ActorRef candidate = candidates.get(Math.floorMod(editor.uuid().hashCode() + (int) day.value(),
                    candidates.size()));
            Platform platform = election.get().platformOf(candidate);
            double agreement = agreement(personality, platform);
            int tendency = agreement >= 0.5 ? 2 : agreement >= 0.15 ? 1 : agreement <= -0.5 ? -2
                    : agreement <= -0.15 ? -1 : 0;
            String name = nameOf.apply(candidate);
            String topic = PressUseCases.topicName(platform);
            String title;
            String body;
            if (tendency > 0) {
                title = variant == 0 ? name + " überzeugt beim Thema " + topic : "Warum " + name + " die richtige Wahl ist";
                body = name + " tritt in " + townName + " mit dem Thema " + topic + " an. Viele sehen darin genau das, "
                        + "was die Gemeinde jetzt braucht. Unsere Redaktion meint: " + name + " ist verlässlich und "
                        + "kompetent.";
            } else if (tendency < 0) {
                title = variant == 0 ? "Zweifel an " + name : name + " und das Problem mit " + topic;
                body = name + " wirbt in " + townName + " mit dem Thema " + topic + ". Doch die Kritik wächst: Viele "
                        + "halten das für einen Fehler. Ob " + name + " das Amt verdient, bleibt fraglich.";
            } else {
                title = "Wahl in " + townName + ": " + name + " tritt an";
                body = name + " kandidiert in " + townName + " und setzt auf " + topic + ". Die Wahl endet an Tag "
                        + election.get().closesOn().value() + ".";
            }
            return press.publish(editor, paper.id(), title, body,
                    new ArticleAnalysis(Optional.of(candidate), tendency, platform, 1, tendency), day);
        }
        int score = Satisfaction.of(personality, town, world.effectiveLaws(town)).score();
        int tendency = score >= 30 ? 2 : score >= 10 ? 1 : score <= -30 ? -2 : score <= -10 ? -1 : 0;
        Platform topic = preferred(personality);
        String title;
        String body;
        if (tendency > 0) {
            title = townName + ": Die Stimmung ist gut";
            body = "In " + townName + " läuft es. Die Menschen sind zufrieden, die Gemeinde ist ein Vorbild. Großes Lob "
                    + "an die Führung, vor allem beim Thema " + PressUseCases.topicName(topic) + ".";
        } else if (tendency < 0) {
            title = townName + ": Unmut wächst";
            body = "In " + townName + " häufen sich die Beschwerden. Viele sind unzufrieden mit der Führung, gerade beim "
                    + "Thema " + PressUseCases.topicName(topic) + "; von Fehlern ist die Rede.";
        } else {
            title = "Aus " + townName;
            body = "Ein ruhiger Tag in " + townName + ". Nichts Besonderes zu berichten.";
        }
        return press.publish(editor, paper.id(), title, body,
                new ArticleAnalysis(Optional.of(town.asActor()), tendency, topic, 1, tendency), day);
    }

    static double agreement(Personality personality, Platform topic) {
        return switch (topic) {
            case NONE -> 0;
            case ORDER -> personality.order() / 100.0;
            case FREEDOM -> -personality.order() / 100.0;
            case LOW_TAXES -> personality.thrift() / 100.0;
            case INVESTMENT -> -personality.thrift() / 100.0;
        };
    }

    /** Das Thema, das einer Persönlichkeit am meisten zusagt. */
    static Platform preferred(Personality personality) {
        if (Math.abs(personality.order()) >= Math.abs(personality.thrift())) {
            return personality.order() >= 0 ? Platform.ORDER : Platform.FREEDOM;
        }
        return personality.thrift() >= 0 ? Platform.LOW_TAXES : Platform.INVESTMENT;
    }
}
