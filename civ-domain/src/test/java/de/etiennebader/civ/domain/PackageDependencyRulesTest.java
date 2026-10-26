package de.etiennebader.civ.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sichert die Architekturregeln des Kerns ab: Paket-Abhängigkeiten zeigen nur in eine Richtung,
 * und der Kern kennt weder Minecraft noch Fabric.
 *
 * <p>Geprüft werden die {@code import}-Anweisungen der Quelltexte. Das genügt, solange im Kern
 * keine voll qualifizierten Klassennamen ohne Import verwendet werden.
 */
class PackageDependencyRulesTest {

    private static final String BASE_PACKAGE = "de.etiennebader.civ.domain";
    private static final Path SOURCE_ROOT = Path.of("src/main/java/de/etiennebader/civ/domain");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Set<String> FORBIDDEN_PREFIXES = Set.of("net.minecraft.", "net.fabricmc.", "com.mojang.");

    /** Welche Pakete ein Paket verwenden darf; das eigene Paket ist immer erlaubt. */
    private static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = Map.of(
            "shared", Set.of(),
            "actor", Set.of("shared"),
            "law", Set.of("shared"),
            "territory", Set.of("shared", "actor"),
            "land", Set.of("shared", "actor", "territory"),
            "economy", Set.of("shared", "actor"),
            "polity", Set.of("shared", "actor", "law"),
            "fiscal", Set.of("shared", "actor", "law", "territory", "land", "economy", "polity"));

    @Test
    @DisplayName("Pakete verwenden nur erlaubte Pakete")
    void packagesOnlyUseAllowedPackages() {
        List<String> violations = new ArrayList<>();
        for (Path source : sources()) {
            String ownPackage = packageOf(source);
            Set<String> allowed = ALLOWED_DEPENDENCIES.get(ownPackage);
            if (allowed == null) {
                violations.add(source + ": Paket '" + ownPackage + "' hat keine Abhängigkeitsregel");
                continue;
            }
            for (String imported : importsOf(source)) {
                String importedPackage = domainPackageOf(imported);
                if (importedPackage != null && !importedPackage.equals(ownPackage) && !allowed.contains(importedPackage)) {
                    violations.add(source + ": '" + ownPackage + "' darf '" + importedPackage + "' nicht verwenden");
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    @DisplayName("Der Kern kennt weder Minecraft noch Fabric")
    void coreIsFrameworkFree() {
        List<String> violations = new ArrayList<>();
        for (Path source : sources()) {
            for (String imported : importsOf(source)) {
                if (FORBIDDEN_PREFIXES.stream().anyMatch(imported::startsWith)) {
                    violations.add(source + ": " + imported);
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    private static List<Path> sources() {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            List<Path> sources = files
                    .filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !file.getParent().equals(SOURCE_ROOT))
                    .toList();
            assertTrue(!sources.isEmpty(), "Keine Quelltexte unter " + SOURCE_ROOT.toAbsolutePath());
            return sources;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String packageOf(Path source) {
        return SOURCE_ROOT.relativize(source).getName(0).toString();
    }

    private static List<String> importsOf(Path source) {
        try {
            Matcher matcher = IMPORT.matcher(Files.readString(source));
            List<String> imports = new ArrayList<>();
            while (matcher.find()) {
                imports.add(matcher.group(1));
            }
            return imports;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Das direkte Unterpaket des Kerns, z. B. {@code polity}, oder {@code null} für fremde Klassen. */
    private static String domainPackageOf(String imported) {
        if (!imported.startsWith(BASE_PACKAGE + ".")) {
            return null;
        }
        String rest = imported.substring(BASE_PACKAGE.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? null : rest.substring(0, dot);
    }
}
