# Villager-Skins nach Beruf

## Wunsch

Dorfbewohner sollen passende Skins für jeden Beruf bekommen: Politiker, Polizist, Soldat, Offizier,
Bauarbeiter, Banker, Unternehmer und so weiter.

## Lösung

Die Skins aus Issue #20 (Hautton, Haarfarbe, Frisur, Rasse) bekommen eine Berufsschicht. Drei Bausteine:

1. **Beruf bestimmen (Server).** `Occupation` (civ-domain, Paket `population`) listet die Berufe; die
   Reihenfolge der Konstanten ist der Vorrang bei mehreren Rollen. `VillagerOccupations` (civ-server) geht
   einmal über alle Gemeinden, Verträge und Register und weist jedem Dorfbewohner den Beruf mit dem höchsten
   Vorrang zu:
   - Ämter jeder Gemeinde, Bundes oder Partei und Parlamentssitze → Politiker
   - Richter und Staatsanwälte (`Judiciary.staff`) → Richter
   - Garnison (`WarRegistry.garrisons`): Soldat und Ausspäher → Soldat, höhere Ränge → Offizier
   - Polizei (`PoliceRegistry.forceOf`) → Polizist
   - Banken (`CreditView.banks`) und der Chef einer Bankfirma → Banker
   - Chef einer Firma → Unternehmer, Leiter einer Zeitung → Journalist
   - Arbeitsverträge (`ContractKind.EMPLOYMENT`) bei einer Firma → Beruf nach Branche
     (`Occupation.employeeOf`): Bau → Bauarbeiter, Wartung → Mechaniker, Bergwerk → Bergmann,
     Forst → Holzfäller, Acker/Obstbau/Schäferei/Viehzucht → Bauer, Fischerei → Fischer, sonst Arbeiter.
   `CivServer.villagerOccupations()` ruft das mit den Registern von Polizei und Krieg auf.
2. **Beruf zum Client bringen.** `VillagerCensus` berechnet die Berufe bei jedem Rundgang (alle 10 s) einmal und
   sendet je Dimension ein `VillagerOccupationsPayload` (UUID → Berufscode) an alle Spieler dort, die das Paket
   empfangen können (`ServerPlayNetworking.canSend`). Spieler ohne Mod bekommen nichts und werden nicht
   abgewiesen. Auch eine leere Liste wird gesendet, damit alte Kleidung verschwindet. Der Client
   (`VillagerOccupationsClient`) hält die Zuordnung und leert sie beim Trennen. Die Berufscodes sind fest
   (`Occupation.code()`), unbekannte Codes ergeben „kein Beruf“.
3. **Kleidung malen (Client).** `OutfitPainter` (ohne Minecraft-Typen, über `Canvas`) färbt das graue Gewand von
   Körper und Beinen der Vanilla-Textur in der Berufsfarbe um (Helligkeit und Falten bleiben), malt Gürtel,
   Krawatte, Abzeichen, Streifen, Karos oder Tarnmuster auf Vorder- und Rückseite und einen Hut auf Kopfoberseite
   und -seiten (Zeile 8 bis 11, die Augen bei Zeile 13 bleiben frei). Haut bleibt unberührt.
   `VillagerSkins.textureOf(uuid, race, occupation)` malt das nach Hautton und Haar und legt das Ergebnis im
   Cache ab; der Schlüssel enthält den Berufscode. Wie bisher bleiben Kinder Vanilla, Resource Packs, die
   `villager.png` ersetzen, wirken weiter, und der Mod liefert keine fremden Texturen mit.

## Neue Berufe ergänzen

1. Konstante in `Occupation` eintragen (neuer, unbenutzter Code; Platz in der Reihenfolge = Vorrang).
2. Regel in `VillagerOccupations.collect` ergänzen.
3. `OutfitPainter.outfitOf` eine Farbgebung geben (und bei Bedarf eine neue `Mark` oder `Hat` zeichnen).
4. Wiki-Tabelle „Berufe und Kleidung“ (Spieler-Alltag) und CHANGELOG fortschreiben.

## Tests

- `OccupationTest` (Codes eindeutig, Vorrang, Branche → Beruf)
- `VillagerOccupationsTest` (der höchste Vorrang gewinnt, Spieler zählen nicht)
- `OutfitPainterTest` (Zivilisten unverändert, Haut und Gesicht bleiben, jeder Beruf sieht anders aus)
