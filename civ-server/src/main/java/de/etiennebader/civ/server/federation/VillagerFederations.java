package de.etiennebader.civ.server.federation;

import de.etiennebader.civ.application.CivApplication;
import de.etiennebader.civ.application.federation.FoundFederation;
import de.etiennebader.civ.application.federation.JoinFederation;
import de.etiennebader.civ.application.population.VillagerAmbitions;
import de.etiennebader.civ.application.query.WorldQueries;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.DomainException;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.server.Announcement;
import de.etiennebader.civ.server.Announcements;
import de.etiennebader.civ.server.command.Message;
import de.etiennebader.civ.server.command.MessageKey;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ehrgeizige Dorfbewohner als Bürgermeister bauen Bünde: Sie gründen über ihrer Gemeinde ein Land oder eine Nation,
 * erweitern ihren Bund um Gemeinden und nehmen Einladungen anderer Bünde an.
 *
 * <ul>
 *   <li><b>Gründen:</b> Wer eine Gemeinde oder einen Bund führt, der noch keinem Bund angehört, gründet mit
 *       {@value #FOUND_CHANCE} Tageswahrscheinlichkeit (gewichtet mit dem Ehrgeiz) einen Bund der nächsthöheren Ebene.
 *       Es gibt höchstens einen Bund je drei Gemeinden.</li>
 *   <li><b>Erweitern:</b> Der Leiter eines Bundes nimmt eine freie Gemeinde niedrigerer Ebene auf. Wird sie von einem
 *       Dorfbewohner geführt (oder von niemandem), tritt sie gleich bei; wird sie von einem Spieler geführt, bekommt
 *       sie eine Einladung und der Spieler entscheidet.</li>
 *   <li><b>Annehmen:</b> Eine Gemeinde unter Führung eines ehrgeizigen Dorfbewohners tritt dem einladenden Bund bei.</li>
 * </ul>
 *
 * <p>Je Tag geschieht höchstens eine solche Handlung. Der Zufall ist fest je Dorfbewohner und Tag.
 */
public final class VillagerFederations {

    static final double FOUND_CHANCE = 0.05;
    private static final int MUNICIPALITIES_PER_FEDERATION = 3;

    private final CivApplication app;
    private final FederationService federations;
    private final Announcements announcements;

    public VillagerFederations(CivApplication app, FederationService federations, Announcements announcements) {
        this.app = Objects.requireNonNull(app, "app");
        this.federations = Objects.requireNonNull(federations, "federations");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
    }

    /** @return ob heute etwas geschah */
    public boolean endOfDay(Day day) {
        WorldQueries world = app.queries();
        List<PolityView> led = world.polities().stream()
                .filter(polity -> polity.kind() == PolityKind.TERRITORIAL || polity.kind() == PolityKind.FEDERATION)
                .filter(polity -> villagerHead(polity).filter(VillagerAmbitions::isAmbitious).isPresent())
                .sorted(Comparator.comparing(polity -> polity.id().value()))
                .toList();
        int start = led.isEmpty() ? 0 : (int) Math.floorMod(day.value(), (long) led.size());
        for (int index = 0; index < led.size(); index++) {
            PolityView polity = led.get((start + index) % led.size());
            PersonRef head = villagerHead(polity).orElseThrow();
            double drive = VillagerAmbitions.drive(head) / 100.0;
            if (accept(polity, day) || expand(polity, head, drive, day) || found(polity, head, drive, day)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<PersonRef> villagerHead(PolityView polity) {
        return polity.mayor().filter(PersonRef.class::isInstance).map(PersonRef.class::cast);
    }

    private boolean found(PolityView polity, PersonRef head, double drive, Day day) {
        WorldQueries world = app.queries();
        if (world.parentOf(polity.id()).isPresent()
                || VillagerAmbitions.roll(head, day, 0x61) >= FOUND_CHANCE * drive) {
            return false;
        }
        long municipalities = world.polities().stream().filter(p -> p.kind() == PolityKind.TERRITORIAL).count();
        if (world.federations().size() >= Math.max(1, municipalities / MUNICIPALITIES_PER_FEDERATION)) {
            return false;
        }
        int rank = federations.levelOf(polity).rank();
        Optional<Level> next = federations.levels().federal().stream().filter(level -> level.rank() > rank)
                .min(Comparator.comparingInt(Level::rank));
        if (next.isEmpty()) {
            return false;
        }
        String label = next.get().builtIn()
                ? next.get().id().substring(0, 1).toUpperCase() + next.get().id().substring(1) : next.get().name();
        String name = label + " " + polity.name().value();
        try {
            PolityName federationName = PolityName.of(name.length() > PolityName.MAX_LENGTH
                    ? name.substring(0, PolityName.MAX_LENGTH) : name);
            app.foundFederation().execute(new FoundFederation.Command(federationName, next.get().id(), polity.id(),
                    head, day));
            announcements.add(Announcement.toAll(Message.of(MessageKey.FEDERATION_ANNOUNCED, "Ein Dorfbewohner",
                    next.get().displayName(), federationName.value(), polity.name().value())));
            return true;
        } catch (DomainException ignored) {
            return false;
        }
    }

    private boolean expand(PolityView federation, PersonRef head, double drive, Day day) {
        if (federation.kind() != PolityKind.FEDERATION
                || VillagerAmbitions.roll(head, day, 0x62) >= 2 * FOUND_CHANCE * drive) {
            return false;
        }
        WorldQueries world = app.queries();
        int rank = federations.levelOf(federation).rank();
        Optional<PolityView> guest = world.polities().stream()
                .filter(polity -> polity.kind() == PolityKind.TERRITORIAL || polity.kind() == PolityKind.FEDERATION)
                .filter(polity -> !polity.id().equals(federation.id()))
                .filter(polity -> federations.levelOf(polity).rank() < rank)
                .filter(polity -> world.parentOf(polity.id()).isEmpty())
                .filter(polity -> !federations.invitationsFor(polity.id()).contains(federation.id()))
                .min(Comparator.comparing(polity -> polity.id().value()));
        if (guest.isEmpty()) {
            return false;
        }
        try {
            if (guest.get().mayor().isEmpty() || villagerHead(guest.get()).isPresent()) {
                app.joinFederation().execute(new JoinFederation.Command(federation.id(), guest.get().id(), day));
                announcements.add(Announcement.toTown(federation.id(), Message.of(MessageKey.FEDERATION_JOINED,
                        guest.get().name().value(), federation.name().value())));
            } else {
                federations.sendInvitation(federation, guest.get());
            }
            return true;
        } catch (DomainException ignored) {
            return false;
        }
    }

    /** Nimmt die erste Einladung an, die zu einem höheren Bund führt. */
    private boolean accept(PolityView polity, Day day) {
        WorldQueries world = app.queries();
        int rank = federations.levelOf(polity).rank();
        for (PolityId hostId : federations.invitationsFor(polity.id())) {
            Optional<PolityView> host = world.polity(hostId);
            if (host.isPresent() && federations.levelOf(host.get()).rank() > rank
                    && world.parentOf(polity.id()).isEmpty()) {
                try {
                    federations.accept(host.get(), polity);
                    return true;
                } catch (DomainException ignored) {
                    // z. B. Kreis im Bundesbaum: dann bleibt die Einladung unbeantwortet
                }
            }
        }
        return false;
    }
}
