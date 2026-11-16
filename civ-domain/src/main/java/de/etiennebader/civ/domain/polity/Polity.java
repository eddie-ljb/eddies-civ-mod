package de.etiennebader.civ.domain.polity;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.actor.PolityRef;
import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.law.LawBook;
import de.etiennebader.civ.domain.law.LawView;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.Statute;
import de.etiennebader.civ.domain.law.UnknownStatuteException;
import de.etiennebader.civ.domain.shared.AggregateRoot;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.ProposalId;
import de.etiennebader.civ.domain.shared.StatuteId;
import de.etiennebader.civ.domain.territory.BlockPosition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregat Gemeinwesen: wer dazugehört und welches Recht gilt.
 *
 * <p>Invarianten: Eine Polity hat immer mindestens ein Mitglied, jeder Akteur ist höchstens
 * einmal Mitglied, und eine Polity kann nicht von einer anderen Polity gegründet werden.
 */
public final class Polity extends AggregateRoot<PolityEvent> implements PolityView {

    private final PolityId id;
    private final PolityName name;
    private final PolityKind kind;
    private final String level;
    private SettlementTier tier;
    private final CurrencyId currency;
    private final Day foundedOn;
    private Optional<BlockPosition> seat;
    private final Map<ActorRef, Membership> members = new LinkedHashMap<>();
    private final Map<ProposalId, Proposal> proposals = new LinkedHashMap<>();
    private final Map<Office, OfficeTerm> offices = new EnumMap<>(Office.class);
    private final Map<ElectionId, Election> elections = new LinkedHashMap<>();
    private final LawBook lawBook = new LawBook();
    private Constitution constitution = Constitution.DRAFT;
    private Optional<PartyProfile> party = Optional.empty();
    private Parliament parliament = Parliament.NONE;
    private final List<ElectionResult> results = new ArrayList<>();
    private final List<Promise> promises = new ArrayList<>();
    private final Map<BlockPosition, Poster> posters = new LinkedHashMap<>();
    private final Map<String, Street> streets = new LinkedHashMap<>();

    /** So viele Straßen darf eine Gemeinde höchstens haben. */
    public static final int MAX_STREETS = 100;

    /** So viele Wahlergebnisse bleiben gespeichert, die neuesten. */
    public static final int KEPT_RESULTS = 10;
    /** So viele Wahlversprechen bleiben gespeichert, die neuesten. */
    public static final int KEPT_PROMISES = 40;
    /** So viele Wahlplakate darf eine Gemeinde höchstens haben. */
    public static final int MAX_POSTERS = 200;

    /** Ebene jeder Gemeinde. */
    public static final String MUNICIPAL_LEVEL = "gemeinde";

    private Polity(PolityId id, PolityName name, PolityKind kind, String level, SettlementTier tier,
                   CurrencyId currency, Day foundedOn, Optional<BlockPosition> seat) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.level = requireLevel(kind, level);
        this.tier = Objects.requireNonNull(tier, "tier");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.foundedOn = Objects.requireNonNull(foundedOn, "foundedOn");
        this.seat = Objects.requireNonNull(seat, "seat");
    }

    /**
     * Gründet eine Gemeinde mit ihrem Sitz, dem Standort des Gemeindehauses. Der Gründer wird erster Bürger.
     *
     * @throws IllegalFounderException wenn der Gründer selbst eine Polity ist
     */
    public static Polity found(PolityId id, PolityName name, ActorRef founder, CurrencyId currency, BlockPosition seat,
                               Day day) {
        Objects.requireNonNull(founder, "founder");
        Objects.requireNonNull(seat, "seat");
        if (founder instanceof PolityRef) {
            throw new IllegalFounderException(founder);
        }
        Polity polity = new Polity(id, name, PolityKind.TERRITORIAL, MUNICIPAL_LEVEL, SettlementTier.MUNICIPALITY,
                currency, day, Optional.of(seat));
        polity.members.put(founder, new Membership(founder, MembershipType.CITIZEN, day));
        polity.raise(new PolityFounded(id, name, founder, seat, day));
        return polity;
    }

    /**
     * Gründet einen Bund auf einer höheren Ebene mit seiner ersten Gliedkörperschaft. Ein Bund hat kein eigenes Gebiet
     * und keinen Sitz; seine Bürger sind die Bürger seiner Glieder. Wer ihn gründet, führt ihn zunächst.
     *
     * @param level Schlüssel der Ebene, z. B. {@code land}; nicht {@link #MUNICIPAL_LEVEL}
     * @throws IllegalFounderException wenn der Gründer selbst eine Polity ist
     */
    public static Polity foundFederation(PolityId id, PolityName name, String level, PolityRef constituent,
                                         ActorRef head, CurrencyId currency, Day day) {
        Objects.requireNonNull(constituent, "constituent");
        Objects.requireNonNull(head, "head");
        if (head instanceof PolityRef) {
            throw new IllegalFounderException(head);
        }
        if (constituent.id().equals(id)) {
            throw new IllegalArgumentException("Ein Bund kann nicht Glied seiner selbst sein");
        }
        Polity polity = new Polity(id, name, PolityKind.FEDERATION, level, SettlementTier.MUNICIPALITY, currency, day,
                Optional.empty());
        polity.members.put(head, new Membership(head, MembershipType.CITIZEN, day));
        polity.members.put(constituent, new Membership(constituent, MembershipType.CONSTITUENT, day));
        polity.raise(new FederationFounded(id, name, polity.level, constituent, head, day));
        return polity;
    }

    /** Ebenen-Schlüssel aller Parteien. */
    public static final String PARTY_LEVEL = "partei";

    /**
     * Gründet eine Partei. Der Gründer wird erstes Mitglied und Vorsitz.
     *
     * @throws IllegalFounderException wenn der Gründer selbst eine Polity ist
     */
    public static Polity foundParty(PolityId id, PolityName name, ActorRef founder, PartyProfile profile,
                                    CurrencyId currency, Day day) {
        Objects.requireNonNull(founder, "founder");
        Objects.requireNonNull(profile, "profile");
        if (founder instanceof PolityRef) {
            throw new IllegalFounderException(founder);
        }
        Polity polity = new Polity(id, name, PolityKind.PARTY, PARTY_LEVEL, SettlementTier.MUNICIPALITY, currency, day,
                Optional.empty());
        polity.party = Optional.of(profile);
        polity.members.put(founder, new Membership(founder, MembershipType.CITIZEN, day));
        polity.offices.put(Office.MAYOR, new OfficeTerm(Office.MAYOR, founder, day, Optional.empty()));
        polity.raise(new PartyFounded(id, name, founder, profile, day));
        return polity;
    }

    /** Ebenen-Schlüssel aller Firmen. */
    public static final String COMPANY_LEVEL = "firma";

    /** Ebenen-Schlüssel aller Zeitungen. */
    public static final String NEWSPAPER_LEVEL = "zeitung";

    /**
     * Gründet eine Firma oder Zeitung. Der Gründer wird erstes Mitglied und Leitung.
     *
     * @param kind {@link PolityKind#COMPANY} oder {@link PolityKind#NEWSPAPER}
     * @throws IllegalFounderException wenn der Gründer selbst eine Polity ist
     */
    public static Polity foundOrganisation(PolityId id, PolityName name, PolityKind kind, ActorRef founder,
                                           CurrencyId currency, Day day) {
        Objects.requireNonNull(founder, "founder");
        if (kind != PolityKind.COMPANY && kind != PolityKind.NEWSPAPER) {
            throw new IllegalArgumentException("Keine Organisation: " + kind);
        }
        if (founder instanceof PolityRef) {
            throw new IllegalFounderException(founder);
        }
        Polity polity = new Polity(id, name, kind, kind == PolityKind.COMPANY ? COMPANY_LEVEL : NEWSPAPER_LEVEL,
                SettlementTier.MUNICIPALITY, currency, day, Optional.empty());
        polity.members.put(founder, new Membership(founder, MembershipType.CITIZEN, day));
        polity.offices.put(Office.MAYOR, new OfficeTerm(Office.MAYOR, founder, day, Optional.empty()));
        polity.raise(new OrganisationFounded(id, name, kind, founder, day));
        return polity;
    }

    /** Ändert Richtung oder Wappen einer Partei. */
    public void changePartyProfile(PartyProfile profile, Day day) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(day, "day");
        if (kind != PolityKind.PARTY) {
            throw new IllegalStateException("Keine Partei: " + id);
        }
        party = Optional.of(profile);
        raise(new PartyProfileChanged(id, profile, day));
    }

    @Override
    public Optional<PartyProfile> partyProfile() {
        return party;
    }

    private static String requireLevel(PolityKind kind, String level) {
        Objects.requireNonNull(level, "level");
        if (!level.matches("[a-z][a-z0-9_]{0,31}")) {
            throw new IllegalArgumentException("Ungültiger Ebenen-Schlüssel: " + level);
        }
        if ((kind == PolityKind.COMPANY) != level.equals(COMPANY_LEVEL)
                || (kind == PolityKind.NEWSPAPER) != level.equals(NEWSPAPER_LEVEL)) {
            throw new IllegalArgumentException("Ebene " + level + " passt nicht zu " + kind);
        }
        if ((kind == PolityKind.PARTY) != level.equals(PARTY_LEVEL)) {
            throw new IllegalArgumentException("Ebene " + level + " passt nicht zu " + kind);
        }
        if ((kind == PolityKind.TERRITORIAL) != level.equals(MUNICIPAL_LEVEL)) {
            throw new IllegalArgumentException("Ebene " + level + " passt nicht zu " + kind);
        }
        return level;
    }

    /**
     * Stellt eine gespeicherte Polity wieder her, ohne Events auszulösen. Mitglieder und Gesetze
     * werden in der übergebenen Reihenfolge übernommen; Gesetze in der Reihenfolge ihres Erlasses.
     *
     * @param seat leer bei Polities aus der Zeit, bevor es Gemeindehäuser gab
     * @throws IllegalArgumentException wenn die Daten keine gültige Polity ergeben
     */
    public static Polity restore(PolityId id, PolityName name, PolityKind kind, SettlementTier tier, CurrencyId currency,
                                 Day foundedOn, Optional<BlockPosition> seat, List<Membership> members,
                                 List<EnactedStatute> statutes) {
        return restore(id, name, kind, tier, currency, foundedOn, seat, members, statutes, List.of());
    }

    /**
     * Wie oben, zusätzlich mit offenen Abstimmungen.
     *
     * @throws IllegalArgumentException wenn die Daten keine gültige Polity ergeben
     */
    public static Polity restore(PolityId id, PolityName name, PolityKind kind, SettlementTier tier, CurrencyId currency,
                                 Day foundedOn, Optional<BlockPosition> seat, List<Membership> members,
                                 List<EnactedStatute> statutes, List<Proposal> proposals) {
        return restore(id, name, kind, tier, currency, foundedOn, seat, members, statutes, proposals, List.of(),
                List.of());
    }

    /**
     * Wie oben, zusätzlich mit Amtsinhabern und laufenden Wahlen.
     *
     * @throws IllegalArgumentException wenn die Daten keine gültige Polity ergeben, z. B. ein Amtsinhaber kein Mitglied
     *                                  ist oder ein Amt doppelt besetzt
     */
    public static Polity restore(PolityId id, PolityName name, PolityKind kind, SettlementTier tier, CurrencyId currency,
                                 Day foundedOn, Optional<BlockPosition> seat, List<Membership> members,
                                 List<EnactedStatute> statutes, List<Proposal> proposals, List<OfficeTerm> offices,
                                 List<Election> elections) {
        return restore(id, name, kind, kind == PolityKind.TERRITORIAL ? MUNICIPAL_LEVEL : "land", tier, currency,
                foundedOn, seat, members, statutes, proposals, offices, elections);
    }

    /**
     * Wie oben, zusätzlich mit der Ebene.
     *
     * @throws IllegalArgumentException wenn die Daten keine gültige Polity ergeben
     */
    public static Polity restore(PolityId id, PolityName name, PolityKind kind, String level, SettlementTier tier,
                                 CurrencyId currency, Day foundedOn, Optional<BlockPosition> seat,
                                 List<Membership> members, List<EnactedStatute> statutes, List<Proposal> proposals,
                                 List<OfficeTerm> offices, List<Election> elections) {
        return restore(id, name, kind, level, tier, currency, foundedOn, seat, members, statutes, proposals, offices,
                elections, Constitution.DRAFT);
    }

    /**
     * Wie oben, zusätzlich mit der Verfassung.
     *
     * @throws IllegalArgumentException wenn die Daten keine gültige Polity ergeben
     */
    public static Polity restore(PolityId id, PolityName name, PolityKind kind, String level, SettlementTier tier,
                                 CurrencyId currency, Day foundedOn, Optional<BlockPosition> seat,
                                 List<Membership> members, List<EnactedStatute> statutes, List<Proposal> proposals,
                                 List<OfficeTerm> offices, List<Election> elections, Constitution constitution) {
        return restore(id, name, kind, level, tier, currency, foundedOn, seat, members, statutes, proposals, offices,
                elections, constitution, Optional.empty());
    }

    /**
     * Wie oben, zusätzlich mit Richtung und Wappen einer Partei.
     *
     * @throws IllegalArgumentException wenn die Daten keine gültige Polity ergeben, z. B. eine Partei ohne Richtung
     */
    public static Polity restore(PolityId id, PolityName name, PolityKind kind, String level, SettlementTier tier,
                                 CurrencyId currency, Day foundedOn, Optional<BlockPosition> seat,
                                 List<Membership> members, List<EnactedStatute> statutes, List<Proposal> proposals,
                                 List<OfficeTerm> offices, List<Election> elections, Constitution constitution,
                                 Optional<PartyProfile> party) {
        return restore(id, name, kind, level, tier, currency, foundedOn, seat, members, statutes, proposals, offices,
                elections, constitution, party, Parliament.NONE);
    }

    /**
     * Wie oben, zusätzlich mit dem Parlament.
     *
     * @throws IllegalArgumentException wenn die Daten keine gültige Polity ergeben, z. B. ein Abgeordneter kein Mitglied
     *                                  ist
     */
    public static Polity restore(PolityId id, PolityName name, PolityKind kind, String level, SettlementTier tier,
                                 CurrencyId currency, Day foundedOn, Optional<BlockPosition> seat,
                                 List<Membership> members, List<EnactedStatute> statutes, List<Proposal> proposals,
                                 List<OfficeTerm> offices, List<Election> elections, Constitution constitution,
                                 Optional<PartyProfile> party, Parliament parliament) {
        return restore(id, name, kind, level, tier, currency, foundedOn, seat, members, statutes, proposals, offices,
                elections, constitution, party, parliament, List.of(), List.of(), List.of());
    }

    /**
     * Wie oben, zusätzlich mit Wahlergebnissen, Wahlversprechen und Wahlplakaten.
     *
     * @throws IllegalArgumentException wenn die Daten keine gültige Polity ergeben
     */
    public static Polity restore(PolityId id, PolityName name, PolityKind kind, String level, SettlementTier tier,
                                 CurrencyId currency, Day foundedOn, Optional<BlockPosition> seat,
                                 List<Membership> members, List<EnactedStatute> statutes, List<Proposal> proposals,
                                 List<OfficeTerm> offices, List<Election> elections, Constitution constitution,
                                 Optional<PartyProfile> party, Parliament parliament, List<ElectionResult> results,
                                 List<Promise> promises, List<Poster> posters) {
        return restore(id, name, kind, level, tier, currency, foundedOn, seat, members, statutes, proposals, offices,
                elections, constitution, party, parliament, results, promises, posters, List.of());
    }

    /**
     * Wie oben, zusätzlich mit Straßen.
     *
     * @throws IllegalArgumentException wenn die Daten keine gültige Polity ergeben
     */
    public static Polity restore(PolityId id, PolityName name, PolityKind kind, String level, SettlementTier tier,
                                 CurrencyId currency, Day foundedOn, Optional<BlockPosition> seat,
                                 List<Membership> members, List<EnactedStatute> statutes, List<Proposal> proposals,
                                 List<OfficeTerm> offices, List<Election> elections, Constitution constitution,
                                 Optional<PartyProfile> party, Parliament parliament, List<ElectionResult> results,
                                 List<Promise> promises, List<Poster> posters, List<Street> streets) {
        Objects.requireNonNull(streets, "streets");
        Objects.requireNonNull(party, "party");
        Objects.requireNonNull(parliament, "parliament");
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(promises, "promises");
        Objects.requireNonNull(posters, "posters");
        if ((kind == PolityKind.PARTY) != party.isPresent()) {
            throw new IllegalArgumentException("Parteiprofil passt nicht zu " + kind + ": " + id);
        }
        Objects.requireNonNull(offices, "offices");
        Objects.requireNonNull(constitution, "constitution");
        Objects.requireNonNull(elections, "elections");
        Polity polity = restoreCore(id, name, kind, level, tier, currency, foundedOn, seat, members, statutes,
                proposals);
        for (OfficeTerm term : offices) {
            if (!term.office().single()) {
                throw new IllegalArgumentException("Kein Einzelamt in " + id + ": " + term.office());
            }
            if (!polity.isMember(term.holder())) {
                throw new IllegalArgumentException("Amtsinhaber ist kein Mitglied von " + id + ": " + term.holder());
            }
            if (polity.offices.putIfAbsent(term.office(), term) != null) {
                throw new IllegalArgumentException("Amt doppelt besetzt in " + id + ": " + term.office());
            }
        }
        for (Election election : elections) {
            if (polity.elections.values().stream().anyMatch(open -> open.office() == election.office())) {
                throw new IllegalArgumentException("Zwei Wahlen für dasselbe Amt in " + id + ": " + election.office());
            }
            polity.elections.put(election.id(), election);
        }
        for (Parliament.Seat member : parliament.seats()) {
            if (!polity.isMember(member.member())) {
                throw new IllegalArgumentException("Abgeordneter ist kein Mitglied von " + id + ": " + member.member());
            }
        }
        polity.constitution = constitution;
        polity.party = party;
        polity.parliament = parliament;
        polity.results.addAll(results);
        polity.promises.addAll(promises);
        posters.forEach(poster -> polity.posters.put(poster.position(), poster));
        streets.forEach(street -> polity.streets.put(key(street.name()), street));
        return polity;
    }

    private static Polity restoreCore(PolityId id, PolityName name, PolityKind kind, String level,
                                      SettlementTier tier, CurrencyId currency, Day foundedOn,
                                      Optional<BlockPosition> seat, List<Membership> members,
                                      List<EnactedStatute> statutes, List<Proposal> proposals) {
        Objects.requireNonNull(members, "members");
        Objects.requireNonNull(proposals, "proposals");
        Objects.requireNonNull(statutes, "statutes");
        Polity polity = new Polity(id, name, kind, level, tier, currency, foundedOn, seat);
        if (members.isEmpty()) {
            throw new IllegalArgumentException("Gespeicherte Polity ohne Mitglieder: " + id);
        }
        for (Membership membership : members) {
            if (membership.member().equals(polity.asActor())) {
                throw new IllegalArgumentException("Polity ist Mitglied ihrer selbst: " + id);
            }
            if (polity.members.putIfAbsent(membership.member(), membership) != null) {
                throw new IllegalArgumentException("Doppelte Mitgliedschaft in " + id + ": " + membership.member());
            }
        }
        statutes.forEach(enacted -> polity.lawBook.enact(enacted.statute(), enacted.enactedOn()));
        proposals.forEach(proposal -> polity.proposals.put(proposal.id(), proposal));
        return polity;
    }

    /**
     * Nimmt einen Akteur als Mitglied auf.
     *
     * @throws AlreadyMemberException wenn er bereits Mitglied ist
     */
    public void admit(ActorRef member, MembershipType type, Day day) {
        Objects.requireNonNull(member, "member");
        if (member.equals(asActor())) {
            throw new IllegalArgumentException("Eine Polity kann nicht Mitglied ihrer selbst sein");
        }
        if (isMember(member)) {
            throw new AlreadyMemberException(member);
        }
        members.put(member, new Membership(member, type, day));
        raise(new MemberAdmitted(id, member, type, day));
    }

    /**
     * Ein Mitglied tritt aus.
     *
     * @throws NotAMemberException wenn der Akteur kein Mitglied ist
     * @throws LastMemberException wenn er das letzte Mitglied ist
     */
    public void leave(ActorRef member, Day day) {
        Objects.requireNonNull(day, "day");
        if (!isMember(member)) {
            throw new NotAMemberException(member);
        }
        if (members.size() == 1) {
            throw new LastMemberException(member);
        }
        members.remove(member);
        raise(new MemberLeft(id, member, day));
        for (OfficeTerm term : List.copyOf(offices.values())) {
            if (term.holder().equals(member)) {
                vacateOffice(term.office(), day);
            }
        }
        elections.replaceAll((electionId, election) -> election.without(member));
        parliament = parliament.without(member, this::isMember);
        promises.removeIf(promise -> promise.candidate().equals(member));
    }

    /**
     * Besetzt ein Amt, durch Wahl oder Ernennung. Ein bisheriger Inhaber verliert es.
     *
     * @param until letzter Tag der Amtszeit; leer für unbefristet
     * @throws NotAMemberException wenn der neue Inhaber kein Mitglied ist
     */
    public void fillOffice(Office office, ActorRef holder, Optional<Day> until, Day day) {
        Objects.requireNonNull(office, "office");
        Objects.requireNonNull(day, "day");
        if (!office.single()) {
            throw new IllegalArgumentException("Kein Einzelamt: " + office);
        }
        if (!isMember(holder)) {
            throw new NotAMemberException(holder);
        }
        offices.put(office, new OfficeTerm(office, holder, day, until));
        raise(new OfficeFilled(id, office, holder, day));
    }

    /**
     * Macht ein Amt frei.
     *
     * @return ob das Amt besetzt war
     */
    public boolean vacateOffice(Office office, Day day) {
        Objects.requireNonNull(office, "office");
        Objects.requireNonNull(day, "day");
        OfficeTerm previous = offices.remove(office);
        if (previous == null) {
            return false;
        }
        raise(new OfficeVacated(id, office, previous.holder(), day));
        return true;
    }

    /**
     * Ruft eine Wahl für ein Amt aus.
     *
     * @param closesOn an dessen Ende wird ausgezählt
     * @throws ElectionAlreadyOpenException wenn für das Amt schon gewählt wird
     */
    public void callElection(ElectionId electionId, Office office, Day closesOn, Day day) {
        Objects.requireNonNull(electionId, "electionId");
        Objects.requireNonNull(office, "office");
        if (elections.containsKey(electionId)) {
            throw new IllegalArgumentException("Wahl existiert bereits: " + electionId);
        }
        if (electionFor(office).isPresent()) {
            throw new ElectionAlreadyOpenException(office);
        }
        elections.put(electionId, new Election(electionId, office, day, closesOn, List.of(), List.of()));
        raise(new ElectionCalled(id, electionId, office, closesOn, day));
    }

    /**
     * Ein Mitglied kandidiert. Ob es dafür Bürger sein muss, entscheidet der Aufrufer.
     *
     * @throws UnknownElectionException   wenn es die Wahl nicht gibt
     * @throws NotAMemberException        wenn der Kandidat kein Mitglied ist
     * @throws AlreadyCandidateException  wenn er schon kandidiert
     */
    public void standForElection(ElectionId electionId, ActorRef candidate, Day day) {
        standForElection(electionId, candidate, Platform.NONE, day);
    }

    /**
     * Wie oben, mit Wahlprogramm.
     */
    public void standForElection(ElectionId electionId, ActorRef candidate, Platform platform, Day day) {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(day, "day");
        Election election = requireElection(electionId);
        if (!isMember(candidate)) {
            throw new NotAMemberException(candidate);
        }
        if (election.isCandidate(candidate)) {
            throw new AlreadyCandidateException(candidate);
        }
        elections.put(electionId, election.withCandidate(candidate, platform));
        raise(new CandidacyDeclared(id, electionId, candidate, platform, day));
    }

    /**
     * Gibt eine Stimme ab oder ändert sie.
     *
     * @throws UnknownElectionException wenn es die Wahl nicht gibt
     * @throws NotAMemberException      wenn der Wähler kein Mitglied ist
     * @throws NotACandidateException   wenn der Gewählte nicht kandidiert
     */
    public void castBallot(ElectionId electionId, ActorRef voter, ActorRef candidate, Day day) {
        Objects.requireNonNull(day, "day");
        Election election = requireElection(electionId);
        if (!isMember(voter)) {
            throw new NotAMemberException(voter);
        }
        if (!election.isCandidate(candidate)) {
            throw new NotACandidateException(candidate);
        }
        elections.put(electionId, election.withBallot(new Ballot(voter, candidate)));
        raise(new BallotCast(id, electionId, voter, day));
    }

    /**
     * Beendet eine Wahl. Den Gewinner ermittelt der Aufrufer; er erhält das Amt.
     *
     * @param winner leer, wenn niemand kandidiert hat; das Amt bleibt dann, wie es ist
     * @param until  letzter Tag der neuen Amtszeit; leer für unbefristet
     * @return die beendete Wahl
     * @throws UnknownElectionException wenn es die Wahl nicht gibt
     * @throws NotACandidateException   wenn der Gewinner nicht kandidiert hat
     */
    public Election closeElection(ElectionId electionId, Optional<ActorRef> winner, Optional<Day> until, Day day) {
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(day, "day");
        Election election = requireElection(electionId);
        if (!election.office().single()) {
            throw new IllegalArgumentException("Parlamentswahlen enden mit seatParliament: " + electionId);
        }
        if (winner.isPresent() && !election.isCandidate(winner.get())) {
            throw new NotACandidateException(winner.get());
        }
        elections.remove(electionId);
        raise(new ElectionClosed(id, electionId, election.office(), winner.isPresent(), day));
        winner.ifPresent(elected -> fillOffice(election.office(), elected, until, day));
        return election;
    }

    /**
     * Beendet eine Parlamentswahl und setzt das neue Parlament ein. Die Sitzverteilung ermittelt der Aufrufer. Ohne Sitze
     * (niemand hat kandidiert oder Stimmen bekommen) bleibt das bisherige Parlament.
     *
     * @param seats Sitze in Reihenfolge, das Präsidium zuerst; jeder Abgeordnete muss kandidiert haben
     * @param until letzter Tag der Wahlperiode; leer für unbefristet
     * @return die beendete Wahl
     * @throws UnknownElectionException wenn es die Wahl nicht gibt
     * @throws NotACandidateException   wenn ein Abgeordneter nicht kandidiert hat
     */
    public Election seatParliament(ElectionId electionId, List<Parliament.Seat> seats, Optional<Day> until, Day day) {
        return seatParliament(electionId, seats, List.of(), until, day);
    }

    /**
     * Wie oben, mit Nachrückern aus den Parteilisten.
     *
     * @param reserves nicht gewählte Kandidaten mit Partei, in der Reihenfolge, in der sie nachrücken
     */
    public Election seatParliament(ElectionId electionId, List<Parliament.Seat> seats, List<Parliament.Seat> reserves,
                                   Optional<Day> until, Day day) {
        Objects.requireNonNull(seats, "seats");
        Objects.requireNonNull(reserves, "reserves");
        Objects.requireNonNull(until, "until");
        Objects.requireNonNull(day, "day");
        Election election = requireElection(electionId);
        if (election.office() != Office.PARLIAMENT) {
            throw new IllegalArgumentException("Keine Parlamentswahl: " + electionId);
        }
        for (Parliament.Seat member : seats) {
            if (!election.isCandidate(member.member())) {
                throw new NotACandidateException(member.member());
            }
        }
        elections.remove(electionId);
        raise(new ElectionClosed(id, electionId, Office.PARLIAMENT, !seats.isEmpty(), day));
        if (!seats.isEmpty()) {
            parliament = new Parliament(seats, Optional.of(day), until, reserves);
            raise(new ParliamentElected(id, electionId, seats.size(), day));
        }
        return election;
    }

    /** Verlängert die Wahlperiode, z. B. wenn bei der Neuwahl niemand kandidiert hat. */
    public void extendParliament(Optional<Day> until, Day day) {
        Objects.requireNonNull(until, "until");
        Objects.requireNonNull(day, "day");
        if (parliament.seated()) {
            parliament = parliament.extendedUntil(until);
        }
    }

    /**
     * Löst das Parlament auf, z. B. weil das Recht keine Sitze mehr vorsieht.
     *
     * @return ob es ein Parlament gab
     */
    public boolean dissolveParliament(Day day) {
        Objects.requireNonNull(day, "day");
        if (!parliament.seated()) {
            return false;
        }
        parliament = Parliament.NONE;
        raise(new ParliamentDissolved(id, day));
        return true;
    }

    @Override
    public Parliament parliament() {
        return parliament;
    }

    /** Hält das Ergebnis einer beendeten Wahl fest; ältere Ergebnisse über {@link #KEPT_RESULTS} fallen weg. */
    public void recordResult(ElectionResult result) {
        Objects.requireNonNull(result, "result");
        results.add(result);
        while (results.size() > KEPT_RESULTS) {
            results.removeFirst();
        }
    }

    @Override
    public List<ElectionResult> results() {
        return List.copyOf(results);
    }

    /**
     * Ein Kandidat gibt ein Wahlversprechen ab.
     *
     * @throws NotACandidateException wenn er bei keiner laufenden Wahl für dieses Amt kandidiert
     */
    public void promise(Promise promise) {
        Objects.requireNonNull(promise, "promise");
        boolean candidate = electionFor(promise.office()).map(open -> open.isCandidate(promise.candidate()))
                .orElse(false);
        if (!candidate) {
            throw new NotACandidateException(promise.candidate());
        }
        promises.add(promise);
        while (promises.size() > KEPT_PROMISES) {
            promises.removeFirst();
        }
    }

    @Override
    public List<Promise> promises() {
        return List.copyOf(promises);
    }

    /**
     * Stellt ein Wahlplakat auf; ein Plakat am selben Ort wird ersetzt.
     *
     * @return ob es aufgestellt wurde; nicht, wenn schon {@link #MAX_POSTERS} stehen
     */
    public boolean putUpPoster(Poster poster) {
        Objects.requireNonNull(poster, "poster");
        if (!posters.containsKey(poster.position()) && posters.size() >= MAX_POSTERS) {
            return false;
        }
        posters.put(poster.position(), poster);
        return true;
    }

    /** Entfernt das Plakat an dieser Stelle. @return ob dort eines stand */
    public boolean takeDownPoster(BlockPosition position) {
        return posters.remove(Objects.requireNonNull(position, "position")) != null;
    }

    @Override
    public List<Poster> posters() {
        return List.copyOf(posters.values());
    }

    /**
     * Legt eine Straße an oder vermisst eine gleichnamige neu.
     *
     * @return ob es eine neue Straße ist; nicht, wenn schon {@link #MAX_STREETS} bestehen und sie neu wäre
     */
    public boolean layStreet(Street street, Day day) {
        Objects.requireNonNull(street, "street");
        Objects.requireNonNull(day, "day");
        boolean known = streets.containsKey(key(street.name()));
        if (!known && streets.size() >= MAX_STREETS) {
            return false;
        }
        streets.put(key(street.name()), street);
        raise(new StreetLaid(id, street, day));
        return !known;
    }

    /** Hebt eine Straße auf. @return ob es sie gab */
    public boolean removeStreet(String name, Day day) {
        Objects.requireNonNull(day, "day");
        Street removed = streets.remove(key(name));
        if (removed == null) {
            return false;
        }
        raise(new StreetRemoved(id, removed.name(), day));
        return true;
    }

    @Override
    public List<Street> streets() {
        return List.copyOf(streets.values());
    }

    private static String key(String streetName) {
        return Objects.requireNonNull(streetName, "streetName").strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public Optional<OfficeTerm> officeTerm(Office office) {
        return Optional.ofNullable(offices.get(Objects.requireNonNull(office, "office")));
    }

    @Override
    public Collection<OfficeTerm> offices() {
        return List.copyOf(offices.values());
    }

    @Override
    public Collection<Election> elections() {
        return List.copyOf(elections.values());
    }

    @Override
    public Optional<Election> electionFor(Office office) {
        Objects.requireNonNull(office, "office");
        return elections.values().stream().filter(election -> election.office() == office).findFirst();
    }

    private Election requireElection(ElectionId electionId) {
        Objects.requireNonNull(electionId, "electionId");
        Election election = elections.get(electionId);
        if (election == null) {
            throw new UnknownElectionException(electionId);
        }
        return election;
    }

    /**
     * Eröffnet eine Abstimmung über eine Regelung.
     *
     * @throws NotAMemberException         wenn der Antragsteller kein Mitglied ist
     * @throws ProposalAlreadyOpenException wenn über diese Regelung schon abgestimmt wird
     */
    public void openProposal(ProposalId proposalId, ParameterValue<?> change, ActorRef proposer, Day day) {
        Objects.requireNonNull(change, "change");
        openProposal(proposalId, new Motion.Change(change), proposer, day);
    }

    /**
     * Eröffnet eine Abstimmung über eine Regelung, einen neuen Paragraphen oder eine Aufhebung.
     *
     * @throws NotAMemberException         wenn der Antragsteller kein Mitglied ist
     * @throws ProposalAlreadyOpenException wenn über denselben Gegenstand schon abgestimmt wird
     * @throws UnknownStatuteException     wenn ein aufzuhebender Paragraph nicht gilt
     */
    public void openProposal(ProposalId proposalId, Motion motion, ActorRef proposer, Day day) {
        Objects.requireNonNull(proposalId, "proposalId");
        Objects.requireNonNull(motion, "motion");
        Objects.requireNonNull(day, "day");
        if (!isMember(proposer)) {
            throw new NotAMemberException(proposer);
        }
        if (proposals.containsKey(proposalId)) {
            throw new IllegalArgumentException("Abstimmung existiert bereits: " + proposalId);
        }
        if (motion instanceof Motion.Repeal repeal && !lawBook.isInForce(repeal.statute())) {
            throw new UnknownStatuteException(repeal.statute());
        }
        String subject = motion.subject();
        if (proposals.values().stream().anyMatch(open -> open.motion().subject().equals(subject))) {
            throw new ProposalAlreadyOpenException(subject);
        }
        proposals.put(proposalId, new Proposal(proposalId, motion, proposer, day, List.of()));
        raise(new ProposalOpened(id, proposalId, subject, proposer, day));
    }

    /**
     * Gibt eine Stimme ab oder ändert sie.
     *
     * @throws UnknownProposalException wenn es die Abstimmung nicht gibt
     * @throws NotAMemberException      wenn der Wähler kein Mitglied ist
     */
    public void vote(ProposalId proposalId, ActorRef voter, boolean inFavour, Day day) {
        Objects.requireNonNull(day, "day");
        Proposal proposal = requireProposal(proposalId);
        if (!isMember(voter)) {
            throw new NotAMemberException(voter);
        }
        proposals.put(proposalId, proposal.with(new Vote(voter, inFavour)));
        raise(new VoteCast(id, proposalId, voter, inFavour, day));
    }

    /**
     * Beendet eine Abstimmung. Ob die Änderung dann gilt, entscheidet der Aufrufer.
     *
     * @return die beendete Abstimmung
     * @throws UnknownProposalException wenn es die Abstimmung nicht gibt
     */
    public Proposal closeProposal(ProposalId proposalId, boolean accepted, Day day) {
        Objects.requireNonNull(day, "day");
        Proposal proposal = requireProposal(proposalId);
        proposals.remove(proposalId);
        raise(new ProposalClosed(id, proposalId, accepted, day));
        return proposal;
    }

    @Override
    public Collection<Proposal> proposals() {
        return List.copyOf(proposals.values());
    }

    private Proposal requireProposal(ProposalId proposalId) {
        Objects.requireNonNull(proposalId, "proposalId");
        Proposal proposal = proposals.get(proposalId);
        if (proposal == null) {
            throw new UnknownProposalException(proposalId);
        }
        return proposal;
    }

    /**
     * Ruft die Verfassung aus. Danach steht die Präambel fest, und Verfassungsartikel ändern sich nur durch Abstimmung.
     *
     * @throws ConstitutionAlreadyProclaimedException wenn sie schon ausgerufen ist
     */
    public void proclaimConstitution(Day day) {
        Objects.requireNonNull(day, "day");
        if (constitution.proclaimed()) {
            throw new ConstitutionAlreadyProclaimedException(name);
        }
        constitution = constitution.proclaim(day);
        raise(new ConstitutionProclaimed(id, day));
    }

    /**
     * Setzt die Präambel des Entwurfs.
     *
     * @throws ConstitutionAlreadyProclaimedException wenn die Verfassung schon ausgerufen ist
     * @throws PreambleTooLongException               bei mehr als {@link Constitution#MAX_PREAMBLE} Zeichen
     */
    public void changePreamble(String text, Day day) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(day, "day");
        if (constitution.proclaimed()) {
            throw new ConstitutionAlreadyProclaimedException(name);
        }
        String preamble = text.strip();
        if (preamble.length() > Constitution.MAX_PREAMBLE) {
            throw new PreambleTooLongException(preamble.length());
        }
        constitution = constitution.withPreamble(preamble);
        raise(new PreambleChanged(id, preamble, day));
    }

    @Override
    public Constitution constitution() {
        return constitution;
    }

    /** Verlegt den Sitz, z. B. weil das Gemeindehaus an anderer Stelle neu errichtet wurde. */
    public void moveSeat(BlockPosition newSeat, Day day) {
        Objects.requireNonNull(newSeat, "newSeat");
        Objects.requireNonNull(day, "day");
        BlockPosition previous = seat.orElse(null);
        seat = Optional.of(newSeat);
        raise(new SeatMoved(id, Optional.ofNullable(previous), newSeat, day));
    }

    /** Löst die Polity auf. Gebiet, Grundstücke und Kasse räumt der Aufrufer vorher ab. */
    public void dissolve(Day day) {
        Objects.requireNonNull(day, "day");
        raise(new PolityDissolved(id, name, day));
    }

    /**
     * Stuft die Siedlung auf- oder ab, z. B. nach ihrer Einwohnerzahl.
     *
     * @return ob sich die Stufe geändert hat
     */
    public boolean changeTier(SettlementTier newTier, Day day) {
        Objects.requireNonNull(newTier, "newTier");
        Objects.requireNonNull(day, "day");
        if (newTier == tier) {
            return false;
        }
        SettlementTier previous = tier;
        tier = newTier;
        raise(new TierChanged(id, previous, newTier, day));
        return true;
    }

    /**
     * Erlässt ein Gesetz. In Phase 0 ein direkter Aufruf; Abstimmungen und Organe
     * schieben sich später als Use Case davor.
     */
    public void enact(Statute statute, Day day) {
        lawBook.enact(statute, day);
        raise(new StatuteEnacted(id, statute.id(), day));
    }

    /**
     * Hebt ein geltendes Gesetz auf.
     *
     * @throws de.etiennebader.civ.domain.law.UnknownStatuteException wenn es nicht gilt
     */
    public void repeal(StatuteId statuteId, Day day) {
        Objects.requireNonNull(day, "day");
        lawBook.repeal(statuteId);
        raise(new StatuteRepealed(id, statuteId, day));
    }

    @Override
    public boolean isMember(ActorRef actor) {
        return members.containsKey(Objects.requireNonNull(actor, "actor"));
    }

    @Override
    public Optional<Membership> membershipOf(ActorRef actor) {
        return Optional.ofNullable(members.get(Objects.requireNonNull(actor, "actor")));
    }

    /** Alle Mitgliedschaften in Beitrittsreihenfolge, nur lesend. */
    @Override
    public Collection<Membership> members() {
        return List.copyOf(members.values());
    }

    @Override
    public int memberCount() {
        return members.size();
    }

    /** Das geltende Recht, nur lesend. Änderungen laufen über {@link #enact} und {@link #repeal}. */
    @Override
    public LawView laws() {
        return lawBook;
    }

    @Override
    public Optional<BlockPosition> seat() {
        return seat;
    }

    /** Die Polity als handelnder Akteur, z. B. als Kontoinhaberin oder Grundbesitzerin. */
    @Override
    public PolityRef asActor() {
        return new PolityRef(id);
    }

    @Override
    public PolityId id() {
        return id;
    }

    @Override
    public PolityName name() {
        return name;
    }

    @Override
    public PolityKind kind() {
        return kind;
    }

    @Override
    public String level() {
        return level;
    }

    @Override
    public SettlementTier tier() {
        return tier;
    }

    @Override
    public CurrencyId currency() {
        return currency;
    }

    @Override
    public Day foundedOn() {
        return foundedOn;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Polity polity && id.equals(polity.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Polity[" + name + ", " + id.value() + "]";
    }
}
