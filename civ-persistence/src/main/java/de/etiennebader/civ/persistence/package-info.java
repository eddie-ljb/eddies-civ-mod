/**
 * Speicheradapter für den Weltzustand: SQLite über JDBC, eigene Schema-Migrationen und ein
 * Audit-Log aller Domain Events. Kennt kein Minecraft; den Dateipfad bestimmt der Aufrufer.
 */
package de.etiennebader.civ.persistence;
