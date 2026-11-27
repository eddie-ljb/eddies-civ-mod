package de.etiennebader.civ.server.law;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.day.Calendar;
import de.etiennebader.civ.application.law.EnactParagraph;
import de.etiennebader.civ.application.law.RepealParagraph;
import de.etiennebader.civ.application.polity.ProposeMotion;
import de.etiennebader.civ.application.polity.VoteOutcome;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PlayerRef;
import de.etiennebader.civ.domain.shared.PlayerId;
import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.law.LawPath;
import de.etiennebader.civ.domain.law.LawRecognizer;
import de.etiennebader.civ.domain.law.LawText;
import de.etiennebader.civ.domain.law.ParameterCatalog;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.law.Statute;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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

    /**
     * Gesetzesvorlagen für den Gesetzes-Editor im Web (#10): bewusst standardmäßig nur im Arbeitsspeicher (bis zum
     * Serverneustart); mit {@code law_drafts_persist=true} zusätzlich in einer Datei im Datenordner – siehe
     * {@code docs/issue-10-gesetzeseditor.md}.
     */
    /** Obergrenzen gegen unbegrenztes Wachstum des Arbeitsspeichers. */
    static final int MAX_DRAFTS_PER_AUTHOR = 30;
    static final int MAX_RIGHTS_PER_PARAGRAPH = 50;

    private final Map<String, LawDraft> drafts = new LinkedHashMap<>();

    /** Wohin Entwürfe geschrieben werden, wenn {@code law_drafts_persist} an ist; sonst leer. */
    private final Optional<Path> draftFile;

    public LawBookService(CivApplication app, Calendar calendar, Texts texts, Announcements announcements,
                          FederationService federations) {
        this(app, calendar, texts, announcements, federations, Optional.empty());
    }

    public LawBookService(CivApplication app, Calendar calendar, Texts texts, Announcements announcements,
                          FederationService federations, Optional<Path> draftFile) {
        this.draftFile = Objects.requireNonNull(draftFile, "draftFile");
        loadDrafts();
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
            paragraphs.add(new ParagraphView(number++, enacted.statute().id(), enacted.statute().lawName(),
                    enacted.statute().partName(), enacted.statute().title(), enacted.statute().text(),
                    enacted.enactedOn().value(), effects, unrecognized, LawText.sections(enacted.statute().text())));
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
            lines.add(Message.of(MessageKey.PARAGRAPH_ENTRY, paragraph.number(), paragraph.qualifiedTitle(),
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
        lines.add(Message.of(MessageKey.PARAGRAPH_HEADER, paragraph.number(), paragraph.qualifiedTitle(),
                paragraph.enactedOn()));
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
        return enact(caller, polityId, title, text, List.of());
    }

    /** Mit eigenen Feldern für Gesetz und Teil, z. B. aus dem Web-Editor; leere Felder bleiben bei der Titelangabe. */
    public Reply enact(Caller caller, PolityId polityId, String lawName, String partName, String title, String text) {
        LawPath path = LawPath.parse(title);
        return enact(caller, polityId, lawName.isBlank() ? path.lawName() : lawName,
                partName.isBlank() ? path.partName() : partName, path.title(), text, List.of());
    }

    private Reply enact(Caller caller, PolityId polityId, String title, String text,
                        List<ParameterValue<?>> explicitValues) {
        LawPath path = LawPath.parse(title);
        return enact(caller, polityId, path.lawName(), path.partName(), path.title(), text, explicitValues);
    }

    private Reply enact(Caller caller, PolityId polityId, String lawName, String partName, String title, String text,
                        List<ParameterValue<?>> explicitValues) {
        return attempt(() -> {
            EnactParagraph.Result result = app.enactParagraph().execute(new EnactParagraph.Command(polityId,
                    caller.player(), lawName.strip(), partName.strip(), title, text, explicitValues,
                    calendar.today()));
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
            lines.addAll(effects(merge(result.recognition().values(), explicitValues)));
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

    private static List<ParameterValue<?>> merge(List<ParameterValue<?>> recognized,
                                                  List<ParameterValue<?>> explicit) {
        Map<ParameterKey<?>, ParameterValue<?>> byKey = new LinkedHashMap<>();
        recognized.forEach(value -> byKey.put(value.key(), value));
        explicit.forEach(value -> byKey.put(value.key(), value));
        return List.copyOf(byKey.values());
    }

    // ---- Rechte-Editor und Gesetzesüberblick im Web (#10) ----

    /** Ein Recht (Parameter) einer Polity mit geltendem Wert. */
    public record RightView(ParameterKey<?> key, Object value, Object defaultValue, boolean explicit) {
    }

    /** Alle Rechte einer Polity mit geltendem Wert, in Katalogreihenfolge. */
    public List<RightView> rightsOf(PolityView polity) {
        List<RightView> rights = new ArrayList<>();
        for (ParameterKey<?> key : ParameterCatalog.standard().keys()) {
            rights.add(rightOf(polity, key));
        }
        return rights;
    }

    private static <T> RightView rightOf(PolityView polity, ParameterKey<T> key) {
        Optional<T> explicit = polity.laws().explicitValueOf(key);
        return new RightView(key, explicit.orElse(key.defaultValue()), key.defaultValue(), explicit.isPresent());
    }

    /** Darf die Person für diese Polity Gesetze erlassen oder beantragen (Ebene, die sie führt, sonst Wohngemeinde)? */
    public boolean canLegislate(Caller caller, PolityId polityId) {
        return legislature(caller).map(PolityView::id).filter(polityId::equals).isPresent();
    }

    /**
     * Setzt Rechte ausdrücklich als Paragraphen, ohne Freitext: {@code values} bildet Parameter-ID auf Wert ab.
     * Wie jeder Paragraph wird er sofort erlassen oder, bei Abstimmungspflicht, als Antrag eröffnet.
     */
    public Reply enactRights(Caller caller, PolityId polityId, String title, Map<String, String> values) {
        if (!canLegislate(caller, polityId)) {
            return Reply.failure(Message.of(MessageKey.NOT_IN_MUNICIPALITY));
        }
        if (values.isEmpty() || values.size() > MAX_RIGHTS_PER_PARAGRAPH) {
            return Reply.failure(Message.of(MessageKey.INVALID_LAW_VALUE, String.valueOf(values.size()), ""));
        }
        ParameterCatalog catalog = ParameterCatalog.standard();
        List<ParameterValue<?>> decoded = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            try {
                ParameterValue<?> value = catalog.decode(entry.getKey(), entry.getValue());
                if (!value.key().accepts(value.value())) {
                    return Reply.failure(Message.of(MessageKey.INVALID_LAW_VALUE, entry.getValue(), entry.getKey()));
                }
                decoded.add(value);
            } catch (IllegalArgumentException e) {
                return Reply.failure(Message.of(MessageKey.INVALID_LAW_VALUE, entry.getValue(), entry.getKey()));
            }
        }
        return enact(caller, polityId, title, "", decoded);
    }

    // ---- Gesetzesvorlagen (#10) ----

    /**
     * Ein gespeicherter Entwurf für einen Paragraphen: sichtbar für die Autorin/den Autor und, sofern vorhanden,
     * für die eigene Partei zum Zeitpunkt des Speicherns. Ob die Partei später wechselt, ändert die Sichtbarkeit
     * eines schon gespeicherten Entwurfs nicht mehr.
     */
    public record LawDraft(String id, ActorRef author, String authorName, Optional<PolityId> party,
                           Optional<PolityId> targetPolity, String title, String text, long savedOn) {
    }

    /**
     * Speichert einen Entwurf neu oder, mit {@code id} eines eigenen vorhandenen Entwurfs, überschreibt ihn.
     *
     * @throws IllegalArgumentException bei leerem/zu langem Titel oder Text, oder wenn {@code id} den Entwurf
     *                                  einer anderen Person meint
     */
    public LawDraft saveDraft(Caller caller, Optional<String> id, Optional<PolityId> targetPolity, String title,
                              String text) {
        String draftTitle = title.strip();
        String draftText = text.strip();
        if (draftTitle.isEmpty() || draftTitle.length() > Statute.MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Titel leer oder zu lang (höchstens %d Zeichen)"
                    .formatted(Statute.MAX_TITLE_LENGTH));
        }
        if (draftText.length() > Statute.MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Text zu lang (höchstens %d Zeichen)".formatted(Statute.MAX_TEXT_LENGTH));
        }
        if (id.isPresent() && drafts.containsKey(id.get()) && !drafts.get(id.get()).author().equals(caller.player())) {
            throw new IllegalArgumentException("Entwurf gehört einer anderen Person");
        }
        boolean overwrite = id.isPresent() && drafts.containsKey(id.get());
        if (!overwrite && drafts.values().stream().filter(d -> d.author().equals(caller.player())).count()
                >= MAX_DRAFTS_PER_AUTHOR) {
            throw new IllegalArgumentException("Zu viele Entwürfe (höchstens %d)".formatted(MAX_DRAFTS_PER_AUTHOR));
        }
        String draftId = id.filter(drafts::containsKey).orElseGet(() -> UUID.randomUUID().toString());
        Optional<PolityId> party = queries.partyOf(caller.player()).map(PolityView::id);
        LawDraft draft = new LawDraft(draftId, caller.player(), caller.name(), party, targetPolity, draftTitle,
                draftText, calendar.today().value());
        drafts.put(draftId, draft);
        storeDrafts();
        return draft;
    }

    /** Eigene Entwürfe und die der eigenen Partei, neueste zuerst. */
    public List<LawDraft> draftsVisibleTo(Caller caller) {
        Optional<PolityId> party = queries.partyOf(caller.player()).map(PolityView::id);
        return drafts.values().stream()
                .filter(draft -> draft.author().equals(caller.player())
                        || (party.isPresent() && draft.party().equals(party)))
                .sorted(Comparator.comparingLong(LawDraft::savedOn).reversed())
                .toList();
    }

    /** @return ob gelöscht wurde; {@code false} ohne diesen Entwurf oder wenn er einer anderen Person gehört */
    public boolean deleteDraft(Caller caller, String id) {
        LawDraft draft = drafts.get(id);
        if (draft == null || !draft.author().equals(caller.player())) {
            return false;
        }
        drafts.remove(id);
        storeDrafts();
        return true;
    }

    // Dateiformat: eine Zeile je Entwurf, Felder durch Tab getrennt, \\ \t \n maskiert.
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                out.append(next == 't' ? '\t' : next == 'n' ? '\n' : next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private void storeDrafts() {
        if (draftFile.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (LawDraft d : drafts.values()) {
            lines.add(String.join("\t", d.id(), d.author().uuid().toString(), escape(d.authorName()),
                    d.party().map(id -> id.value().toString()).orElse(""),
                    d.targetPolity().map(id -> id.value().toString()).orElse(""), escape(d.title()),
                    escape(d.text()), Long.toString(d.savedOn())));
        }
        try {
            Path target = draftFile.get();
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.write(temp, lines);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            // Der Entwurf bleibt im Speicher gültig; nur das Schreiben ist gescheitert.
            System.err.println("Gesetzesvorlagen konnten nicht gespeichert werden: " + e);
        }
    }

    private void loadDrafts() {
        if (draftFile.isEmpty() || !Files.isRegularFile(draftFile.get())) {
            return;
        }
        try {
            for (String line : Files.readAllLines(draftFile.get())) {
                String[] f = line.split("\t", -1);
                if (f.length != 8) {
                    continue;
                }
                try {
                    ActorRef author = new PlayerRef(new PlayerId(UUID.fromString(f[1])));
                    drafts.put(f[0], new LawDraft(f[0], author, unescape(f[2]),
                            f[3].isEmpty() ? Optional.empty() : Optional.of(new PolityId(UUID.fromString(f[3]))),
                            f[4].isEmpty() ? Optional.empty() : Optional.of(new PolityId(UUID.fromString(f[4]))),
                            unescape(f[5]), unescape(f[6]), Long.parseLong(f[7])));
                } catch (IllegalArgumentException e) {
                    // kaputte Zeile überspringen
                }
            }
        } catch (IOException e) {
            System.err.println("Gesetzesvorlagen konnten nicht gelesen werden: " + e);
        }
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
