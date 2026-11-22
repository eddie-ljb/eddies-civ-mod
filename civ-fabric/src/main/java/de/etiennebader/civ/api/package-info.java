/**
 * Öffentliche Schnittstelle für fremde Mods (Third-Party-API, #51).
 *
 * <p>Eine Mod meldet eigene Gesetze an, die Spieler per Abstimmung oder Verordnung setzen können
 * (z. B. „ME-Systeme sind verboten“), und fragt an ihren Stellen ab, ob das geltende Recht eine Handlung erlaubt.
 * Alles hier ist stabil gehalten; Klassen außerhalb dieses Pakets sind intern und können sich ändern.
 *
 * <p>Einstieg: {@link de.etiennebader.civ.api.CivModApi} als Fabric-Entrypoint {@code eddies-civ-mod:api},
 * dort {@link de.etiennebader.civ.api.CivApi#registerLaw} aufrufen.
 */
package de.etiennebader.civ.api;
