package de.etiennebader.civ.server.construction;

import de.etiennebader.civ.domain.construction.Blueprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Alle verfügbaren Baupläne: eingebaute Stile und importierte Dateien. Importierte Dateien liegen unverändert im
 * Ordner {@code blueprints} neben der Datenbank und werden beim Start wieder gelesen; der Dateiname ohne Endung ist
 * der Planname.
 */
public final class BlueprintLibrary {

    /** Größte angenommene Datei beim Import (komprimiert). */
    public static final int MAX_UPLOAD_BYTES = 4 * 1024 * 1024;

    private final Map<String, Blueprint> plans = new TreeMap<>();
    private final Optional<Path> directory;

    public BlueprintLibrary(Optional<Path> directory) {
        this.directory = directory;
        BuiltInBlueprints.all().forEach(plan -> plans.put(plan.name(), plan));
        directory.ifPresent(this::loadFolder);
    }

    private void loadFolder(Path folder) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.sorted().toList()) {
                String fileName = file.getFileName().toString();
                int dot = fileName.lastIndexOf('.');
                if (dot <= 0) {
                    continue;
                }
                try {
                    Blueprint plan = SchematicReader.read(fileName, fileName.substring(0, dot), "eigen",
                            Files.readAllBytes(file));
                    plans.put(plan.name(), plan);
                } catch (SchematicReader.InvalidSchematicException | IllegalArgumentException | IOException ignored) {
                    // kaputte Datei: ignorieren, der Server startet trotzdem
                }
            }
        } catch (IOException ignored) {
            // kein Ordner: nur die eingebauten Pläne
        }
    }

    public synchronized List<Blueprint> all() {
        return new ArrayList<>(plans.values());
    }

    public synchronized Optional<Blueprint> find(String name) {
        return Optional.ofNullable(plans.get(name.strip().toLowerCase(Locale.ROOT)));
    }

    /**
     * Nimmt eine Bauplandatei auf und speichert sie.
     *
     * @param fileName Dateiname mit Endung {@code .litematic} oder {@code .schem}; ohne Endung ist es der Planname
     * @throws SchematicReader.InvalidSchematicException bei ungültigen Dateien oder Namen
     */
    public synchronized Blueprint importFile(String fileName, byte[] bytes)
            throws SchematicReader.InvalidSchematicException {
        if (bytes.length > MAX_UPLOAD_BYTES) {
            throw new SchematicReader.InvalidSchematicException("Datei zu groß");
        }
        int dot = fileName.lastIndexOf('.');
        String name = (dot > 0 ? fileName.substring(0, dot) : fileName).strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "-");
        if (name.isEmpty() || BuiltInBlueprints.all().stream().anyMatch(plan -> plan.name().equals(name))) {
            throw new SchematicReader.InvalidSchematicException("Dieser Planname ist vergeben oder ungültig: " + name);
        }
        String extension = dot > 0 ? fileName.substring(dot).toLowerCase(Locale.ROOT) : "";
        Blueprint plan;
        try {
            plan = SchematicReader.read(name + extension, name, "eigen", bytes);
        } catch (IllegalArgumentException e) {
            throw new SchematicReader.InvalidSchematicException(e.getMessage());
        }
        directory.ifPresent(folder -> {
            try {
                Files.createDirectories(folder);
                Files.write(folder.resolve(name + extension), bytes);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        plans.put(plan.name(), plan);
        return plan;
    }

    /** Pläne, die in die Fläche passen, nach Größe sortiert. */
    public synchronized List<Blueprint> fitting(long width, long depth) {
        return plans.values().stream().filter(plan -> plan.sizeX() <= width && plan.sizeZ() <= depth)
                .sorted(Comparator.comparingInt(Blueprint::blockCount)).toList();
    }
}
