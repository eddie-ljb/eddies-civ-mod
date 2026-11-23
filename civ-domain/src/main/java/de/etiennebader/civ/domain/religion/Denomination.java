package de.etiennebader.civ.domain.religion;

import java.util.Objects;

/**
 * Eine Konfession innerhalb einer Religion, z. B. eine Strömung mit eigenem Namen.
 */
public record Denomination(String name) {

    public static final int MAX_NAME = 32;

    public Denomination {
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("Name der Konfession leer oder zu lang: " + name);
        }
    }
}
