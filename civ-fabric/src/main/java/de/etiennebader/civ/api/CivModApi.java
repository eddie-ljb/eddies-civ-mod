package de.etiennebader.civ.api;

/**
 * Fabric-Entrypoint für fremde Mods. In der {@code fabric.mod.json} der Mod eintragen:
 *
 * <pre>{@code
 * "entrypoints": { "eddies-civ-mod:api": [ "com.example.MyModCivApi" ] }
 * }</pre>
 *
 * <p>{@link #onCivApi()} läuft beim Start von Eddies Civ Mod, also unabhängig von der Ladereihenfolge der Mods.
 * Hier ist der richtige Ort, um Gesetze anzumelden; später (nach Serverstart) ist das nicht mehr möglich.
 */
@FunctionalInterface
public interface CivModApi {

    void onCivApi();
}
