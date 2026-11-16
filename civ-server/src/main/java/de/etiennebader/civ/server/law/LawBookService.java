package de.etiennebader.civ.server.law;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.law.EnactParagraph;
import de.etiennebader.civ.application.law.RepealParagraph;
import de.etiennebader.civ.application.polity.ProposeMotion;
import de.etiennebader.civ.application.polity.VoteOutcome;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.law.LawRecognizer;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Caller;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;
import de.etiennebader.civ.server.command.Reply;
import de.etiennebader.civ.server.command.Texts;
import de.etiennebader.civ.server.command.TranslationKeys;
import de.etiennebader.civ.server.federation.FederationService;
import de.etiennebader.civ.server.town.LawSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Das Gesetzbuch: Paragraphen aus Titel und Freitext. Erkannte Regelungen setzt das Spiel durch, der Rest gilt als
 * Text (später Sache der Gerichte).
 *
 * <ul>
 *   <li>{@code /civ gesetz [liste [Name]]}, {@code /civ gesetz zeigen <Nr>}, {@code /civ gesetz pruefen <Text>}.</li>
 *   <li>{@code /civ gesetz erlassen <Titel> | <Text>} oder mit Buch und Feder in der Hand nur den Titel.</li>
 *   <li>{@code /civ gesetz aufheben <Nr>}.</li>
 * </ul>
 * Wer eine Ebene führt, handelt für die oberste davon, sonst für die eigene Gemeinde. Muss abgestimmt werden, wird
 * ein Antrag eröffnet.
 */
public final class LawBookService {

    private final CivApplication app;
    private final WorldQueries queries;
    private final Calendar calendar;
    private final Texts texts;
    private final Announcements announcements;
    private final FederationService federations;

    public LawBookService(CivApplication app, Calendar calendar, Texts texts, Announcements announcements,
                          FederationService federations) {
        this.app = Objects.requireNonNull(app, "app");
        this.queries = app.queries();
        this.calendar = Objects.requireNonNull(calendar, "calendar");
        this.texts = Objects.requireNonNull(texts, "texts");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.federations = Objects.requireNonNull(federations, "federations");
    }

    /** Die Polity, für die der Spieler Gesetze erlässt: oberste geführte Ebene, sonst die eigene Gemeinde. */
    public Optional<PolityView> legislature(Caller caller) {
        return federations.actingPolity(caller.player()).or(() -> queries.municipalityOf(caller.player()));
    }

    /** Alle Paragraphen einer Polity in der Reihenfolge ihres Erlasses. */
    public List<ParagraphView> paragraphsOf(PolityView polity) {
        List<ParagraphView> paragraphs = new ArrayList<>();
        int number = 1;
        for (EnactedStatute enacted : polity.laws().inForce()) {
            List<LawSetting> effects = enacted.statute().values().stream()
                    .map(value -> new LawSetting(value.key().id(), Message.of(TranslationKeys.law(value.key())),
                            value.value()))
                    .toList();
            List<String> unrecognized = enacted.statute().text().isEmpty() ? List.of()
                    : LawRecognizer.recognize(enacted.statute().text()).unrecognized();
            paragraphs.add(new ParagraphView(number++, enacted.statute().id(), enacted.statute().title(),
                    enacted.statute().text(), enacted.enactedOn().value(), effects, unrecognized));
        }
        return paragraphs;
    }

    /** {@code /civ gesetz liste [Name]} */
    public Reply list(Caller caller, Optional<String> name) {
        Optional<PolityView> polity = name.filter(found -> !found.isBlank()).map(queries::polityNamed)
                .orElseGet(() -> queries.municipalityOf(caller.player()));
        if (polity.isEmpty()) {
            return notFound(name);
        }
        List<ParagraphView> paragraphs = paragraphsOf(polity.get());
        if (paragraphs.isEmpty()) {
            return Reply.success(Message.of(MessageKey.LAW_BOOK_EMPTY, polity.get().name().value()));
        }
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.LAW_BOOK_HEADER, polity.get().name().value(), paragraphs.size()));
        for (ParagraphView paragraph : paragraphs) {
            lines.add(Message.of(MessageKey.PARAGRAPH_ENTRY, paragraph.number(), paragraph.title(),
                    paragraph.enactedOn(), paragraph.effects().size()));
        }
        queries.ancestorsOf(polity.get().id()).stream()
                .filter(federation -> federation.laws().valueOf(StandardParameters.FEDERAL_LAW_PREVAILS))
                .forEach(federation -> lines.add(Message.of(MessageKey.LAW_BOOK_FEDERAL, federation.name().value())));
        return Reply.success(lines);
    }

    /** {@code /civ gesetz zeigen <Nr>} */
    public Reply show(Caller caller, int number) {
        Optional<PolityView> polity = queries.municipalityOf(caller.player());
        if (polity.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        return paragraph(polity.get(), number).map(paragraph -> Reply.success(describe(paragraph)))
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.UNKNOWN_PARAGRAPH, number)));
    }

    /** Chatform eines Paragraphen. */
    public List<Message> describe(ParagraphView paragraph) {
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.PARAGRAPH_HEADER, paragraph.number(), paragraph.title(), paragraph.enactedOn()));
        if (!paragraph.text().isEmpty()) {
            lines.add(Message.of(MessageKey.PARAGRAPH_TEXT, paragraph.text()));
        }
        for (LawSetting effect : paragraph.effects()) {
            lines.add(Message.of(MessageKey.PARAGRAPH_EFFECT, effect.name(), texts.lawValue(effect.value())));
        }
        if (!paragraph.unrecognized().isEmpty()) {
            lines.add(Message.of(MessageKey.PARAGRAPH_UNENFORCED, String.join(" / ", paragraph.unrecognized())));
        }
        return lines;
    }

    /** {@code /civ gesetz pruefen <Text>}: zeigt, was erkannt würde, ohne etwas zu erlassen. */
    public Reply check(String text) {
        LawRecognizer.Recognition recognition = LawRecognizer.recognize(text);
        List<Message> lines = new ArrayList<>();
        lines.add(Message.of(MessageKey.RECOGNITION_HEADER, recognition.values().size()));
        lines.addAll(effects(recognition.values()));
        if (!recognition.unrecognized().isEmpty()) {
            lines.add(Message.of(MessageKey.PARAGRAPH_UNENFORCED, String.join(" / ", recognition.unrecognized())));
        }
        return Reply.success(lines);
    }

    /** {@code /civ gesetz erlassen <Titel> | <Text>} */
    public Reply enact(Caller caller, String title, String text) {
        Optional<PolityView> polity = legislature(caller);
        if (polity.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        return enact(caller, polity.get().id(), title, text);
    }

    /** Erlass in einer bestimmten Gemeinde oder einem Bund, z. B. aus dem Gesetzbuch-Menü. */
    public Reply enact(Caller caller, PolityId polityId, String title, String text) {
        return attempt(() -> {
            EnactParagraph.Result result = app.enactParagraph().execute(new EnactParagraph.Command(polityId,
                    caller.player(), title, text, calendar.today()));
            PolityView polity = queries.polity(polityId).orElseThrow();
            List<Message> lines = new ArrayList<>();
            if (result.statute().isPresent() || accepted(result.proposal())) {
                Message enacted = Message.of(MessageKey.PARAGRAPH_ENACTED, polity.name().value(), title.strip(),
                        polity.laws().inForce().size());
                announcements.add(Announcement.toTown(polityId, enacted));
                lines.add(enacted);
            } else {
                Message opened = Message.of(MessageKey.PARAGRAPH_PROPOSED, polity.name().value(), title.strip(),
                        polity.proposals().size());
                announcements.add(Announcement.toTown(polityId, Message.of(MessageKey.PARAGRAPH_PROPOSAL_ANNOUNCED,
                        caller.name(), title.strip())));
                lines.add(opened);
            }
            lines.addAll(effects(result.recognition().values()));
            if (!result.recognition().unrecognized().isEmpty()) {
                lines.add(Message.of(MessageKey.PARAGRAPH_UNENFORCED,
                        String.join(" / ", result.recognition().unrecognized())));
            }
            return Reply.success(lines);
        });
    }

    /** {@code /civ gesetz aufheben <Nr>} */
    public Reply repeal(Caller caller, int number) {
        Optional<PolityView> polity = legislature(caller);
        if (polity.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        return repeal(caller, polity.get().id(), number);
    }

    /** Aufhebung in einer bestimmten Gemeinde oder einem Bund. */
    public Reply repeal(Caller caller, PolityId polityId, int number) {
        Optional<PolityView> polity = queries.polity(polityId);
        Optional<ParagraphView> paragraph = polity.flatMap(found -> paragraph(found, number));
        if (paragraph.isEmpty()) {
            return Reply.failure(Message.of(MessageKey.UNKNOWN_PARAGRAPH, number));
        }
        return attempt(() -> {
            Optional<ProposeMotion.Result> proposal = app.repealParagraph().execute(new RepealParagraph.Command(
                    polityId, caller.player(), paragraph.get().id(), calendar.today()));
            String name = polity.get().name().value();
            if (proposal.isEmpty() || accepted(proposal)) {
                Message repealed = Message.of(MessageKey.PARAGRAPH_REPEALED, name, paragraph.get().title());
                announcements.add(Announcement.toTown(polityId, repealed));
                return Reply.success(repealed);
            }
            return Reply.success(Message.of(MessageKey.PARAGRAPH_REPEAL_PROPOSED, name, paragraph.get().title()));
        });
    }

    private Optional<ParagraphView> paragraph(PolityView polity, int number) {
        List<ParagraphView> paragraphs = paragraphsOf(polity);
        return number >= 1 && number <= paragraphs.size() ? Optional.of(paragraphs.get(number - 1)) : Optional.empty();
    }

    private List<Message> effects(List<ParameterValue<?>> values) {
        return values.stream()
                .map(value -> Message.of(MessageKey.PARAGRAPH_EFFECT, Message.of(TranslationKeys.law(value.key())),
                        texts.lawValue(value.value())))
                .toList();
    }

    private static boolean accepted(Optional<ProposeMotion.Result> proposal) {
        return proposal.map(result -> result.outcome() == VoteOutcome.ACCEPTED).orElse(false);
    }

    private static Reply notFound(Optional<String> name) {
        return name.filter(found -> !found.isBlank())
                .map(found -> Reply.failure(Message.of(MessageKey.UNKNOWN_POLITY_NAMED, found.strip())))
                .orElseGet(() -> Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY)));
    }

    private Reply attempt(Supplier<Reply> action) {
        try {
            return action.get();
        } catch (DomainException e) {
            return Reply.failure(texts.error(e));
        }
    }
}
