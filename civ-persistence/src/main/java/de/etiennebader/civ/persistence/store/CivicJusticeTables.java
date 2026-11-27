package de.etiennebader.civ.persistence.store;

import de.etiennebader.civ.domain.actor.ActorRef;
import de.etiennebader.civ.domain.civic.Anthem;
import de.etiennebader.civ.domain.civic.Area;
import de.etiennebader.civ.domain.civic.CivicRegistry;
import de.etiennebader.civ.domain.civic.CivicView;
import de.etiennebader.civ.domain.civic.Constituency;
import de.etiennebader.civ.domain.civic.ConstituencyId;
import de.etiennebader.civ.domain.civic.CustomImage;
import de.etiennebader.civ.domain.civic.Emblem;
import de.etiennebader.civ.domain.civic.Flag;
import de.etiennebader.civ.domain.civic.HomePort;
import de.etiennebader.civ.domain.civic.PortAccess;
import de.etiennebader.civ.domain.civic.PortSettings;
import de.etiennebader.civ.domain.territory.BlockPosition;
import de.etiennebader.civ.domain.civic.ImageKind;
import de.etiennebader.civ.domain.justice.CaseId;
import de.etiennebader.civ.domain.justice.CaseKind;
import de.etiennebader.civ.domain.justice.CourtCase;
import de.etiennebader.civ.domain.justice.CourtStaff;
import de.etiennebader.civ.domain.justice.Evidence;
import de.etiennebader.civ.domain.justice.Exile;
import de.etiennebader.civ.domain.justice.Judiciary;
import de.etiennebader.civ.domain.justice.JudiciaryView;
import de.etiennebader.civ.domain.justice.Role;
import de.etiennebader.civ.domain.justice.Side;
import de.etiennebader.civ.domain.justice.Stage;
import de.etiennebader.civ.domain.justice.Statement;
import de.etiennebader.civ.domain.justice.Verdict;
import de.etiennebader.civ.domain.shared.Day;
import de.etiennebader.civ.domain.shared.PolityId;
import de.etiennebader.civ.domain.territory.DimensionId;
import de.etiennebader.civ.persistence.ActorCodec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tabellen für Wahlkreise, Hoheitszeichen und Justiz. Beide Aggregate sind klein und werden als Ganzes neu geschrieben.
 */
final class CivicJusticeTables {

    void writeCivic(Connection connection, CivicView civic) throws SQLException {
        clear(connection, "constituency", "constituency_area", "constituency_town", "polity_flag", "polity_anthem",
                "polity_image", "polity_port_settings", "polity_port", "polity_emblem");
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO constituency (id, position, polity_id, name) VALUES (?, ?, ?, ?)");
             PreparedStatement area = connection.prepareStatement("""
                     INSERT INTO constituency_area (constituency_id, position, dimension, min_x, min_z, max_x, max_z)
                     VALUES (?, ?, ?, ?, ?, ?, ?)""");
             PreparedStatement town = connection.prepareStatement(
                     "INSERT INTO constituency_town (constituency_id, position, town_id) VALUES (?, ?, ?)")) {
            int position = 0;
            for (Constituency constituency : civic.constituencies()) {
                String id = constituency.id().value().toString();
                insert.setString(1, id);
                insert.setInt(2, position++);
                insert.setString(3, constituency.polity().value().toString());
                insert.setString(4, constituency.name());
                insert.addBatch();
                for (int index = 0; index < constituency.areas().size(); index++) {
                    Area rect = constituency.areas().get(index);
                    area.setString(1, id);
                    area.setInt(2, index);
                    area.setString(3, rect.dimension().value());
                    area.setInt(4, rect.minX());
                    area.setInt(5, rect.minZ());
                    area.setInt(6, rect.maxX());
                    area.setInt(7, rect.maxZ());
                    area.addBatch();
                }
                for (int index = 0; index < constituency.towns().size(); index++) {
                    town.setString(1, id);
                    town.setInt(2, index);
                    town.setString(3, constituency.towns().get(index).value().toString());
                    town.addBatch();
                }
            }
            insert.executeBatch();
            area.executeBatch();
            town.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO polity_flag (polity_id, base, layers) VALUES (?, ?, ?)")) {
            for (Map.Entry<PolityId, Flag> entry : civic.flags().entrySet()) {
                insert.setString(1, entry.getKey().value().toString());
                insert.setString(2, entry.getValue().base());
                insert.setString(3, String.join(";", entry.getValue().layers().stream()
                        .map(layer -> layer.pattern() + "=" + layer.color()).toList()));
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO polity_anthem (polity_id, title, instrument, notes, audio) VALUES (?, ?, ?, ?, ?)")) {
            for (Map.Entry<PolityId, Anthem> entry : civic.anthems().entrySet()) {
                insert.setString(1, entry.getKey().value().toString());
                insert.setString(2, entry.getValue().title());
                insert.setString(3, entry.getValue().instrument());
                insert.setString(4, entry.getValue().notes());
                insert.setString(5, entry.getValue().audio());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO polity_image (id, polity_id, kind, hash, width, height, uploaded) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (CustomImage image : civic.images().values()) {
                insert.setString(1, image.id());
                insert.setString(2, image.polity().value().toString());
                insert.setString(3, image.kind().name());
                insert.setString(4, image.hash());
                insert.setInt(5, image.width());
                insert.setInt(6, image.height());
                insert.setLong(7, image.uploadedAt().value());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO polity_emblem (dimension, x, y, z, polity_id, kind, placed) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (Emblem emblem : civic.emblems()) {
                insert.setString(1, emblem.position().dimension().value());
                insert.setInt(2, emblem.position().x());
                insert.setInt(3, emblem.position().y());
                insert.setInt(4, emblem.position().z());
                insert.setString(5, emblem.polity().value().toString());
                insert.setString(6, emblem.kind().name());
                insert.setLong(7, emblem.placedOn().value());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO polity_port_settings (polity_id, access, fee) VALUES (?, ?, ?)")) {
            for (Map.Entry<PolityId, PortSettings> entry : civic.portSettings().entrySet()) {
                insert.setString(1, entry.getKey().value().toString());
                insert.setString(2, entry.getValue().access().name());
                insert.setLong(3, entry.getValue().fee());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO polity_port (polity_id, slot, dimension, x, y, z) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (HomePort port : civic.ports()) {
                insert.setString(1, port.polity().value().toString());
                insert.setInt(2, port.slot());
                insert.setString(3, port.position().dimension().value());
                insert.setInt(4, port.position().x());
                insert.setInt(5, port.position().y());
                insert.setInt(6, port.position().z());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    CivicRegistry loadCivic(Connection connection) throws SQLException {
        Map<String, List<Area>> areas = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT * FROM constituency_area ORDER BY constituency_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                areas.computeIfAbsent(rows.getString("constituency_id"), ignored -> new ArrayList<>())
                        .add(new Area(new DimensionId(rows.getString("dimension")), rows.getInt("min_x"),
                                rows.getInt("min_z"), rows.getInt("max_x"), rows.getInt("max_z")));
            }
        }
        Map<String, List<PolityId>> towns = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT * FROM constituency_town ORDER BY constituency_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                towns.computeIfAbsent(rows.getString("constituency_id"), ignored -> new ArrayList<>())
                        .add(polity(rows.getString("town_id")));
            }
        }
        List<Constituency> constituencies = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM constituency ORDER BY position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String id = rows.getString("id");
                constituencies.add(new Constituency(new ConstituencyId(UUID.fromString(id)),
                        polity(rows.getString("polity_id")), rows.getString("name"),
                        areas.getOrDefault(id, List.of()), towns.getOrDefault(id, List.of())));
            }
        }
        Map<PolityId, Flag> flags = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM polity_flag");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                List<Flag.Layer> layers = new ArrayList<>();
                for (String layer : rows.getString("layers").split(";")) {
                    int equals = layer.indexOf('=');
                    if (equals > 0) {
                        layers.add(new Flag.Layer(layer.substring(0, equals), layer.substring(equals + 1)));
                    }
                }
                flags.put(polity(rows.getString("polity_id")), new Flag(rows.getString("base"), layers));
            }
        }
        Map<PolityId, Anthem> anthems = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM polity_anthem");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                anthems.put(polity(rows.getString("polity_id")), new Anthem(rows.getString("title"),
                        rows.getString("instrument"), rows.getString("notes"), rows.getString("audio")));
            }
        }
        Map<String, CustomImage> images = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM polity_image");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                images.put(rows.getString("id"), new CustomImage(rows.getString("id"), polity(rows.getString("polity_id")),
                        ImageKind.valueOf(rows.getString("kind")), rows.getString("hash"), rows.getInt("width"),
                        rows.getInt("height"), Day.of(rows.getLong("uploaded"))));
            }
        }
        Map<PolityId, PortSettings> portSettings = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM polity_port_settings");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                portSettings.put(polity(rows.getString("polity_id")), new PortSettings(
                        PortAccess.valueOf(rows.getString("access")), rows.getLong("fee")));
            }
        }
        List<HomePort> ports = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM polity_port ORDER BY polity_id, slot");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                ports.add(new HomePort(polity(rows.getString("polity_id")), rows.getInt("slot"),
                        new BlockPosition(new DimensionId(rows.getString("dimension")), rows.getInt("x"),
                                rows.getInt("y"), rows.getInt("z"))));
            }
        }
        List<Emblem> emblems = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM polity_emblem");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                emblems.add(new Emblem(new BlockPosition(new DimensionId(rows.getString("dimension")), rows.getInt("x"),
                        rows.getInt("y"), rows.getInt("z")), polity(rows.getString("polity_id")),
                        Emblem.Kind.valueOf(rows.getString("kind")), Day.of(rows.getLong("placed"))));
            }
        }
        return CivicRegistry.restore(constituencies, flags, anthems, images, portSettings, ports, emblems);
    }

    void writeJustice(Connection connection, JudiciaryView justice) throws SQLException {
        clear(connection, "court_case", "case_party", "case_evidence", "case_statement", "court_staff", "exile");
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO court_case (id, number, court_id, kind, plaintiff, defendant, charge, target, judge,
                    prosecutor, defense, stage, filed_on, stage_until, appeal_of, v_upheld, v_fine, v_damages, v_exile,
                    v_dismissal, v_reasoning, v_decided_by, v_guilty, v_innocent, v_day)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""");
             PreparedStatement party = connection.prepareStatement(
                     "INSERT INTO case_party (case_id, role, position, actor, guilty) VALUES (?, ?, ?, ?, ?)");
             PreparedStatement evidence = connection.prepareStatement("""
                     INSERT INTO case_evidence (case_id, number, submitted_by, side, kind, title, content, weight, day)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""");
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO case_statement (case_id, position, speaker, role, text, tendency, day)
                     VALUES (?, ?, ?, ?, ?, ?, ?)""")) {
            for (CourtCase found : justice.cases()) {
                String id = found.id().value().toString();
                insert.setString(1, id);
                insert.setInt(2, found.number());
                insert.setString(3, found.court().value().toString());
                insert.setString(4, found.kind().name());
                insert.setString(5, ActorCodec.encode(found.plaintiff()));
                insert.setString(6, ActorCodec.encode(found.defendant()));
                insert.setString(7, found.charge());
                insert.setString(8, found.target());
                optionalActor(insert, 9, found.judge());
                optionalActor(insert, 10, found.prosecutor());
                optionalActor(insert, 11, found.defense());
                insert.setString(12, found.stage().name());
                insert.setLong(13, found.filedOn().value());
                insert.setLong(14, found.stageUntil().value());
                if (found.appealOf().isPresent()) {
                    insert.setString(15, found.appealOf().get().value().toString());
                } else {
                    insert.setNull(15, Types.VARCHAR);
                }
                if (found.verdict().isPresent()) {
                    Verdict verdict = found.verdict().get();
                    insert.setInt(16, verdict.upheld() ? 1 : 0);
                    insert.setLong(17, verdict.fine());
                    insert.setLong(18, verdict.damages());
                    insert.setInt(19, verdict.exile() ? 1 : 0);
                    insert.setInt(20, verdict.dismissal() ? 1 : 0);
                    insert.setString(21, verdict.reasoning());
                    insert.setString(22, ActorCodec.encode(verdict.decidedBy()));
                    insert.setInt(23, verdict.guiltyVotes());
                    insert.setInt(24, verdict.innocentVotes());
                    insert.setLong(25, verdict.day().value());
                } else {
                    for (int column = 16; column <= 25; column++) {
                        insert.setNull(column, Types.INTEGER);
                    }
                }
                insert.addBatch();
                parties(party, id, "W", found.witnesses(), Map.of());
                parties(party, id, "J", found.jurors(), found.votes());
                for (Evidence item : found.evidence()) {
                    evidence.setString(1, id);
                    evidence.setInt(2, item.number());
                    evidence.setString(3, ActorCodec.encode(item.submittedBy()));
                    evidence.setString(4, item.side().name());
                    evidence.setString(5, item.kind());
                    evidence.setString(6, item.title());
                    evidence.setString(7, item.content());
                    evidence.setInt(8, item.weight());
                    evidence.setLong(9, item.day().value());
                    evidence.addBatch();
                }
                for (int index = 0; index < found.statements().size(); index++) {
                    Statement said = found.statements().get(index);
                    statement.setString(1, id);
                    statement.setInt(2, index);
                    statement.setString(3, ActorCodec.encode(said.speaker()));
                    statement.setString(4, said.role().name());
                    statement.setString(5, said.text());
                    statement.setInt(6, said.tendency());
                    statement.setLong(7, said.day().value());
                    statement.addBatch();
                }
            }
            insert.executeBatch();
            party.executeBatch();
            evidence.executeBatch();
            statement.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO court_staff (court_id, role, position, actor) VALUES (?, ?, ?, ?)")) {
            for (Map.Entry<PolityId, CourtStaff> entry : justice.staff().entrySet()) {
                staff(insert, entry.getKey(), "JUDGE", entry.getValue().judges());
                staff(insert, entry.getKey(), "PROSECUTOR", entry.getValue().prosecutors());
            }
            insert.executeBatch();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO exile (polity_id, actor, until) VALUES (?, ?, ?)")) {
            for (Exile exile : justice.exiles()) {
                insert.setString(1, exile.polity().value().toString());
                insert.setString(2, ActorCodec.encode(exile.actor()));
                insert.setLong(3, exile.until().value());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    Judiciary loadJustice(Connection connection) throws SQLException {
        Map<String, List<ActorRef>> witnesses = new LinkedHashMap<>();
        Map<String, List<ActorRef>> jurors = new LinkedHashMap<>();
        Map<String, Map<ActorRef, Boolean>> votes = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT * FROM case_party ORDER BY case_id, role, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String id = rows.getString("case_id");
                ActorRef actor = ActorCodec.decode(rows.getString("actor"));
                if (rows.getString("role").equals("W")) {
                    witnesses.computeIfAbsent(id, ignored -> new ArrayList<>()).add(actor);
                } else {
                    jurors.computeIfAbsent(id, ignored -> new ArrayList<>()).add(actor);
                    int guilty = rows.getInt("guilty");
                    if (!rows.wasNull()) {
                        votes.computeIfAbsent(id, ignored -> new LinkedHashMap<>()).put(actor, guilty == 1);
                    }
                }
            }
        }
        Map<String, List<Evidence>> evidence = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT * FROM case_evidence ORDER BY case_id, number");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                evidence.computeIfAbsent(rows.getString("case_id"), ignored -> new ArrayList<>()).add(new Evidence(
                        rows.getInt("number"), ActorCodec.decode(rows.getString("submitted_by")),
                        Side.valueOf(rows.getString("side")), rows.getString("kind"), rows.getString("title"),
                        rows.getString("content"), rows.getInt("weight"), Day.of(rows.getLong("day"))));
            }
        }
        Map<String, List<Statement>> statements = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT * FROM case_statement ORDER BY case_id, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                statements.computeIfAbsent(rows.getString("case_id"), ignored -> new ArrayList<>()).add(new Statement(
                        ActorCodec.decode(rows.getString("speaker")), Role.valueOf(rows.getString("role")),
                        rows.getString("text"), rows.getInt("tendency"), Day.of(rows.getLong("day"))));
            }
        }
        List<CourtCase> cases = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM court_case ORDER BY number");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String id = rows.getString("id");
                Optional<Verdict> verdict = Optional.empty();
                rows.getInt("v_upheld");
                if (!rows.wasNull()) {
                    verdict = Optional.of(new Verdict(rows.getInt("v_upheld") == 1, rows.getLong("v_fine"),
                            rows.getLong("v_damages"), rows.getInt("v_exile") == 1, rows.getInt("v_dismissal") == 1,
                            rows.getString("v_reasoning"), ActorCodec.decode(rows.getString("v_decided_by")),
                            rows.getInt("v_guilty"), rows.getInt("v_innocent"), Day.of(rows.getLong("v_day"))));
                }
                String appealOf = rows.getString("appeal_of");
                cases.add(new CourtCase(new CaseId(UUID.fromString(id)), rows.getInt("number"),
                        polity(rows.getString("court_id")), CaseKind.valueOf(rows.getString("kind")),
                        ActorCodec.decode(rows.getString("plaintiff")), ActorCodec.decode(rows.getString("defendant")),
                        rows.getString("charge"), rows.getString("target"), optionalActor(rows, "judge"),
                        optionalActor(rows, "prosecutor"), optionalActor(rows, "defense"),
                        witnesses.getOrDefault(id, List.of()), jurors.getOrDefault(id, List.of()),
                        evidence.getOrDefault(id, List.of()), statements.getOrDefault(id, List.of()),
                        votes.getOrDefault(id, Map.of()), Stage.valueOf(rows.getString("stage")),
                        Day.of(rows.getLong("filed_on")), Day.of(rows.getLong("stage_until")), verdict,
                        appealOf == null ? Optional.empty() : Optional.of(new CaseId(UUID.fromString(appealOf)))));
            }
        }
        Map<PolityId, List<ActorRef>> judges = new LinkedHashMap<>();
        Map<PolityId, List<ActorRef>> prosecutors = new LinkedHashMap<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT * FROM court_staff ORDER BY court_id, role, position");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                PolityId court = polity(rows.getString("court_id"));
                (rows.getString("role").equals("JUDGE") ? judges : prosecutors)
                        .computeIfAbsent(court, ignored -> new ArrayList<>())
                        .add(ActorCodec.decode(rows.getString("actor")));
            }
        }
        Map<PolityId, CourtStaff> staff = new LinkedHashMap<>();
        judges.keySet().forEach(court -> staff.put(court, null));
        prosecutors.keySet().forEach(court -> staff.put(court, null));
        staff.replaceAll((court, ignored) -> new CourtStaff(judges.getOrDefault(court, List.of()),
                prosecutors.getOrDefault(court, List.of())));
        List<Exile> exiles = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM exile");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                exiles.add(new Exile(polity(rows.getString("polity_id")), ActorCodec.decode(rows.getString("actor")),
                        Day.of(rows.getLong("until"))));
            }
        }
        return Judiciary.restore(cases, staff, exiles);
    }

    private static void parties(PreparedStatement insert, String id, String role, List<ActorRef> actors,
                                Map<ActorRef, Boolean> votes) throws SQLException {
        for (int index = 0; index < actors.size(); index++) {
            insert.setString(1, id);
            insert.setString(2, role);
            insert.setInt(3, index);
            insert.setString(4, ActorCodec.encode(actors.get(index)));
            Boolean vote = votes.get(actors.get(index));
            if (vote == null) {
                insert.setNull(5, Types.INTEGER);
            } else {
                insert.setInt(5, vote ? 1 : 0);
            }
            insert.addBatch();
        }
    }

    private static void staff(PreparedStatement insert, PolityId court, String role, List<ActorRef> actors)
            throws SQLException {
        for (int index = 0; index < actors.size(); index++) {
            insert.setString(1, court.value().toString());
            insert.setString(2, role);
            insert.setInt(3, index);
            insert.setString(4, ActorCodec.encode(actors.get(index)));
            insert.addBatch();
        }
    }

    private static void optionalActor(PreparedStatement insert, int column, Optional<ActorRef> actor)
            throws SQLException {
        if (actor.isPresent()) {
            insert.setString(column, ActorCodec.encode(actor.get()));
        } else {
            insert.setNull(column, Types.VARCHAR);
        }
    }

    private static Optional<ActorRef> optionalActor(ResultSet rows, String column) throws SQLException {
        String value = rows.getString(column);
        return value == null ? Optional.empty() : Optional.of(ActorCodec.decode(value));
    }

    private static PolityId polity(String id) {
        return new PolityId(UUID.fromString(id));
    }

    private static void clear(Connection connection, String... tables) throws SQLException {
        try (java.sql.Statement clear = connection.createStatement()) {
            for (String table : tables) {
                clear.executeUpdate("DELETE FROM " + table);
            }
        }
    }
}
