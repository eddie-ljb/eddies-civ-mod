package de.etiennebader.civ.server.company;

/** Eine offene Order; {@code side} ist BUY oder SELL. */
public record OrderView(int number, String company, String trader, String side, long quantity, long remaining,
                        String limit, long units, long placedOn, boolean mine) {
}
