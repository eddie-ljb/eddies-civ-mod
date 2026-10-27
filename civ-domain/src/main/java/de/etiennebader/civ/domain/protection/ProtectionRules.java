package de.etiennebader.civ.domain.protection;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.Claim;

import java.util.Objects;
import java.util.Optional;

/**
 * Entscheidet, ob ein Akteur an einem Block handeln darf.
 *
 * <ol>
 *   <li>Wildnis (kein Claim): alles erlaubt.</li>
 *   <li>Das Gemeindehaus darf niemand verändern.</li>
 *   <li>Auf einem Grundstück, das nicht der Gemeinde selbst gehört, darf nur der Inhaber alles.
 *       Mitglieder der Gemeinde dürfen dort Türen und Knöpfe benutzen, sonst nichts.
 *       Gehört das Grundstück einer anderen Gemeinde (Botschaft), zählen deren Mitglieder als Inhaber.</li>
 *   <li>Auf dem übrigen Gemeindeland dürfen Mitglieder alles. Für Fremde entscheidet das
 *       geltende Recht ({@link ProtectedAction#outsiderPermission()}).</li>
 * </ol>
 */
public final class ProtectionRules {

    /**
     * @return die Verweigerung samt Grund; leer, wenn die Handlung erlaubt ist
     */
    public Optional<Denial> check(ActorRef actor, ProtectedAction action, BlockPosition position,
                                  ProtectionContext context) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(context, "context");

        Optional<Claim> claim = context.territory().claimAt(position.chunk());
        if (claim.isEmpty()) {
            return Optional.empty();
        }
        PolityView polity = context.polity(claim.get().polity())
                .orElseThrow(() -> new IllegalStateException("Claim ohne Polity: " + claim.get()));

        if (action == ProtectedAction.BUILD && polity.seat().filter(position::equals).isPresent()) {
            return deny(DenialReason.SEAT, polity, polity.asActor());
        }

        Optional<Plot> plot = context.land(polity.id())
                .flatMap(land -> plotAt(land, position));
        if (plot.isPresent() && !plot.get().holder().equals(polity.asActor())) {
            return checkForeignPlot(actor, action, polity, plot.get().holder(), context);
        }

        if (polity.isMember(actor) || polity.laws().valueOf(action.outsiderPermission())) {
            return Optional.empty();
        }
        return deny(DenialReason.FOREIGN_TERRITORY, polity, polity.asActor());
    }

    private static Optional<Denial> checkForeignPlot(ActorRef actor, ProtectedAction action, PolityView polity,
                                                     ActorRef holder, ProtectionContext context) {
        if (isHolder(actor, holder, context)) {
            return Optional.empty();
        }
        if (action == ProtectedAction.INTERACT && polity.isMember(actor)) {
            return Optional.empty();
        }
        return deny(DenialReason.FOREIGN_PLOT, polity, holder);
    }

    private static boolean isHolder(ActorRef actor, ActorRef holder, ProtectionContext context) {
        if (actor.equals(holder)) {
            return true;
        }
        return holder instanceof PolityRef holdingPolity
                && context.polity(holdingPolity.id()).map(found -> found.isMember(actor)).orElse(false);
    }

    private static Optional<Plot> plotAt(LandView land, BlockPosition position) {
        return land.plotAt(position.dimension(), position.x(), position.z());
    }

    private static Optional<Denial> deny(DenialReason reason, PolityView polity, ActorRef owner) {
        return Optional.of(new Denial(reason, polity.id(), owner));
    }
}
