package de.etiennebader.civ.domain.police;

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
 * Polizeizustand aller Gemeinden: Dienstgrade, Anzeigen, Zellen, Inhaftierte und Handschellen.
 * Regeln hier, Berechtigungen (Bürgermeister, Befehlskette) im Dienst.
 */
public final class PoliceRegistry {

    /** Handschellen halten höchstens so viele Tage, falls niemand sie löst. */
    public static final int CUFF_DAYS = 1;
    public static final int MAX_CELLS = 20;
    public static final int MAX_OFFENSES = 200;

    private final Map<PolityId, Map<ActorRef, PoliceRank>> ranks = new LinkedHashMap<>();
    private final Map<PolityId, List<Offense>> offenses = new LinkedHashMap<>();
    private final Map<PolityId, Integer> cells = new LinkedHashMap<>();
    private final Map<PolityId, Map<ActorRef, Detention>> detained = new LinkedHashMap<>();
    private final Map<ActorRef, Day> cuffed = new LinkedHashMap<>();
    private final Map<Integer, CaseLink> cases = new LinkedHashMap<>();

    public record Snapshot(Map<PolityId, Map<ActorRef, PoliceRank>> ranks, Map<PolityId, List<Offense>> offenses,
                           Map<PolityId, Integer> cells, Map<PolityId, Map<ActorRef, Detention>> detained,
                           Map<ActorRef, Day> cuffed, List<CaseLink> cases) {
    }

    public static PoliceRegistry empty() {
        return new PoliceRegistry();
    }

    public static PoliceRegistry restore(Snapshot s) {
        PoliceRegistry r = new PoliceRegistry();
        s.ranks().forEach((p, m) -> r.ranks.put(p, new LinkedHashMap<>(m)));
        s.offenses().forEach((p, l) -> r.offenses.put(p, new ArrayList<>(l)));
        r.cells.putAll(s.cells());
        s.detained().forEach((p, m) -> r.detained.put(p, new LinkedHashMap<>(m)));
        r.cuffed.putAll(s.cuffed());
        s.cases().forEach(link -> r.cases.put(link.caseNumber(), link));
        return r;
    }

    public Snapshot snapshot() {
        Map<PolityId, Map<ActorRef, PoliceRank>> rk = new LinkedHashMap<>();
        ranks.forEach((p, m) -> rk.put(p, new LinkedHashMap<>(m)));
        Map<PolityId, List<Offense>> of = new LinkedHashMap<>();
        offenses.forEach((p, l) -> of.put(p, List.copyOf(l)));
        Map<PolityId, Map<ActorRef, Detention>> dt = new LinkedHashMap<>();
        detained.forEach((p, m) -> dt.put(p, new LinkedHashMap<>(m)));
        return new Snapshot(rk, of, new LinkedHashMap<>(cells), dt, new LinkedHashMap<>(cuffed),
                List.copyOf(cases.values()));
    }

    // ------------------------------------------------------------------ Dienstgrade

    public Map<ActorRef, PoliceRank> forceOf(PolityId polity) {
        return Map.copyOf(ranks.getOrDefault(polity, Map.of()));
    }

    public Optional<PoliceRank> rankOf(PolityId polity, ActorRef member) {
        return Optional.ofNullable(ranks.getOrDefault(polity, Map.of()).get(member));
    }

    /** Setzt den Dienstgrad (Einstellung, Beförderung oder Degradierung). */
    public void appoint(PolityId polity, ActorRef member, PoliceRank rank) {
        Objects.requireNonNull(rank, "rank");
        ranks.computeIfAbsent(polity, k -> new LinkedHashMap<>()).put(Objects.requireNonNull(member, "member"), rank);
    }

    public boolean dismiss(PolityId polity, ActorRef member) {
        Map<ActorRef, PoliceRank> force = ranks.get(polity);
        return force != null && force.remove(member) != null;
    }

    /** Ob {@code superior} laut Befehlskette über {@code subordinate} steht (beide im Dienst, höherer Rang). */
    public boolean commands(PolityId polity, ActorRef superior, ActorRef subordinate) {
        Optional<PoliceRank> a = rankOf(polity, superior);
        Optional<PoliceRank> b = rankOf(polity, subordinate);
        return a.isPresent() && b.isPresent() && a.get().outranks(b.get());
    }

    public int strength(PolityId polity) {
        return ranks.getOrDefault(polity, Map.of()).size();
    }

    public int count(PolityId polity, PoliceRank atLeast) {
        return (int) ranks.getOrDefault(polity, Map.of()).values().stream()
                .filter(r -> r.level() >= atLeast.level()).count();
    }

    // ------------------------------------------------------------------ Anzeigen

    /** Nimmt eine Anzeige auf; die älteste bearbeitete wird verdrängt, wenn die Liste voll ist. */
    public Offense report(PolityId polity, ActorRef suspect, CrimeKind kind, Day on) {
        return report(polity, suspect, kind, on, List.of());
    }

    /** Wie {@link #report(PolityId, ActorRef, CrimeKind, Day)}, mit Zeugen (höchstens {@value Offense#MAX_WITNESSES}). */
    public Offense report(PolityId polity, ActorRef suspect, CrimeKind kind, Day on, List<ActorRef> witnesses) {
        List<Offense> list = offenses.computeIfAbsent(polity, k -> new ArrayList<>());
        int number = list.stream().mapToInt(Offense::number).max().orElse(0) + 1;
        if (list.size() >= MAX_OFFENSES) {
            list.stream().filter(Offense::handled).findFirst().ifPresent(list::remove);
            if (list.size() >= MAX_OFFENSES) {
                list.remove(0);
            }
        }
        Offense offense = new Offense(number, suspect, kind, on, false,
                witnesses.size() > Offense.MAX_WITNESSES ? witnesses.subList(0, Offense.MAX_WITNESSES) : witnesses);
        list.add(offense);
        return offense;
    }

    public List<Offense> offenses(PolityId polity) {
        return List.copyOf(offenses.getOrDefault(polity, List.of()));
    }

    public List<Offense> openOffenses(PolityId polity) {
        return offenses(polity).stream().filter(o -> !o.handled()).toList();
    }

    public Optional<Offense> offense(PolityId polity, int number) {
        return offenses(polity).stream().filter(o -> o.number() == number).findFirst();
    }

    public void handle(PolityId polity, int number) {
        List<Offense> list = offenses.get(polity);
        if (list != null) {
            list.replaceAll(o -> o.number() == number ? o.markHandled() : o);
        }
    }

    // ------------------------------------------------------------------ Verfahren

    public void link(CaseLink link) {
        cases.put(link.caseNumber(), Objects.requireNonNull(link, "link"));
    }

    public Optional<CaseLink> linkOf(PolityId polity, int caseNumber) {
        return Optional.ofNullable(cases.get(caseNumber)).filter(l -> l.polity().equals(polity));
    }

    public List<CaseLink> links() {
        return List.copyOf(cases.values());
    }

    /** Legt die Haftdauer des Urteils fest; false, wenn das Verfahren nicht aus einer Festnahme stammt. */
    public boolean sentence(PolityId polity, int caseNumber, int days) {
        Optional<CaseLink> link = linkOf(polity, caseNumber);
        link.ifPresent(l -> cases.put(caseNumber, l.withSentence(days)));
        return link.isPresent();
    }

    public void prepared(PolityId polity, int caseNumber) {
        linkOf(polity, caseNumber).ifPresent(l -> cases.put(caseNumber, l.markPrepared()));
    }

    /** Beendet die Verknüpfung (Verfahren rechtskräftig). */
    public Optional<CaseLink> unlink(PolityId polity, int caseNumber) {
        Optional<CaseLink> link = linkOf(polity, caseNumber);
        link.ifPresent(l -> cases.remove(caseNumber));
        return link;
    }

    // ------------------------------------------------------------------ Zellen

    public int cells(PolityId polity) {
        return cells.getOrDefault(polity, 0);
    }

    public void setCells(PolityId polity, int count) {
        if (count < 0 || count > MAX_CELLS) {
            throw new IllegalArgumentException("Zellenzahl außerhalb 0.." + MAX_CELLS);
        }
        cells.put(polity, count);
    }

    public int freeCells(PolityId polity) {
        return Math.max(0, cells(polity) - detained.getOrDefault(polity, Map.of()).size());
    }

    public Map<ActorRef, Detention> detainees(PolityId polity) {
        return Map.copyOf(detained.getOrDefault(polity, Map.of()));
    }

    public Optional<PolityId> jailOf(ActorRef person) {
        return detained.entrySet().stream().filter(e -> e.getValue().containsKey(person)).map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Sperrt eine Person in eine freie Zelle, löst Handschellen.
     *
     * @return {@code false}, wenn keine Zelle frei ist oder die Person bereits einsitzt
     */
    public boolean detain(PolityId polity, ActorRef person, Day since, int days, boolean remand) {
        if (days < 1 || freeCells(polity) == 0 || jailOf(person).isPresent()) {
            return false;
        }
        detained.computeIfAbsent(polity, k -> new LinkedHashMap<>())
                .put(person, new Detention(person, since, since.plus(days), remand));
        cuffed.remove(person);
        return true;
    }

    public boolean release(PolityId polity, ActorRef person) {
        Map<ActorRef, Detention> map = detained.get(polity);
        return map != null && map.remove(person) != null;
    }

    /** Entlässt alle, deren Haft bis einschließlich gestern lief; gibt sie zurück. */
    public List<ActorRef> releaseDue(Day today) {
        List<ActorRef> freed = new ArrayList<>();
        for (Map<ActorRef, Detention> map : detained.values()) {
            map.values().removeIf(d -> {
                boolean due = !d.until().isAfter(today);
                if (due) {
                    freed.add(d.person());
                }
                return due;
            });
        }
        return freed;
    }

    // ------------------------------------------------------------------ Handschellen

    public void cuff(ActorRef person, Day today) {
        cuffed.put(Objects.requireNonNull(person, "person"), today.plus(CUFF_DAYS));
    }

    public boolean uncuff(ActorRef person) {
        return cuffed.remove(person) != null;
    }

    /** Gefesselt oder inhaftiert: darf nicht schlagen, abbauen oder kämpfen. */
    public boolean restrained(ActorRef person, Day today) {
        Day until = cuffed.get(person);
        return (until != null && until.isAfter(today)) || jailOf(person).isPresent();
    }

    public void expireCuffs(Day today) {
        cuffed.values().removeIf(until -> !until.isAfter(today));
    }
}
