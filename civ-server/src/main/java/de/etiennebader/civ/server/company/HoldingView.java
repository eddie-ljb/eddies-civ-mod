package de.etiennebader.civ.server.company;

/** Aktien im Depot mit aktuellem Wert. */
public record HoldingView(String companyId, String company, long shares, long free, String price, String value,
                          long valueUnits) {
}
