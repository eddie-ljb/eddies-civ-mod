package de.etiennebader.civ.domain.protection;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.land.LandView;
import de.etiennebader.civ.domain.land.Plot;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.law.StandardParameters;
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
            if (context.mayUsePlot(actor, polity.id(), plot.get().id())) {
                return Optional.empty();
            }
            return checkForeignPlot(actor, action, polity, plot.get(), context);
        }

        if (plot.isPresent() && context.mayUsePlot(actor, polity.id(), plot.get().id())) {
            return Optional.empty();
        }
        if (polity.isMember(actor) || context.isAlly(actor, polity)
                || context.laws(polity).valueOf(action.outsiderPermission())) {
            return Optional.empty();
        }
        return deny(DenialReason.FOREIGN_TERRITORY, polity, polity.asActor());
    }

    /**
     * Ob eine Naturgewalt an einem Block wirken darf: in der Wildnis immer, im Gemeindegebiet nur, wenn das
     * Recht der Gemeinde es erlaubt.
     */
    public boolean allows(Hazard hazard, BlockPosition position, ProtectionContext context) {
        Objects.requireNonNull(hazard, "hazard");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(context, "context");
        Optional<Claim> claim = context.territory().claimAt(position.chunk());
        if (claim.isEmpty()) {
            return true;
        }
        return allows(PlotFlag.of(hazard), position, context);
    }

    /**
     * Ob eine Regel (PvP, Monster, Explosionen, Feuer, Tierschutz) an diesem Block gilt: in der Wildnis immer, sonst
     * nach dem Recht der Gemeinde. Auf einem fremden Grundstück darf dessen Inhaber davon abweichen, wenn das Recht
     * {@link de.etiennebader.civ.domain.law.StandardParameters#PLOT_RIGHTS_OVERRIDE} es erlaubt.
     */
    public boolean allows(PlotFlag flag, BlockPosition position, ProtectionContext context) {
        Objects.requireNonNull(flag, "flag");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(context, "context");
        Optional<Claim> claim = context.territory().claimAt(position.chunk());
        if (claim.isEmpty()) {
            return true;
        }
        Optional<? extends PolityView> polity = context.polity(claim.get().polity());
        if (polity.isEmpty()) {
            return true;
        }
        LawView laws = context.laws(polity.get());
        boolean law = laws.valueOf(flag.law());
        if (!laws.valueOf(StandardParameters.PLOT_RIGHTS_OVERRIDE)) {
            return law;
        }
        return context.land(polity.get().id()).flatMap(land -> plotAt(land, position))
                .filter(plot -> !plot.holder().equals(polity.get().asActor()))
                .flatMap(plot -> context.plotRights(polity.get().id(), plot.id()))
                .map(rights -> rights.stance(flag).resolve(law))
                .orElse(law);
    }

    private static Optional<Denial> checkForeignPlot(ActorRef actor, ProtectedAction action, PolityView polity,
                                                     Plot plot, ProtectionContext context) {
        ActorRef holder = plot.holder();
        if (isHolder(actor, holder, context)) {
            return Optional.empty();
        }
        PlotRights rights = context.plotRights(polity.id(), plot.id()).orElse(PlotRights.DEFAULT);
        boolean granted = rights.permits(actor, polity.isMember(actor), action);
        LawView laws = context.laws(polity);
        // Darf der Inhaber das Recht überschreiben, entscheidet allein seine Einstellung. Sonst gilt das Recht
        // der Gemeinde, des Landes und der Nation auch hier: Erlaubt es Fremden die Handlung, erlaubt es sie überall.
        boolean allowed = laws.valueOf(StandardParameters.PLOT_RIGHTS_OVERRIDE)
                ? granted : granted || laws.valueOf(action.outsiderPermission());
        return allowed ? Optional.empty() : deny(DenialReason.FOREIGN_PLOT, polity, holder);
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
