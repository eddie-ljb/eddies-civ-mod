package de.etiennebader.civ.server.press;

import java.util.List;

/** Eine Zeitung mit Redaktion und Verbreitung. */
public record NewspaperView(String id, String name, String head, List<String> editors, int villagerEditors,
                           List<String> towns, int articles, int readers, long founded) {
}
