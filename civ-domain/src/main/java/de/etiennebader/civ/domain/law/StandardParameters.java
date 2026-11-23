package de.etiennebader.civ.domain.law;


import java.util.List;

/**
 * Katalog der Standardparameter, die Gesetze setzen können.
 *
 * <p>In Phase 3 entstehen Parameter zusätzlich datengetrieben aus Datapacks.
 */
public final class StandardParameters {

    /** Grundsteuer in kleinster Währungseinheit pro Block Grundstücksfläche und Tag. */
    public static final ParameterKey<Long> LAND_TAX_PER_BLOCK =
            ParameterKey.constrained("land_tax_per_block", Long.class, 0L, rate -> rate >= 0);

    /** Steuerfreie Grundstücksfläche je Inhaber in Blöcken; erst darüber hinaus wird Grundsteuer fällig. */
    public static final ParameterKey<Long> LAND_TAX_FREE_BLOCKS =
            ParameterKey.constrained("land_tax_free_blocks", Long.class, 0L, blocks -> blocks >= 0);

    /** So viele Tage nach dem Erwerb eines Grundstücks ist es steuerfrei, z. B. für Neubürger. */
    public static final ParameterKey<Long> LAND_TAX_GRACE_DAYS =
            ParameterKey.constrained("land_tax_grace_days", Long.class, 0L, days -> days >= 0);

    /** Ob Botschaften Grundsteuer zahlen. */
    public static final ParameterKey<Boolean> TAX_EMBASSIES =
            ParameterKey.of("tax_embassies", Boolean.class, false);

    /** Wie viele Außenposten eine Polity höchstens beanspruchen darf. */
    public static final ParameterKey<Integer> MAX_OUTPOSTS =
            ParameterKey.constrained("max_outposts", Integer.class, 0, max -> max >= 0);

    /**
     * Kosten für einen Chunk-Claim in kleinster Währungseinheit (100 = ein Smaragd), fällig aus der
     * Gemeindekasse. Reicht die Kasse nicht, wird der Claim abgelehnt. 0 (Standard) = kostenlos, wie
     * bisher; ein Beispielwert für eine spürbare Gebühr wäre 200 (2 Smaragde).
     */
    public static final ParameterKey<Long> CLAIM_COST_PER_CHUNK =
            ParameterKey.constrained("claim_cost_per_chunk", Long.class, 0L, cost -> cost >= 0);

    /** Ob Nicht-Mitglieder im Gebiet bauen und abbauen dürfen. */
    public static final ParameterKey<Boolean> OUTSIDERS_MAY_BUILD =
            ParameterKey.of("outsiders_may_build", Boolean.class, false);

    /** Ob Nicht-Mitglieder Türen, Tore, Knöpfe, Hebel und Betten benutzen dürfen. */
    public static final ParameterKey<Boolean> OUTSIDERS_MAY_INTERACT =
            ParameterKey.of("outsiders_may_interact", Boolean.class, false);

    /** Ob Nicht-Mitglieder Truhen, Öfen und andere Behälter öffnen dürfen. */
    public static final ParameterKey<Boolean> OUTSIDERS_MAY_OPEN_CONTAINERS =
            ParameterKey.of("outsiders_may_open_containers", Boolean.class, false);

    /** Ob Explosionen im Gemeindegebiet Blöcke zerstören. */
    public static final ParameterKey<Boolean> EXPLOSIONS_ALLOWED =
            ParameterKey.of("explosions_allowed", Boolean.class, false);

    /** Ob Feuer im Gemeindegebiet Blöcke verbrennt. */
    public static final ParameterKey<Boolean> FIRE_SPREAD_ALLOWED =
            ParameterKey.of("fire_spread_allowed", Boolean.class, false);

    /** Ob Rechtsänderungen eine Abstimmung der Bürger brauchen, statt sofort zu gelten. */
    public static final ParameterKey<Boolean> LAWS_BY_VOTE =
            ParameterKey.of("laws_by_vote", Boolean.class, false);

    /** Nach wie vielen Tagen eine Abstimmung ohne klare Mehrheit ausgezählt wird. */
    public static final ParameterKey<Long> VOTE_DAYS =
            ParameterKey.constrained("vote_days", Long.class, 2L, days -> days >= 1);

    /** Prozent jedes Ladenverkaufs im Gemeindegebiet, die an die Gemeindekasse gehen. */
    public static final ParameterKey<Long> SALES_TAX_PERCENT =
            ParameterKey.constrained("sales_tax_percent", Long.class, 0L, percent -> percent >= 0 && percent <= 50);

    /** Prozent der Mieteinnahmen und Löhne, die an die Gemeindekasse gehen (Einkommensteuer, Issue #19). */
    public static final ParameterKey<Long> INCOME_TAX_PERCENT =
            ParameterKey.constrained("income_tax_percent", Long.class, 0L, percent -> percent >= 0 && percent <= 50);

    /** Ob Bauprojekte auf Grundstücken der Gemeinde vom Bauamt genehmigt werden müssen (Issue #19). */
    public static final ParameterKey<Boolean> BUILD_PERMIT_REQUIRED =
            ParameterKey.of("build_permit_required", Boolean.class, false);

    /** Gebühr für eine Baugenehmigung in kleinster Währungseinheit, fällig vom Bauherrn an die Gemeindekasse. */
    public static final ParameterKey<Long> BUILD_PERMIT_FEE =
            ParameterKey.constrained("build_permit_fee", Long.class, 0L, fee -> fee >= 0);

    /** Wie viele Mieter ein Grundstück höchstens hat; über 1 sind Mehrfamilienhäuser erlaubt (Issue #19). */
    public static final ParameterKey<Integer> MAX_TENANTS_PER_PLOT =
            ParameterKey.constrained("max_tenants_per_plot", Integer.class, 1, max -> max >= 1 && max <= 50);

    /** Amtszeit gewählter Ämter in Tagen; 0 heißt unbefristet, dann wird nur auf Antrag gewählt. */
    public static final ParameterKey<Long> TERM_DAYS =
            ParameterKey.constrained("term_days", Long.class, 0L, days -> days >= 0 && days <= 365);

    /** Wie viele Tage eine Wahl läuft; ausgezählt wird am Ende des letzten Tages. */
    public static final ParameterKey<Long> ELECTION_DAYS =
            ParameterKey.constrained("election_days", Long.class, 2L, days -> days >= 1 && days <= 30);

    /** Gewicht einer Dorfbewohner-Stimme bei Wahlen in Prozent einer Bürgerstimme; 0 heißt kein Wahlrecht. */
    public static final ParameterKey<Long> VILLAGER_VOTE_PERCENT =
            ParameterKey.constrained("villager_vote_percent", Long.class, 0L, percent -> percent >= 0 && percent <= 100);

    /** Prozent der Stimmen, die eine Änderung von Verfassungsartikeln braucht, sobald die Verfassung ausgerufen ist; 66 = zwei Drittel. */
    public static final ParameterKey<Long> AMENDMENT_MAJORITY_PERCENT =
            ParameterKey.constrained("amendment_majority_percent", Long.class, 66L,
                    percent -> percent >= 50 && percent <= 100);

    /** Ob Recht eines übergeordneten Bundes dem eigenen Recht vorgeht, wo beide dasselbe regeln. */
    public static final ParameterKey<Boolean> FEDERAL_LAW_PREVAILS =
            ParameterKey.of("federal_law_prevails", Boolean.class, true);

    /** Ob Spieler einander im Gebiet Schaden zufügen dürfen. */
    public static final ParameterKey<Boolean> PVP_ALLOWED =
            ParameterKey.of("pvp_allowed", Boolean.class, true);

    /** Ob feindliche Monster im Gebiet leben dürfen; sonst verschwinden sie dort. */
    public static final ParameterKey<Boolean> HOSTILE_MOBS_ALLOWED =
            ParameterKey.of("hostile_mobs_allowed", Boolean.class, true);

    /** Ob Nicht-Mitglieder Tiere im Gebiet verletzen dürfen. */
    public static final ParameterKey<Boolean> OUTSIDERS_MAY_HURT_ANIMALS =
            ParameterKey.of("outsiders_may_hurt_animals", Boolean.class, false);

    /** Bundesumlage: täglicher Anteil der Kasse jedes Glieds in Prozent, der an den Bund geht (nur im Bund). */
    public static final ParameterKey<Long> FEDERAL_LEVY_PERCENT =
            ParameterKey.constrained("federal_levy_percent", Long.class, 0L, percent -> percent >= 0 && percent <= 10);

    /**
     * Sitze im Parlament; 0 heißt kein Parlament. Mit einem gewählten Parlament stimmen nur noch die Abgeordneten über
     * Anträge ab, und jede Rechtsänderung braucht ihre Zustimmung.
     */
    public static final ParameterKey<Long> PARLIAMENT_SEATS =
            ParameterKey.constrained("parliament_seats", Long.class, 0L, seats -> seats >= 0 && seats <= 99);

    /** Ob Stimmen bei Wahlen nur an einer Wahlurne (Block) abgegeben werden dürfen, nicht per Menü oder Befehl. */
    public static final ParameterKey<Boolean> BALLOT_BOX_REQUIRED =
            ParameterKey.of("ballot_box_required", Boolean.class, false);

    /**
     * Wie viele Tage ein Antrag mindestens offen sein muss, bevor im Parlament darüber abgestimmt wird; verhindert,
     * dass ein Antrag noch am Tag seiner Stellung entschieden wird, bevor Spieler mitstimmen konnten.
     */
    public static final ParameterKey<Long> PARLIAMENT_MOTION_MIN_OPEN_DAYS =
            ParameterKey.constrained("parliament_motion_min_open_days", Long.class, 1L, days -> days >= 0 && days <= 30);

    /**
     * Ob das Ergebnis einer Abstimmung erst verkündet wird, nachdem alle Stimmberechtigten (Abgeordnete bzw. Bürger)
     * abgestimmt haben, statt schon bei rechnerisch sicherer Mehrheit. Die Frist ({@link #VOTE_DAYS}) zählt trotzdem
     * aus, falls nicht alle abstimmen.
     */
    public static final ParameterKey<Boolean> FULL_TURNOUT_REQUIRED =
            ParameterKey.of("full_turnout_required", Boolean.class, false);

    /**
     * Alle wie viele Tage das Parlament tagt: Nur an Sitzungstagen stimmen Dorfbewohner-Abgeordnete ab, stellen Anträge
     * und werden fällige Anträge ausgezählt.
     */
    public static final ParameterKey<Long> SESSION_DAYS =
            ParameterKey.constrained("session_days", Long.class, 1L, days -> days >= 1 && days <= 7);

    /** Ob Bürger per Brief wählen dürfen: Stimmzettel am Briefkasten, auch wo sonst nur die Wahlurne zählt. */
    public static final ParameterKey<Boolean> POSTAL_VOTE_ALLOWED =
            ParameterKey.of("postal_vote_allowed", Boolean.class, false);

    /**
     * Tägliches Gehalt aus der Kasse für jeden Amtsinhaber und Abgeordneten, in kleinster Währungseinheit
     * (100 = ein Smaragd). Reicht die Kasse nicht, fällt es aus.
     */
    public static final ParameterKey<Long> OFFICE_SALARY =
            ParameterKey.constrained("office_salary", Long.class, 0L, salary -> salary >= 0 && salary <= 10_000);

    /** Wie Ämter besetzt werden: relative Mehrheit oder absolute Mehrheit mit Stichwahl. */
    public static final ParameterKey<String> OFFICE_VOTING =
            ParameterKey.choice("office_voting", VotingMethod.Office.PLURALITY.id(), VotingMethod.Office.ids());

    /** Wie Parlamentssitze verteilt werden, z. B. Sainte-Laguë, D'Hondt, Mehrheitswahl in Wahlkreisen. */
    public static final ParameterKey<String> PARLIAMENT_VOTING =
            ParameterKey.choice("parliament_voting", VotingMethod.Parliament.SAINTE_LAGUE.id(),
                    VotingMethod.Parliament.ids());

    /** Personenwahl oder Listenwahl bei der Parlamentswahl. */
    public static final ParameterKey<String> BALLOT_TYPE =
            ParameterKey.choice("ballot_type", VotingMethod.Ballot.PERSON.id(), VotingMethod.Ballot.ids());

    /** Sperrklausel in Prozent: Listen darunter bekommen bei Verhältniswahl keine Sitze (Wahlkreissieger schon). */
    public static final ParameterKey<Long> THRESHOLD_PERCENT =
            ParameterKey.constrained("threshold_percent", Long.class, 0L, percent -> percent >= 0 && percent <= 20);

    /** Amtszeitbegrenzung: höchstens so viele Amtszeiten in Folge im selben Amt; 0 heißt unbegrenzt. */
    public static final ParameterKey<Long> TERM_LIMIT =
            ParameterKey.constrained("term_limit", Long.class, 0L, terms -> terms >= 0 && terms <= 10);

    /** Wahlpflicht: Bürger, die bei einer Wahl nicht abstimmen, zahlen {@link #VOTING_FINE} an die Kasse. */
    public static final ParameterKey<Boolean> COMPULSORY_VOTING =
            ParameterKey.of("compulsory_voting", Boolean.class, false);

    /** Bußgeld bei Wahlpflicht in kleinster Einheit (100 = ein Smaragd). */
    public static final ParameterKey<Long> VOTING_FINE =
            ParameterKey.constrained("voting_fine", Long.class, 100L, fine -> fine >= 0 && fine <= 10_000);

    /** Wahldebatte: am Vortag jeder Wahl diskutieren die Kandidaten öffentlich. */
    public static final ParameterKey<Boolean> ELECTION_DEBATES =
            ParameterKey.of("election_debates", Boolean.class, true);

    /** Pressefreiheit: ohne sie veröffentlichen Zeitungen keine Kritik an der Führung dieser Polity. */
    public static final ParameterKey<Boolean> PRESS_FREEDOM =
            ParameterKey.of("press_freedom", Boolean.class, true);

    /** Geschworene je Strafverfahren; 0 heißt, der Richter entscheidet allein. */
    public static final ParameterKey<Long> JURY_SIZE =
            ParameterKey.constrained("jury_size", Long.class, 5L, jurors -> jurors >= 0 && jurors <= 12);

    /** Höchststrafe eines Gerichts in kleinster Einheit. */
    public static final ParameterKey<Long> MAX_FINE =
            ParameterKey.constrained("max_fine", Long.class, 5_000L, fine -> fine >= 0 && fine <= 1_000_000);

    /** Ob ein Gericht Verurteilte aus der Gemeinde verbannen darf. */
    public static final ParameterKey<Boolean> EXILE_ALLOWED =
            ParameterKey.of("exile_allowed", Boolean.class, true);

    /** Gerichtsgebühr für eine Klage in kleinster Einheit; der Unterlegene trägt sie. */
    public static final ParameterKey<Long> COURT_FEE =
            ParameterKey.constrained("court_fee", Long.class, 100L, fee -> fee >= 0 && fee <= 100_000);

    /**
     * Regierungsmaßnahme gegen die Konjunktur: dämpft die Auswirkung der aktuellen Konjunkturphase auf
     * Firmenproduktion, Dorfbewohner-Einkommen und Aktienkurse dieser Wirtschaftszone in Prozent; 100 dämpft am
     * stärksten, neutralisiert die Phase aber nicht vollständig.
     */
    public static final ParameterKey<Long> CONJUNCTURE_STIMULUS_PERCENT =
            ParameterKey.constrained("conjuncture_stimulus_percent", Long.class, 0L,
                    percent -> percent >= 0 && percent <= 100);
    /**
     * Die Parteiliste einer Partei für Listenwahlen: Mitglieder in Reihenfolge, kodiert wie
     * {@code PLAYER:<uuid>,PERSON:<uuid>,...}; leer, solange keine gesetzt wurde (dann gilt die
     * Reihenfolge der Kandidatur). Siehe {@code de.etiennebader.civ.domain.polity.PartyList}.
     */
    public static final ParameterKey<String> PARTY_LIST = ParameterKey.of("party_list", String.class, "");

    /**
     * Ob die Parteiliste ({@link #PARTY_LIST}) nur per Abstimmung der Parteimitglieder geändert werden kann, statt
     * allein vom Parteivorsitz.
     */
    public static final ParameterKey<Boolean> PARTY_LIST_BY_VOTE =
            ParameterKey.of("party_list_by_vote", Boolean.class, false);

    /**
     * Reparationen je Punkt Gefechtsstärke-Unterschied (siehe {@code de.etiennebader.civ.domain.war.BattleResolver}),
     * in kleinster Währungseinheit, fällig von der Gemeindekasse der unterlegenen an die der siegreichen Polity
     * nach einem entschiedenen Gefecht. 0 (Standard) = keine Reparationen.
     */
    public static final ParameterKey<Long> WAR_REPARATIONS_PER_STRENGTH_POINT =
            ParameterKey.constrained("war_reparations_per_strength_point", Long.class, 0L, rate -> rate >= 0);

    /**
     * Gebühr je Tag erkaufter Neutralität (siehe {@code WarService}), aus der Gemeindekasse an den übergeordneten
     * Bund. In kleinster Währungseinheit.
     */
    public static final ParameterKey<Long> NEUTRALITY_FEE_PER_DAY = ParameterKey.constrained(
            "neutrality_fee_per_day", Long.class, 100L, fee -> fee >= 0);

    /**
     * Wartezeit in Sekunden zwischen zwei Teleports eines Spielers zu den Heimat-Häfen dieser Polity
     * (0 = keine Wartezeit).
     */
    public static final ParameterKey<Long> HOME_PORT_COOLDOWN_SECONDS = ParameterKey.constrained(
            "home_port_cooldown_seconds", Long.class, 10L, seconds -> seconds >= 0 && seconds <= 86_400);

    /** Ab diesem Kriegswillen (0-100) darf der Bürgermeister Krieg erklären; abhängig von Persönlichkeiten, Parteien, Ansprüchen. */
    public static final ParameterKey<Long> WAR_WILL_THRESHOLD = ParameterKey.constrained(
            "war_will_threshold", Long.class, 50L, value -> value >= 0 && value <= 100);

    /** Grenz-Chunks, die der Sieger eines entschiedenen Kriegs fordert (0 = keine Gebietseroberung). */
    public static final ParameterKey<Long> CONQUEST_CHUNKS_PER_WIN = ParameterKey.constrained(
            "conquest_chunks_per_win", Long.class, 2L, value -> value >= 0 && value <= 16);

    /** Kosten je Soldat für eine Stufe Ausbildung, aus der Gemeindekasse (kleinste Währungseinheit). */
    public static final ParameterKey<Long> TRAINING_COST_PER_HEAD = ParameterKey.constrained(
            "training_cost_per_head", Long.class, 20L, value -> value >= 0);

    /** Kosten je Soldat für eine Stufe Ausrüstung, aus der Gemeindekasse. */
    public static final ParameterKey<Long> EQUIPMENT_COST_PER_HEAD = ParameterKey.constrained(
            "equipment_cost_per_head", Long.class, 50L, value -> value >= 0);

    /** Kosten je Lazarettbett, aus der Gemeindekasse. */
    public static final ParameterKey<Long> HOSPITAL_BED_COST = ParameterKey.constrained(
            "hospital_bed_cost", Long.class, 200L, value -> value >= 0);

    /** Täglicher Sold je einfachem Soldaten in der Garnison, aus der Gemeindekasse. 0 (Standard) = unbezahlt. */
    public static final ParameterKey<Long> SOLDIER_WAGE = ParameterKey.constrained("soldier_wage", Long.class, 0L,
            wage -> wage >= 0);

    /** Täglicher Sold je Offizier in der Garnison, aus der Gemeindekasse. 0 (Standard) = unbezahlt. */
    public static final ParameterKey<Long> OFFICER_WAGE = ParameterKey.constrained("officer_wage", Long.class, 0L,
            wage -> wage >= 0);

    /**
     * Standardwährung dieser Polity als Währungscode (siehe {@code CurrencyRegistry}). Leer (Standard) = die
     * Währung, mit der die Polity gegründet wurde. In ihr handeln die Dorfbewohner des Gebiets.
     */
    public static final ParameterKey<String> DEFAULT_CURRENCY = ParameterKey.constrained("default_currency",
            String.class, "", code -> code.isEmpty() || code.matches("[A-Z_]{2,16}"));

    /** Ob in dieser Polity Banken Kredite vergeben dürfen. */
    public static final ParameterKey<Boolean> BANK_LENDING_ALLOWED =
            ParameterKey.of("bank_lending_allowed", Boolean.class, true);

    /** Höchster Jahreszins in Prozent, den Banken dieser Polity verlangen dürfen. */
    public static final ParameterKey<Long> BANK_MAX_INTEREST_PERCENT = ParameterKey.constrained(
            "bank_max_interest_percent", Long.class, 50L, value -> value >= 0 && value <= 1_000);

    /** Mindest-Kreditscore (300-850) eines Kreditnehmers; 0 = keine Einschränkung. */
    public static final ParameterKey<Long> BANK_MIN_CREDIT_SCORE = ParameterKey.constrained(
            "bank_min_credit_score", Long.class, 0L, value -> value == 0 || value >= 300 && value <= 850);

    /** Höchstes Leitzins-Niveau in Prozent, das ein Zentralbank-Vorsitzender setzen darf. */
    public static final ParameterKey<Long> CENTRAL_BANK_MAX_BASE_RATE_PERCENT = ParameterKey.constrained(
            "central_bank_max_base_rate_percent", Long.class, 100L, value -> value >= 0 && value <= 100);

     /** Alle Standardparameter in fester Reihenfolge. */
    public static List<ParameterKey<?>> all() {
        return List.of(
                LAND_TAX_PER_BLOCK,
                LAND_TAX_FREE_BLOCKS,
                LAND_TAX_GRACE_DAYS,
                TAX_EMBASSIES,
                MAX_OUTPOSTS,
                CLAIM_COST_PER_CHUNK,
                OUTSIDERS_MAY_BUILD,
                OUTSIDERS_MAY_INTERACT,
                OUTSIDERS_MAY_OPEN_CONTAINERS,
                EXPLOSIONS_ALLOWED,
                FIRE_SPREAD_ALLOWED,
                LAWS_BY_VOTE,
                VOTE_DAYS,
                SALES_TAX_PERCENT,
                INCOME_TAX_PERCENT,
                BUILD_PERMIT_REQUIRED,
                BUILD_PERMIT_FEE,
                MAX_TENANTS_PER_PLOT,
                TERM_DAYS,
                ELECTION_DAYS,
                VILLAGER_VOTE_PERCENT,
                AMENDMENT_MAJORITY_PERCENT,
                FEDERAL_LAW_PREVAILS,
                PVP_ALLOWED,
                HOSTILE_MOBS_ALLOWED,
                OUTSIDERS_MAY_HURT_ANIMALS,
                FEDERAL_LEVY_PERCENT,
                PARLIAMENT_SEATS,
                SESSION_DAYS,
                BALLOT_BOX_REQUIRED,
                PARLIAMENT_MOTION_MIN_OPEN_DAYS,
                FULL_TURNOUT_REQUIRED,
                POSTAL_VOTE_ALLOWED,
                OFFICE_SALARY,
                OFFICE_VOTING,
                PARLIAMENT_VOTING,
                BALLOT_TYPE,
                THRESHOLD_PERCENT,
                TERM_LIMIT,
                COMPULSORY_VOTING,
                VOTING_FINE,
                ELECTION_DEBATES,
                PRESS_FREEDOM,
                JURY_SIZE,
                MAX_FINE,
                EXILE_ALLOWED,
                COURT_FEE,
                CONJUNCTURE_STIMULUS_PERCENT,
                PARTY_LIST,
                PARTY_LIST_BY_VOTE,
                WAR_REPARATIONS_PER_STRENGTH_POINT,
                SOLDIER_WAGE,
                OFFICER_WAGE,
                NEUTRALITY_FEE_PER_DAY,
                HOME_PORT_COOLDOWN_SECONDS,
                WAR_WILL_THRESHOLD,
                CONQUEST_CHUNKS_PER_WIN,
                TRAINING_COST_PER_HEAD,
                EQUIPMENT_COST_PER_HEAD,
                HOSPITAL_BED_COST,
                DEFAULT_CURRENCY,
                BANK_LENDING_ALLOWED,
                BANK_MAX_INTEREST_PERCENT,
                BANK_MIN_CREDIT_SCORE,
                CENTRAL_BANK_MAX_BASE_RATE_PERCENT
        );
    }

    /**
     * Verfassungsartikel: wie entschieden und gewählt wird. Nach dem Ausrufen der Verfassung ändern sie sich nur durch
     * Abstimmung mit {@link #AMENDMENT_MAJORITY_PERCENT}.
     */
    public static List<ParameterKey<?>> constitutional() {
        return List.of(LAWS_BY_VOTE, VOTE_DAYS, TERM_DAYS, ELECTION_DAYS, VILLAGER_VOTE_PERCENT,
                AMENDMENT_MAJORITY_PERCENT, FEDERAL_LAW_PREVAILS, PARLIAMENT_SEATS, SESSION_DAYS, BALLOT_BOX_REQUIRED,
                POSTAL_VOTE_ALLOWED, OFFICE_VOTING, PARLIAMENT_VOTING, BALLOT_TYPE, THRESHOLD_PERCENT, TERM_LIMIT,
                PRESS_FREEDOM, DEFAULT_CURRENCY);
    }

    public static boolean isConstitutional(ParameterKey<?> key) {
        return constitutional().contains(key);
    }

    private StandardParameters() {
    }
}
