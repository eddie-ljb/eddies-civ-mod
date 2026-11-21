package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.land.Area;
import de.etiennebader.civ.domain.law.EnactedStatute;
import de.etiennebader.civ.domain.law.ParameterCatalog;
import de.etiennebader.civ.domain.law.ParameterValue;
import de.etiennebader.civ.domain.law.Statute;
import de.etiennebader.civ.domain.polity.Ballot;
import de.etiennebader.civ.domain.polity.Constitution;
import de.etiennebader.civ.domain.polity.Election;
import de.etiennebader.civ.domain.polity.ElectionResult;
import de.etiennebader.civ.domain.polity.Membership;
import de.etiennebader.civ.domain.polity.MembershipType;
import de.etiennebader.civ.domain.polity.Motion;
import de.etiennebader.civ.domain.polity.Office;
import de.etiennebader.civ.domain.polity.OfficeTerm;
import de.etiennebader.civ.domain.polity.Parliament;
import de.etiennebader.civ.domain.polity.PartyProfile;
import de.etiennebader.civ.domain.polity.ProgramMode;
import de.etiennebader.civ.domain.polity.Platform;
import de.etiennebader.civ.domain.polity.Polity;
import de.etiennebader.civ.domain.polity.PolityKind;
import de.etiennebader.civ.domain.polity.PolityName;
import de.etiennebader.civ.domain.polity.PolitySnapshot;
import de.etiennebader.civ.domain.polity.Poster;
import de.etiennebader.civ.domain.polity.Promise;
import de.etiennebader.civ.domain.polity.Proposal;
import de.etiennebader.civ.domain.polity.SettlementTier;
import de.etiennebader.civ.domain.polity.Street;
import de.etiennebader.civ.domain.polity.Vote;
import de.etiennebader.civ.domain.shared.CurrencyId;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.ElectionId;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.shared.ProposalId;
import de.etiennebader.civ.domain.shared.StatuteId;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.persistence.ActorCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tabellen {@code polity}, {@code membership}, {@code statute}, {@code statute_value}, {@code proposal},
 * {@code office}, {@code election} und {@code parliament_seat} samt Kindtabellen.
 * Eine geänderte Polity wird vollständig neu geschrieben; ihre Gründungsreihenfolge ({@code seq})
 * bleibt dabei erhalten.
 */
final class PolityTable {

    private static final String UPSERT_POLITY = """
            INSERT INTO polity (id, seq, name, kind, tier, currency, founded_on, seat_dimension, seat_x, seat_y, seat_z,
                                level, constitution_proclaimed_on, preamble, party_order, party_economy, party_emblem,
                                party_mode, party_area, parliament_elected_on, parliament_until)
            VALUES (?, (SELECT COALESCE(MAX(seq), 0) + 1 FROM polity), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET name = excluded.name, kind = excluded.kind, level = excluded.level,
                constitution_proclaimed_on = excluded.constitution_proclaimed_on, preamble = excluded.preamble,
                party_order = excluded.party_order, party_economy = excluded.party_economy,
                party_emblem = excluded.party_emblem, party_mode = excluded.party_mode, party_area = excluded.party_area,
                parliament_elected_on = excluded.parliament_elected_on, parliament_until = excluded.parliament_until,
                tier = excluded.tier,
                currency = excluded.currency, founded_on = excluded.founded_on,
                seat_dimension = excluded.seat_dimension, seat_x = excluded.seat_x, seat_y = excluded.seat_y,
                seat_z = excluded.seat_z""";

    private final ParameterCatalog catalog;

    PolityTable(ParameterCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    void write(Connection connection, PolitySnapshot polity) throws SQLException {
        String id = polity.id().value().toString();
        try (PreparedStatement upsert = connection.prepareStatement(UPSERT_POLITY)) {
            upsert.setString(1, id);
            upsert.setString(2, polity.name().value());
            upsert.setString(3, polity.kind().name());
            upsert.setString(4, polity.tier().name());
            upsert.setString(5, polity.currency().code());
            upsert.setLong(6, polity.foundedOn().value());
            bindSeat(upsert, 7, polity.seat());
            upsert.setString(11, polity.level());
            if (polity.constitution().proclaimedOn().isPresent()) {
                upsert.setLong(12, polity.constitution().proclaimedOn().get().value());
            } else {
                upsert.setNull(12, Types.INTEGER);
            }
            upsert.setString(13, polity.constitution().preamble());
            if (polity.party().isPresent()) {
                upsert.setInt(14, polity.party().get().order());
                upsert.setInt(15, polity.party().get().economy());
                upsert.setString(16, polity.party().get().emblem());
                upsert.setString(17, polity.party().get().mode().name());
                if (polity.party().get().area().isPresent()) {
                    upsert.setString(18, polity.party().get().area().get().value().toString());
                } else {
                    upsert.setNull(18, Types.VARCHAR);
                }
            } else {
                upsert.setNull(14, Types.INTEGER);
                upsert.setNull(15, Types.INTEGER);
                upsert.setNull(16, Types.VARCHAR);
                upsert.setNull(17, Types.VARCHAR);
                upsert.setNull(18, Types.VARCHAR);
            }
            bindDay(upsert, 19, polity.parliament().electedOn());
            bindDay(upsert, 20, polity.parliament().until());
            upsert.executeUpdate();
        }
        deleteChildren(connection, id);
        insertMembers(connection, id, polity.members());
        insertStatutes(connection, id, polity.statutes());
        insertProposals(connection, id, polity.proposals());
        insertOffices(connection, id, polity.offices());
        insertElections(connection, id, polity.elections());
        insertSeats(connection, id, polity.parliament());
        insertResults(connection, id, polity.results());
        insertPromises(connection, id, polity.promises());
        insertPosters(connection, id, polity.posters());
        insertStreets(connection, id, polity.streets());
    }

    private static void insertStreets(Connection connection, String polityId, List<Street> streets)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO street (polity_id, position, name, dimension, min_x, min_z, max_x, max_z) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            int position = 0;
            for (Street street : streets) {
                insert.setString(1, polityId);
                insert.setInt(2, position++);
                insert.setString(3, street.name());
                insert.setString(4, street.area().dimension().value());
                insert.setInt(5, street.area().minX());
                insert.setInt(6, street.area().minZ());
                insert.setInt(7, street.area().maxX());
                insert.setInt(8, street.area().maxZ());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static Map<String, List<Street>> loadStreets(Connection connection) throws SQLException {
        Map<String, List<Street>> streets = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT polity_id, name, dimension, min_x, min_z, max_x, max_z FROM street ORDER BY polity_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                streets.computeIfAbsent(rows.getString("polity_id"), ignored -> new ArrayList<>()).add(new Street(
                        rows.getString("name"), new Area(new DimensionId(rows.getString("dimension")),
                        rows.getInt("min_x"), rows.getInt("min_z"), rows.getInt("max_x"), rows.getInt("max_z"))));
            }
        }
        return streets;
    }

    private static void insertResults(Connection connection, String polityId, List<ElectionResult> results)
            throws SQLException {
        try (PreparedStatement insertResult = connection.prepareStatement(
                "INSERT INTO election_result (id, polity_id, position, office, closed_on) VALUES (?, ?, ?, ?, ?)");
             PreparedStatement insertVote = connection.prepareStatement("""
                     INSERT INTO election_result_vote (result_id, position, district_id, candidate_kind, candidate_id, votes)
                     VALUES (?, ?, ?, ?, ?, ?)""");
             PreparedStatement insertWinner = connection.prepareStatement(
                     "INSERT INTO election_result_winner (result_id, position, winner_kind, winner_id) VALUES (?, ?, ?, ?)")) {
            int position = 0;
            for (ElectionResult result : results) {
                String resultId = result.id().value().toString();
                insertResult.setString(1, resultId);
                insertResult.setString(2, polityId);
                insertResult.setInt(3, position++);
                insertResult.setString(4, result.office().name());
                insertResult.setLong(5, result.closedOn().value());
                insertResult.addBatch();
                int votePosition = 0;
                for (ElectionResult.District district : result.districts()) {
                    for (Map.Entry<ActorRef, Long> vote : district.votes().entrySet()) {
                        insertVote.setString(1, resultId);
                        insertVote.setInt(2, votePosition++);
                        if (district.district().isPresent()) {
                            insertVote.setString(3, district.district().get().value().toString());
                        } else {
                            insertVote.setNull(3, Types.VARCHAR);
                        }
                        insertVote.setString(4, ActorCodec.kindOf(vote.getKey()));
                        insertVote.setString(5, vote.getKey().uuid().toString());
                        insertVote.setLong(6, vote.getValue());
                        insertVote.addBatch();
                    }
                }
                int winnerPosition = 0;
                for (ActorRef winner : result.winners()) {
                    insertWinner.setString(1, resultId);
                    insertWinner.setInt(2, winnerPosition++);
                    insertWinner.setString(3, ActorCodec.kindOf(winner));
                    insertWinner.setString(4, winner.uuid().toString());
                    insertWinner.addBatch();
                }
            }
            insertResult.executeBatch();
            insertVote.executeBatch();
            insertWinner.executeBatch();
        }
    }

    private static void insertPromises(Connection connection, String polityId, List<Promise> promises)
            throws SQLException {
        try (PreparedStatement insertPromise = connection.prepareStatement("""
                INSERT INTO promise (polity_id, position, candidate_kind, candidate_id, office, text, made_on)
                VALUES (?, ?, ?, ?, ?, ?, ?)""");
             PreparedStatement insertValue = connection.prepareStatement("""
                     INSERT INTO promise_value (polity_id, promise_position, position, parameter, value)
                     VALUES (?, ?, ?, ?, ?)""")) {
            int position = 0;
            for (Promise promise : promises) {
                int promisePosition = position++;
                insertPromise.setString(1, polityId);
                insertPromise.setInt(2, promisePosition);
                insertPromise.setString(3, ActorCodec.kindOf(promise.candidate()));
                insertPromise.setString(4, promise.candidate().uuid().toString());
                insertPromise.setString(5, promise.office().name());
                insertPromise.setString(6, promise.text());
                insertPromise.setLong(7, promise.madeOn().value());
                insertPromise.addBatch();
                int valuePosition = 0;
                for (ParameterValue<?> value : promise.values()) {
                    insertValue.setString(1, polityId);
                    insertValue.setInt(2, promisePosition);
                    insertValue.setInt(3, valuePosition++);
                    insertValue.setString(4, value.key().id());
                    insertValue.setString(5, ParameterCatalog.encode(value));
                    insertValue.addBatch();
                }
            }
            insertPromise.executeBatch();
            insertValue.executeBatch();
        }
    }

    private static void insertPosters(Connection connection, String polityId, List<Poster> posters)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO poster (polity_id, dimension, x, y, z, subject_kind, subject_id, owner_kind, owner_id, placed_on)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            for (Poster poster : posters) {
                insert.setString(1, polityId);
                insert.setString(2, poster.position().dimension().value());
                insert.setInt(3, poster.position().x());
                insert.setInt(4, poster.position().y());
                insert.setInt(5, poster.position().z());
                insert.setString(6, ActorCodec.kindOf(poster.subject()));
                insert.setString(7, poster.subject().uuid().toString());
                insert.setString(8, ActorCodec.kindOf(poster.owner()));
                insert.setString(9, poster.owner().uuid().toString());
                insert.setLong(10, poster.placedOn().value());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static Map<String, List<ElectionResult>> loadResults(Connection connection) throws SQLException {
        Map<String, Map<Optional<PolityId>, Map<ActorRef, Long>>> votes = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT result_id, district_id, candidate_kind, candidate_id, votes
                FROM election_result_vote ORDER BY result_id, position""");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String district = rows.getString("district_id");
                votes.computeIfAbsent(rows.getString("result_id"), ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(district == null ? Optional.empty()
                                : Optional.of(new PolityId(UUID.fromString(district))), ignored -> new LinkedHashMap<>())
                        .put(ActorCodec.decode(rows.getString("candidate_kind"), rows.getString("candidate_id")),
                                rows.getLong("votes"));
            }
        }
        Map<String, List<ActorRef>> winners = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT result_id, winner_kind, winner_id FROM election_result_winner ORDER BY result_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                winners.computeIfAbsent(rows.getString("result_id"), ignored -> new ArrayList<>())
                        .add(ActorCodec.decode(rows.getString("winner_kind"), rows.getString("winner_id")));
            }
        }
        Map<String, List<ElectionResult>> results = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, polity_id, office, closed_on FROM election_result ORDER BY polity_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String id = rows.getString("id");
                List<ElectionResult.District> districts = votes.getOrDefault(id, Map.of()).entrySet().stream()
                        .map(entry -> new ElectionResult.District(entry.getKey(), entry.getValue()))
                        .toList();
                results.computeIfAbsent(rows.getString("polity_id"), ignored -> new ArrayList<>())
                        .add(new ElectionResult(new ElectionId(UUID.fromString(id)), Office.valueOf(rows.getString("office")),
                                Day.of(rows.getLong("closed_on")), districts, winners.getOrDefault(id, List.of())));
            }
        }
        return results;
    }

    private Map<String, List<Promise>> loadPromises(Connection connection) throws SQLException {
        Map<String, List<ParameterValue<?>>> values = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT polity_id, promise_position, parameter, value
                FROM promise_value ORDER BY polity_id, promise_position, position""");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                values.computeIfAbsent(rows.getString("polity_id") + "#" + rows.getInt("promise_position"),
                        ignored -> new ArrayList<>()).add(catalog.decode(rows.getString("parameter"), rows.getString("value")));
            }
        }
        Map<String, List<Promise>> promises = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT polity_id, position, candidate_kind, candidate_id, office, text, made_on
                FROM promise ORDER BY polity_id, position""");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String polityId = rows.getString("polity_id");
                promises.computeIfAbsent(polityId, ignored -> new ArrayList<>()).add(new Promise(
                        ActorCodec.decode(rows.getString("candidate_kind"), rows.getString("candidate_id")),
                        Office.valueOf(rows.getString("office")), rows.getString("text"),
                        values.getOrDefault(polityId + "#" + rows.getInt("position"), List.of()),
                        Day.of(rows.getLong("made_on"))));
            }
        }
        return promises;
    }

    private static Map<String, List<Poster>> loadPosters(Connection connection) throws SQLException {
        Map<String, List<Poster>> posters = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT polity_id, dimension, x, y, z, subject_kind, subject_id, owner_kind, owner_id, placed_on
                FROM poster ORDER BY polity_id, dimension, x, y, z""");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                posters.computeIfAbsent(rows.getString("polity_id"), ignored -> new ArrayList<>()).add(new Poster(
                        new BlockPosition(new DimensionId(rows.getString("dimension")), rows.getInt("x"),
                                rows.getInt("y"), rows.getInt("z")),
                        ActorCodec.decode(rows.getString("subject_kind"), rows.getString("subject_id")),
                        ActorCodec.decode(rows.getString("owner_kind"), rows.getString("owner_id")),
                        Day.of(rows.getLong("placed_on"))));
            }
        }
        return posters;
    }

    private static void bindDay(PreparedStatement statement, int index, Optional<Day> day) throws SQLException {
        if (day.isPresent()) {
            statement.setLong(index, day.get().value());
        } else {
            statement.setNull(index, Types.INTEGER);
        }
    }

    private static Optional<Day> dayOf(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? Optional.empty() : Optional.of(Day.of(value));
    }

    private static void insertSeats(Connection connection, String polityId, Parliament parliament)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO parliament_seat (polity_id, position, member_kind, member_id, party_id, votes, reserve)
                VALUES (?, ?, ?, ?, ?, ?, ?)""")) {
            int position = 0;
            List<Parliament.Seat> all = new ArrayList<>(parliament.seats());
            all.addAll(parliament.reserves());
            for (Parliament.Seat seat : all) {
                insert.setInt(7, position >= parliament.seats().size() ? 1 : 0);
                insert.setString(1, polityId);
                insert.setInt(2, position++);
                insert.setString(3, ActorCodec.kindOf(seat.member()));
                insert.setString(4, seat.member().uuid().toString());
                if (seat.party().isPresent()) {
                    insert.setString(5, seat.party().get().value().toString());
                } else {
                    insert.setNull(5, Types.VARCHAR);
                }
                insert.setLong(6, seat.votes());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    /** Sitze je Polity; Nachrücker unter dem Schlüssel {@code <polity>#reserve}. */
    private static Map<String, List<Parliament.Seat>> loadSeats(Connection connection) throws SQLException {
        Map<String, List<Parliament.Seat>> seats = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT polity_id, member_kind, member_id, party_id, votes, reserve
                FROM parliament_seat ORDER BY polity_id, position""");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String party = rows.getString("party_id");
                String key = rows.getString("polity_id") + (rows.getInt("reserve") == 1 ? "#reserve" : "");
                seats.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Parliament.Seat(
                        ActorCodec.decode(rows.getString("member_kind"), rows.getString("member_id")),
                        party == null ? Optional.empty() : Optional.of(new PolityId(UUID.fromString(party))),
                        rows.getLong("votes")));
            }
        }
        return seats;
    }

    private static Parliament parliamentOf(ResultSet row, List<Parliament.Seat> seats, List<Parliament.Seat> reserves)
            throws SQLException {
        Optional<Day> electedOn = dayOf(row, "parliament_elected_on");
        if (seats.isEmpty() || electedOn.isEmpty()) {
            return Parliament.NONE;
        }
        return new Parliament(seats, electedOn, dayOf(row, "parliament_until"), reserves);
    }

    private static void bindSeat(PreparedStatement statement, int firstIndex, Optional<BlockPosition> seat)
            throws SQLException {
        if (seat.isEmpty()) {
            statement.setNull(firstIndex, Types.VARCHAR);
            for (int offset = 1; offset <= 3; offset++) {
                statement.setNull(firstIndex + offset, Types.INTEGER);
            }
            return;
        }
        statement.setString(firstIndex, seat.get().dimension().value());
        statement.setInt(firstIndex + 1, seat.get().x());
        statement.setInt(firstIndex + 2, seat.get().y());
        statement.setInt(firstIndex + 3, seat.get().z());
    }

    private static Optional<PartyProfile> partyOf(ResultSet row) throws SQLException {
        String emblem = row.getString("party_emblem");
        if (emblem == null) {
            return Optional.empty();
        }
        String modeName = row.getString("party_mode");
        ProgramMode mode = modeName == null ? ProgramMode.CHAIR : ProgramMode.valueOf(modeName);
        String area = row.getString("party_area");
        Optional<PolityId> areaId = area == null ? Optional.empty() : Optional.of(new PolityId(UUID.fromString(area)));
        return Optional.of(new PartyProfile(row.getInt("party_order"), row.getInt("party_economy"), emblem, mode, areaId));
    }

    private static Constitution constitutionOf(ResultSet row) throws SQLException {
        long proclaimed = row.getLong("constitution_proclaimed_on");
        Optional<Day> proclaimedOn = row.wasNull() ? Optional.empty() : Optional.of(Day.of(proclaimed));
        return new Constitution(proclaimedOn, row.getString("preamble"));
    }

    private static Optional<BlockPosition> seatOf(ResultSet row) throws SQLException {
        String dimension = row.getString("seat_dimension");
        if (dimension == null) {
            return Optional.empty();
        }
        return Optional.of(new BlockPosition(new DimensionId(dimension), row.getInt("seat_x"), row.getInt("seat_y"),
                row.getInt("seat_z")));
    }

    void delete(Connection connection, PolityId polity) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM polity WHERE id = ?")) {
            delete.setString(1, polity.value().toString());
            delete.executeUpdate();
        }
    }

    private static void deleteChildren(Connection connection, String polityId) throws SQLException {
        for (String table : List.of("membership", "statute", "proposal", "office", "election", "parliament_seat",
                "election_result", "promise_value", "promise", "poster", "street")) {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + table + " WHERE polity_id = ?")) {
                delete.setString(1, polityId);
                delete.executeUpdate();
            }
        }
    }

    private static void insertMembers(Connection connection, String polityId, Iterable<Membership> members) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO membership (polity_id, position, actor_kind, actor_id, type, since) VALUES (?, ?, ?, ?, ?, ?)")) {
            int position = 0;
            for (Membership membership : members) {
                insert.setString(1, polityId);
                insert.setInt(2, position++);
                insert.setString(3, ActorCodec.kindOf(membership.member()));
                insert.setString(4, membership.member().uuid().toString());
                insert.setString(5, membership.type().name());
                insert.setLong(6, membership.since().value());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void insertStatutes(Connection connection, String polityId, List<EnactedStatute> statutes) throws SQLException {
        try (PreparedStatement insertStatute = connection.prepareStatement(
                "INSERT INTO statute (id, polity_id, position, title, enacted_on, text) VALUES (?, ?, ?, ?, ?, ?)");
             PreparedStatement insertValue = connection.prepareStatement(
                     "INSERT INTO statute_value (statute_id, position, parameter, value) VALUES (?, ?, ?, ?)")) {
            int position = 0;
            for (EnactedStatute enacted : statutes) {
                String statuteId = enacted.statute().id().value().toString();
                insertStatute.setString(1, statuteId);
                insertStatute.setString(2, polityId);
                insertStatute.setInt(3, position++);
                insertStatute.setString(4, enacted.statute().title());
                insertStatute.setLong(5, enacted.enactedOn().value());
                insertStatute.setString(6, enacted.statute().text());
                insertStatute.addBatch();
                int valuePosition = 0;
                for (ParameterValue<?> value : enacted.statute().values()) {
                    insertValue.setString(1, statuteId);
                    insertValue.setInt(2, valuePosition++);
                    insertValue.setString(3, value.key().id());
                    insertValue.setString(4, ParameterCatalog.encode(value));
                    insertValue.addBatch();
                }
            }
            insertStatute.executeBatch();
            insertValue.executeBatch();
        }
    }

    private static final String PARTY_PROGRAM_KIND = "PARTY_PROGRAM";

    /** Kodiert ein Parteiprogramm für die Antrags-Textspalte: {@code order|economy|emblem|mode|area}. */
    private static String encodeProfile(PartyProfile profile) {
        return profile.order() + "|" + profile.economy() + "|" + profile.emblem() + "|" + profile.mode().name()
                + "|" + profile.area().map(id -> id.value().toString()).orElse("");
    }

    private static PartyProfile decodeProfile(String encoded) {
        String[] parts = encoded.split("\\|", 5);
        Optional<PolityId> area = parts.length > 4 && !parts[4].isBlank()
                ? Optional.of(new PolityId(UUID.fromString(parts[4]))) : Optional.empty();
        return new PartyProfile(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts[2],
                ProgramMode.valueOf(parts[3]), area);
    }

    private static void insertProposals(Connection connection, String polityId, List<Proposal> proposals)
            throws SQLException {
        try (PreparedStatement insertProposal = connection.prepareStatement("""
                INSERT INTO proposal (id, polity_id, position, parameter, value, proposer_kind, proposer_id, opened_on,
                                      motion, title, text, statute_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""");
             PreparedStatement insertValue = connection.prepareStatement(
                     "INSERT INTO proposal_value (proposal_id, position, parameter, value) VALUES (?, ?, ?, ?)");
             PreparedStatement insertVote = connection.prepareStatement(
                     "INSERT INTO proposal_vote (proposal_id, position, voter_kind, voter_id, in_favour) VALUES (?, ?, ?, ?, ?)")) {
            int position = 0;
            for (Proposal proposal : proposals) {
                String proposalId = proposal.id().value().toString();
                insertProposal.setString(1, proposalId);
                insertProposal.setString(2, polityId);
                insertProposal.setInt(3, position++);
                insertProposal.setString(6, ActorCodec.kindOf(proposal.proposer()));
                insertProposal.setString(7, proposal.proposer().uuid().toString());
                insertProposal.setLong(8, proposal.openedOn().value());
                switch (proposal.motion()) {
                    case Motion.Change change -> {
                        insertProposal.setString(4, change.change().key().id());
                        insertProposal.setString(5, ParameterCatalog.encode(change.change()));
                        insertProposal.setString(9, "CHANGE");
                        insertProposal.setString(10, "");
                        insertProposal.setString(11, "");
                        insertProposal.setNull(12, Types.VARCHAR);
                    }
                    case Motion.Enact enact -> {
                        insertProposal.setString(4, "");
                        insertProposal.setString(5, "");
                        insertProposal.setString(9, "ENACT");
                        insertProposal.setString(10, enact.title());
                        insertProposal.setString(11, enact.text());
                        insertProposal.setNull(12, Types.VARCHAR);
                        int valuePosition = 0;
                        for (ParameterValue<?> value : enact.values()) {
                            insertValue.setString(1, proposalId);
                            insertValue.setInt(2, valuePosition++);
                            insertValue.setString(3, value.key().id());
                            insertValue.setString(4, ParameterCatalog.encode(value));
                            insertValue.addBatch();
                        }
                    }
                    case Motion.Repeal repeal -> {
                        insertProposal.setString(4, "");
                        insertProposal.setString(5, "");
                        insertProposal.setString(9, "REPEAL");
                        insertProposal.setString(10, repeal.title());
                        insertProposal.setString(11, "");
                        insertProposal.setString(12, repeal.statute().value().toString());
                    }
                    case Motion.PartyProgram program -> {
                        insertProposal.setString(4, "");
                        insertProposal.setString(5, "");
                        insertProposal.setString(9, PARTY_PROGRAM_KIND);
                        insertProposal.setString(10, "");
                        insertProposal.setString(11, encodeProfile(program.profile()));
                        insertProposal.setNull(12, Types.VARCHAR);
                    }
                }
                insertProposal.addBatch();
                int votePosition = 0;
                for (Vote vote : proposal.votes()) {
                    insertVote.setString(1, proposalId);
                    insertVote.setInt(2, votePosition++);
                    insertVote.setString(3, ActorCodec.kindOf(vote.voter()));
                    insertVote.setString(4, vote.voter().uuid().toString());
                    insertVote.setInt(5, vote.inFavour() ? 1 : 0);
                    insertVote.addBatch();
                }
            }
            insertProposal.executeBatch();
            insertValue.executeBatch();
            insertVote.executeBatch();
        }
    }

    private static void insertOffices(Connection connection, String polityId, List<OfficeTerm> offices)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO office (polity_id, office, holder_kind, holder_id, since, until) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (OfficeTerm term : offices) {
                insert.setString(1, polityId);
                insert.setString(2, term.office().name());
                insert.setString(3, ActorCodec.kindOf(term.holder()));
                insert.setString(4, term.holder().uuid().toString());
                insert.setLong(5, term.since().value());
                if (term.until().isPresent()) {
                    insert.setLong(6, term.until().get().value());
                } else {
                    insert.setNull(6, Types.INTEGER);
                }
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void insertElections(Connection connection, String polityId, List<Election> elections)
            throws SQLException {
        try (PreparedStatement insertElection = connection.prepareStatement(
                "INSERT INTO election (id, polity_id, position, office, called_on, closes_on) VALUES (?, ?, ?, ?, ?, ?)");
             PreparedStatement insertCandidate = connection.prepareStatement("""
                     INSERT INTO election_candidate (election_id, position, candidate_kind, candidate_id, platform)
                     VALUES (?, ?, ?, ?, ?)""");
             PreparedStatement insertBallot = connection.prepareStatement("""
                     INSERT INTO election_ballot (election_id, position, voter_kind, voter_id, candidate_kind, candidate_id)
                     VALUES (?, ?, ?, ?, ?, ?)""")) {
            int position = 0;
            for (Election election : elections) {
                String electionId = election.id().value().toString();
                insertElection.setString(1, electionId);
                insertElection.setString(2, polityId);
                insertElection.setInt(3, position++);
                insertElection.setString(4, election.office().name());
                insertElection.setLong(5, election.calledOn().value());
                insertElection.setLong(6, election.closesOn().value());
                insertElection.addBatch();
                int candidatePosition = 0;
                for (ActorRef candidate : election.candidates()) {
                    insertCandidate.setString(1, electionId);
                    insertCandidate.setInt(2, candidatePosition++);
                    insertCandidate.setString(3, ActorCodec.kindOf(candidate));
                    insertCandidate.setString(4, candidate.uuid().toString());
                    insertCandidate.setString(5, election.platformOf(candidate).name());
                    insertCandidate.addBatch();
                }
                int ballotPosition = 0;
                for (Ballot ballot : election.ballots()) {
                    insertBallot.setString(1, electionId);
                    insertBallot.setInt(2, ballotPosition++);
                    insertBallot.setString(3, ActorCodec.kindOf(ballot.voter()));
                    insertBallot.setString(4, ballot.voter().uuid().toString());
                    insertBallot.setString(5, ActorCodec.kindOf(ballot.candidate()));
                    insertBallot.setString(6, ballot.candidate().uuid().toString());
                    insertBallot.addBatch();
                }
            }
            insertElection.executeBatch();
            insertCandidate.executeBatch();
            insertBallot.executeBatch();
        }
    }

    private static Map<String, List<OfficeTerm>> loadOffices(Connection connection) throws SQLException {
        Map<String, List<OfficeTerm>> offices = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT polity_id, office, holder_kind, holder_id, since, until FROM office ORDER BY polity_id, office");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                long until = rows.getLong("until");
                Optional<Day> end = rows.wasNull() ? Optional.empty() : Optional.of(Day.of(until));
                offices.computeIfAbsent(rows.getString("polity_id"), ignored -> new ArrayList<>()).add(new OfficeTerm(
                        Office.valueOf(rows.getString("office")),
                        ActorCodec.decode(rows.getString("holder_kind"), rows.getString("holder_id")),
                        Day.of(rows.getLong("since")), end));
            }
        }
        return offices;
    }

    private static Map<String, List<Election>> loadElections(Connection connection) throws SQLException {
        Map<String, List<ActorRef>> candidates = new HashMap<>();
        Map<String, Map<ActorRef, Platform>> platforms = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT election_id, candidate_kind, candidate_id, platform
                FROM election_candidate ORDER BY election_id, position""");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String electionId = rows.getString("election_id");
                ActorRef candidate = ActorCodec.decode(rows.getString("candidate_kind"), rows.getString("candidate_id"));
                candidates.computeIfAbsent(electionId, ignored -> new ArrayList<>()).add(candidate);
                platforms.computeIfAbsent(electionId, ignored -> new HashMap<>())
                        .put(candidate, Platform.valueOf(rows.getString("platform")));
            }
        }
        Map<String, List<Ballot>> ballots = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT election_id, voter_kind, voter_id, candidate_kind, candidate_id
                FROM election_ballot ORDER BY election_id, position""");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                ballots.computeIfAbsent(rows.getString("election_id"), ignored -> new ArrayList<>()).add(new Ballot(
                        ActorCodec.decode(rows.getString("voter_kind"), rows.getString("voter_id")),
                        ActorCodec.decode(rows.getString("candidate_kind"), rows.getString("candidate_id"))));
            }
        }
        Map<String, List<Election>> elections = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, polity_id, office, called_on, closes_on FROM election ORDER BY polity_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String id = rows.getString("id");
                elections.computeIfAbsent(rows.getString("polity_id"), ignored -> new ArrayList<>()).add(new Election(
                        new ElectionId(UUID.fromString(id)), Office.valueOf(rows.getString("office")),
                        Day.of(rows.getLong("called_on")), Day.of(rows.getLong("closes_on")),
                        candidates.getOrDefault(id, List.of()), ballots.getOrDefault(id, List.of()),
                        platforms.getOrDefault(id, Map.of())));
            }
        }
        return elections;
    }

    private Map<String, List<Proposal>> loadProposals(Connection connection) throws SQLException {
        Map<String, List<Vote>> votes = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT proposal_id, voter_kind, voter_id, in_favour FROM proposal_vote ORDER BY proposal_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                votes.computeIfAbsent(rows.getString("proposal_id"), ignored -> new ArrayList<>())
                        .add(new Vote(ActorCodec.decode(rows.getString("voter_kind"), rows.getString("voter_id")),
                                rows.getInt("in_favour") == 1));
            }
        }
        Map<String, List<ParameterValue<?>>> values = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT proposal_id, parameter, value FROM proposal_value ORDER BY proposal_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                values.computeIfAbsent(rows.getString("proposal_id"), ignored -> new ArrayList<>())
                        .add(catalog.decode(rows.getString("parameter"), rows.getString("value")));
            }
        }
        Map<String, List<Proposal>> proposals = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, polity_id, parameter, value, proposer_kind, proposer_id, opened_on, motion, title, text,
                       statute_ref
                FROM proposal ORDER BY polity_id, position""");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String id = rows.getString("id");
                Motion motion = switch (rows.getString("motion")) {
                    case "ENACT" -> new Motion.Enact(rows.getString("title"), rows.getString("text"),
                            values.getOrDefault(id, List.of()));
                    case "REPEAL" -> new Motion.Repeal(new StatuteId(UUID.fromString(rows.getString("statute_ref"))),
                            rows.getString("title"));
                    case PARTY_PROGRAM_KIND -> new Motion.PartyProgram(decodeProfile(rows.getString("text")));
                    default -> new Motion.Change(catalog.decode(rows.getString("parameter"), rows.getString("value")));
                };
                proposals.computeIfAbsent(rows.getString("polity_id"), ignored -> new ArrayList<>()).add(new Proposal(
                        new ProposalId(UUID.fromString(id)), motion,
                        ActorCodec.decode(rows.getString("proposer_kind"), rows.getString("proposer_id")),
                        Day.of(rows.getLong("opened_on")), votes.getOrDefault(id, List.of())));
            }
        }
        return proposals;
    }

    /** Alle Polities in Gründungsreihenfolge. */
    List<Polity> loadAll(Connection connection) throws SQLException {
        Map<String, List<Membership>> members = loadMembers(connection);
        Map<String, List<EnactedStatute>> statutes = loadStatutes(connection);
        Map<String, List<Proposal>> proposals = loadProposals(connection);
        Map<String, List<OfficeTerm>> offices = loadOffices(connection);
        Map<String, List<Election>> elections = loadElections(connection);
        Map<String, List<Parliament.Seat>> seats = loadSeats(connection);
        Map<String, List<ElectionResult>> results = loadResults(connection);
        Map<String, List<Promise>> promises = loadPromises(connection);
        Map<String, List<Poster>> posters = loadPosters(connection);
        Map<String, List<Street>> streets = loadStreets(connection);
        List<Polity> polities = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                """
                SELECT id, name, kind, level, tier, currency, founded_on, seat_dimension, seat_x, seat_y, seat_z,
                       constitution_proclaimed_on, preamble, party_order, party_economy, party_emblem, party_mode, party_area,
                       parliament_elected_on, parliament_until
                FROM polity ORDER BY seq""");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String id = rows.getString("id");
                polities.add(Polity.restore(new PolityId(UUID.fromString(id)), new PolityName(rows.getString("name")),
                        PolityKind.valueOf(rows.getString("kind")), rows.getString("level"),
                        SettlementTier.valueOf(rows.getString("tier")),
                        new CurrencyId(rows.getString("currency")), Day.of(rows.getLong("founded_on")), seatOf(rows),
                        members.getOrDefault(id, List.of()), statutes.getOrDefault(id, List.of()),
                        proposals.getOrDefault(id, List.of()), offices.getOrDefault(id, List.of()),
                        elections.getOrDefault(id, List.of()), constitutionOf(rows), partyOf(rows),
                        parliamentOf(rows, seats.getOrDefault(id, List.of()), seats.getOrDefault(id + "#reserve", List.of())),
                        results.getOrDefault(id, List.of()), promises.getOrDefault(id, List.of()),
                        posters.getOrDefault(id, List.of()), streets.getOrDefault(id, List.of())));
            }
        }
        return polities;
    }

    private static Map<String, List<Membership>> loadMembers(Connection connection) throws SQLException {
        Map<String, List<Membership>> members = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT polity_id, actor_kind, actor_id, type, since FROM membership ORDER BY polity_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                members.computeIfAbsent(rows.getString("polity_id"), ignored -> new ArrayList<>()).add(new Membership(
                        ActorCodec.decode(rows.getString("actor_kind"), rows.getString("actor_id")),
                        MembershipType.valueOf(rows.getString("type")), Day.of(rows.getLong("since"))));
            }
        }
        return members;
    }

    private Map<String, List<EnactedStatute>> loadStatutes(Connection connection) throws SQLException {
        Map<String, List<ParameterValue<?>>> values = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT statute_id, parameter, value FROM statute_value ORDER BY statute_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                values.computeIfAbsent(rows.getString("statute_id"), ignored -> new ArrayList<>())
                        .add(catalog.decode(rows.getString("parameter"), rows.getString("value")));
            }
        }
        Map<String, List<EnactedStatute>> statutes = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, polity_id, title, enacted_on, text FROM statute ORDER BY polity_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String id = rows.getString("id");
                Statute statute = new Statute(new StatuteId(UUID.fromString(id)), rows.getString("title"),
                        rows.getString("text"), values.getOrDefault(id, List.of()));
                statutes.computeIfAbsent(rows.getString("polity_id"), ignored -> new ArrayList<>())
                        .add(new EnactedStatute(statute, Day.of(rows.getLong("enacted_on"))));
            }
        }
        return statutes;
    }
}
