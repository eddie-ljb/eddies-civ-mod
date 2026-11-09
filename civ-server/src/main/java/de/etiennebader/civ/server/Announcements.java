package de.etiennebader.civ.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sammelt Ankündigungen, bis {@code civ-fabric} sie am Ende des Ticks verschickt.
 */
public final class Announcements {

    private final List<Announcement> pending = new ArrayList<>();

    public void add(Announcement announcement) {
        pending.add(Objects.requireNonNull(announcement, "announcement"));
    }

    /** Alle gesammelten Ankündigungen; danach ist die Liste leer. */
    public List<Announcement> take() {
        List<Announcement> taken = List.copyOf(pending);
        pending.clear();
        return taken;
    }
}
