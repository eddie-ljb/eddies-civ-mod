package de.etiennebader.civ.application.population;

import de.etiennebader.civ.application.law.AmendedLaws;
import de.etiennebader.civ.application.polity.Legislator;
import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PersonRef;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.law.ParameterKey;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.StandardParameters;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.PolityView;
import de.etiennebader.civ.domain.polity.Proposal;
import de.etiennebader.civ.domain.population.Personality;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dorfbewohner im Parlament entscheiden nach eigener Zufriedenheit und Parteilinie.
 *
 * <p>Ein Antrag wird danach bewertet, wie sich seine Annahme auf den Nutzen des Abgeordneten auswirkt:
 * <ul>
 *   <li>die eigene {@link Satisfaction} nach Persönlichkeit,</li>
 *   <li>dazu, wer einer Partei angehört, die Zufriedenheit einer Person mit genau der Richtung der Partei, und bei
 *       Parteien des Ausbaus ein Plus für Steuereinnahmen.</li>
 * </ul>
 * Steigt der Nutzen um mehr als {@value #DECIDE} Punkte, stimmt er dafür, sinkt er so stark, dagegen; sonst folgt er
 * Anträgen der eigenen Partei und enthält sich bei anderen. Anträge stellt er selbst, wenn eine einzelne Änderung
 * (Schutzregeln umschalten, Steuern um einen Schritt ändern) seinen Nutzen um mindestens {@value #PROPOSE} Punkte hebt.
 */
public final class PersonalityLegislator implements Legislator {

    static final double DECIDE = 1;
    static final double PROPOSE = 4;
    /** So stark gewichten Ausbau-Parteien die Steuereinnahmen. */
    static final double REVENUE = 20;

    private static final List<ParameterKey<Boolean>> ORDER_RULES = List.of(StandardParameters.EXPLOSIONS_ALLOWED,
            StandardParameters.FIRE_SPREAD_ALLOWED, StandardParameters.OUTSIDERS_MAY_BUILD,
            StandardParameters.OUTSIDERS_MAY_INTERACT, StandardParameters.OUTSIDERS_MAY_OPEN_CONTAINERS);
    private static final long SALES_TAX_STEP = 5;
    private static final long LAND_TAX_STEP = 1;

    private final Function<PolityView, LawView> lawsOf;
    private final Function<ActorRef, Optional<PolityView>> partyOf;

    /** Ohne Bundesrecht und Parteien, z. B. für Tests. */
    public PersonalityLegislator() {
        this(PolityView::laws, actor -> Optional.empty());
    }

    /**
     * @param lawsOf  das tatsächlich geltende Recht einer Polity
     * @param partyOf die Partei eines Akteurs
     */
    public PersonalityLegislator(Function<PolityView, LawView> lawsOf,
                                 Function<ActorRef, Optional<PolityView>> partyOf) {
        this.lawsOf = Objects.requireNonNull(lawsOf, "lawsOf");
        this.partyOf = Objects.requireNonNull(partyOf, "partyOf");
    }

    @Override
    public Optional<Boolean> decide(PersonRef member, Proposal proposal, PolityView polity) {
        LawView now = lawsOf.apply(polity);
        double gain = utility(member, polity, AmendedLaws.of(now, polity, proposal.motion()))
                - utility(member, polity, now);
        if (gain > DECIDE) {
            return Optional.of(true);
        }
        if (gain < -DECIDE) {
            return Optional.of(false);
        }
        Optional<PolityId> own = partyOf.apply(member).map(PolityView::id);
        boolean sameParty = own.isPresent()
                && partyOf.apply(proposal.proposer()).map(PolityView::id).equals(own);
        return sameParty ? Optional.of(true) : Optional.empty();
    }

    @Override
    public Optional<Motion> propose(PersonRef member, PolityView polity) {
        LawView now = lawsOf.apply(polity);
        double current = utility(member, polity, now);
        Set<String> open = polity.proposals().stream().map(proposal -> proposal.motion().subject())
                .collect(Collectors.toSet());
        Motion best = null;
        double bestGain = PROPOSE;
        for (ParameterValue<?> change : candidates(now)) {
            Motion motion = new Motion.Change(change);
            if (open.contains(motion.subject())) {
                continue;
            }
            double gain = utility(member, polity, AmendedLaws.of(now, polity, motion)) - current;
            if (gain >= bestGain) {
                best = motion;
                bestGain = gain;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Einzelne Änderungen, die ein Abgeordneter erwägt. */
    static List<ParameterValue<?>> candidates(LawView laws) {
        List<ParameterValue<?>> changes = new ArrayList<>();
        for (ParameterKey<Boolean> rule : ORDER_RULES) {
            changes.add(ParameterValue.of(rule, !laws.valueOf(rule)));
        }
        long sales = laws.valueOf(StandardParameters.SALES_TAX_PERCENT);
        addIfValid(changes, StandardParameters.SALES_TAX_PERCENT, sales + SALES_TAX_STEP);
        addIfValid(changes, StandardParameters.SALES_TAX_PERCENT, sales - SALES_TAX_STEP);
        long land = laws.valueOf(StandardParameters.LAND_TAX_PER_BLOCK);
        addIfValid(changes, StandardParameters.LAND_TAX_PER_BLOCK, land + LAND_TAX_STEP);
        addIfValid(changes, StandardParameters.LAND_TAX_PER_BLOCK, land - LAND_TAX_STEP);
        return changes;
    }

    private static void addIfValid(List<ParameterValue<?>> changes, ParameterKey<Long> key, long value) {
        if (key.accepts(value)) {
            changes.add(ParameterValue.of(key, value));
        }
    }

    /** Nutzen eines Rechtsstands für den Abgeordneten. */
    double utility(PersonRef member, PolityView polity, LawView laws) {
        double utility = Satisfaction.of(Personality.of(member), polity, laws).score();
        Optional<PartyProfile> line = partyOf.apply(member).flatMap(PolityView::partyProfile);
        if (line.isPresent()) {
            Personality party = new Personality(line.get().order(), -line.get().economy(), 0);
            utility += Satisfaction.of(party, polity, laws).score();
            utility += Math.max(0, line.get().economy()) / 100.0 * REVENUE * Satisfaction.taxIndex(laws);
        }
        return utility;
    }
}
