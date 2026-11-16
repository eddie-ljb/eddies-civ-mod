package de.etiennebader.civ.domain.justice;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein Gerichtsverfahren.
 *
 * @param court      die Polity, deren Gericht verhandelt
 * @param plaintiff  Kläger bzw. Anzeigender
 * @param defendant  Beklagter bzw. Angeklagter; bei Verfassungsklagen die Polity selbst
 * @param charge     Vorwurf bzw. Klagegrund
 * @param target     bei Zivilklagen der geforderte Betrag, bei Verfassungsklagen die angegriffene Regel
 *                   ({@code §3} oder eine Regelungs-ID); sonst leer
 * @param stageUntil letzter Tag des aktuellen Abschnitts
 * @param votes      Stimmen der Geschworenen: {@code true} = schuldig
 * @param appealOf   das Verfahren der Vorinstanz bei einer Berufung
 */
public record CourtCase(CaseId id, int number, PolityId court, CaseKind kind, ActorRef plaintiff, ActorRef defendant,
                        String charge, String target, Optional<ActorRef> judge, Optional<ActorRef> prosecutor,
                        Optional<ActorRef> defense, List<ActorRef> witnesses, List<ActorRef> jurors,
                        List<Evidence> evidence, List<Statement> statements, Map<ActorRef, Boolean> votes,
                        Stage stage, Day filedOn, Day stageUntil, Optional<Verdict> verdict, Optional<CaseId> appealOf) {

    public static final int MAX_CHARGE = 200;
    public static final int MAX_EVIDENCE = 30;
    public static final int MAX_STATEMENTS = 40;
    public static final int MAX_WITNESSES = 8;

    public CourtCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(court, "court");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(plaintiff, "plaintiff");
        Objects.requireNonNull(defendant, "defendant");
        charge = Objects.requireNonNull(charge, "charge").strip();
        if (charge.isEmpty() || charge.length() > MAX_CHARGE) {
            throw new IllegalArgumentException("Vorwurf leer oder zu lang");
        }
        target = Objects.requireNonNull(target, "target").strip();
        Objects.requireNonNull(judge, "judge");
        Objects.requireNonNull(prosecutor, "prosecutor");
        Objects.requireNonNull(defense, "defense");
        witnesses = List.copyOf(witnesses);
        jurors = List.copyOf(jurors);
        evidence = List.copyOf(evidence);
        statements = List.copyOf(statements);
        votes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(votes));
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(filedOn, "filedOn");
        Objects.requireNonNull(stageUntil, "stageUntil");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(appealOf, "appealOf");
    }

    /** Die Rolle eines Akteurs; leer, wenn er nicht beteiligt ist. Richter vor Parteien. */
    public Optional<Role> roleOf(ActorRef actor) {
        if (judge.filter(actor::equals).isPresent()) {
            return Optional.of(Role.JUDGE);
        }
        if (prosecutor.filter(actor::equals).isPresent()) {
            return Optional.of(Role.PROSECUTOR);
        }
        if (plaintiff.equals(actor)) {
            return Optional.of(Role.PLAINTIFF);
        }
        if (defendant.equals(actor)) {
            return Optional.of(Role.DEFENDANT);
        }
        if (defense.filter(actor::equals).isPresent()) {
            return Optional.of(Role.DEFENSE);
        }
        if (witnesses.contains(actor)) {
            return Optional.of(Role.WITNESS);
        }
        if (jurors.contains(actor)) {
            return Optional.of(Role.JUROR);
        }
        return Optional.empty();
    }

    /** Die Seite, für die ein Beteiligter spricht; Zeugen und Unbeteiligte sind neutral. */
    public Optional<Side> sideOf(ActorRef actor) {
        return roleOf(actor).flatMap(role -> switch (role) {
            case PROSECUTOR, PLAINTIFF -> Optional.of(Side.PROSECUTION);
            case DEFENDANT, DEFENSE -> Optional.of(Side.DEFENSE);
            default -> Optional.empty();
        });
    }

    public boolean hasJury() {
        return !jurors.isEmpty();
    }

    CourtCase at(Stage next, Day until) {
        return new CourtCase(id, number, court, kind, plaintiff, defendant, charge, target, judge, prosecutor, defense,
                witnesses, jurors, evidence, statements, votes, next, filedOn, until, verdict, appealOf);
    }

    CourtCase staffed(Optional<ActorRef> newJudge, Optional<ActorRef> newProsecutor, Optional<ActorRef> newDefense,
                      List<ActorRef> newJurors) {
        return new CourtCase(id, number, court, kind, plaintiff, defendant, charge, target, newJudge, newProsecutor,
                newDefense, witnesses, newJurors, evidence, statements, votes, stage, filedOn, stageUntil, verdict,
                appealOf);
    }

    CourtCase withWitness(ActorRef witness) {
        List<ActorRef> updated = new ArrayList<>(witnesses);
        if (!updated.contains(witness)) {
            updated.add(witness);
        }
        return new CourtCase(id, number, court, kind, plaintiff, defendant, charge, target, judge, prosecutor, defense,
                updated, jurors, evidence, statements, votes, stage, filedOn, stageUntil, verdict, appealOf);
    }

    CourtCase withEvidence(Evidence added) {
        List<Evidence> updated = new ArrayList<>(evidence);
        updated.add(added);
        return new CourtCase(id, number, court, kind, plaintiff, defendant, charge, target, judge, prosecutor, defense,
                witnesses, jurors, updated, statements, votes, stage, filedOn, stageUntil, verdict, appealOf);
    }

    CourtCase withStatement(Statement added) {
        List<Statement> updated = new ArrayList<>(statements);
        updated.add(added);
        return new CourtCase(id, number, court, kind, plaintiff, defendant, charge, target, judge, prosecutor, defense,
                witnesses, jurors, evidence, updated, votes, stage, filedOn, stageUntil, verdict, appealOf);
    }

    CourtCase withVote(ActorRef juror, boolean guilty) {
        Map<ActorRef, Boolean> updated = new LinkedHashMap<>(votes);
        updated.put(juror, guilty);
        return new CourtCase(id, number, court, kind, plaintiff, defendant, charge, target, judge, prosecutor, defense,
                witnesses, jurors, evidence, statements, updated, stage, filedOn, stageUntil, verdict, appealOf);
    }

    CourtCase decided(Verdict decision, Day finalOn) {
        return new CourtCase(id, number, court, kind, plaintiff, defendant, charge, target, judge, prosecutor, defense,
                witnesses, jurors, evidence, statements, votes, Stage.DECIDED, filedOn, finalOn, Optional.of(decision),
                appealOf);
    }
}
