# Issue #1: Villager behaviour and political activities

## Problem
Gemeinden, Bünde und Firmen kamen zum Stillstand, sobald kein Spieler mehr als Bürgermeister,
Bundesvorsitz oder Firmenchef zur Verfügung stand: Es gab weder eine kommissarische
Dorfbewohner-Regierung noch eine Möglichkeit für Dorfbewohner, Gebiet zu beanspruchen oder
Bündnisse aktiv mitzugestalten.

## Lösung
Ein neuer, standardmäßig aktiver Schalter `villager_politics_enabled` (Serverkonfiguration)
aktiviert drei Mechaniken für Gemeinden ohne Spieler-Bürgermeister:

1. **Kommissarische Regierung:** `PolityView#mayor(boolean villagerPolitics)` fällt, wenn kein
   Bürger vorhanden ist, der kein Dorfbewohner ist, auf den dienstältesten Dorfbewohner unter
   *allen* Einwohnern zurück (nicht nur Bürgern – Dorfbewohner werden nie selbst Bürger). Dieser
   ändert täglich höchstens eine Rechtsregelung nach derselben Persönlichkeitslogik, die auch
   Dorfbewohner-Abgeordnete im Parlament verwenden (`CivApplication#villagerLegislator()`,
   wiederverwendet aus `ParliamentSession`).
2. **Gebietswachstum:** `VillagerService#autoClaimChunk` beansprucht für jede so regierte
   Gemeinde täglich höchstens einen freien Nachbar-Chunk ihres Kerngebiets, solange das
   Claim-Limit (`ServerSettings#claimLimit`) nicht erreicht ist – über dieselbe
   `ClaimChunk`-Use-Case, die auch Spieler-Befehle nutzen, inklusive künftiger Claim-Kosten
   (Issue #3).
3. **Bündnisse:** `FederationService#autoDecideInvitations` lässt einen kommissarischen
   Dorfbewohner-Bürgermeister offene Bund-Einladungen selbst entscheiden, gewichtet nach
   `Personality#order() + Personality#belonging()` (Ordnungsliebende und Heimatverbundene treten
   eher bei). Wird aus `CivServer#endOfDay` aufgerufen.

## Implementierung
- `civ-domain/.../polity/PolityView.java`: `mayor(boolean)` überarbeitet (siehe oben).
- `civ-application/.../CivApplication.java`: `villagerLegislator()`-Zugriff auf die bereits für
  das Parlament verwendete `PersonalityLegislator`-Instanz.
- `civ-server/.../villager/VillagerService.java`: `governAutonomously(Day)` (Rechtsänderung),
  `autoClaimChunk(Day)` (Gebietswachstum), beide aus `endOfDay(Day)` aufgerufen, wenn
  `settings.villagerPolitics()`.
- `civ-server/.../federation/FederationService.java`: `autoDecideInvitations(Day, boolean)`.
- `civ-server/.../CivServer.java`: ruft `autoDecideInvitations` im Tagesabschluss auf.
- `civ-server/.../ServerConfig.java`, `ServerSettings.java`: neuer Konfigurationswert
  `villager_politics_enabled` (Standard `true`).

## Bewusst nicht umgesetzte Teilaspekte
Das Issue ist sehr breit angelegt; folgende Teile wurden bewusst zurückgestellt, damit das
Gelieferte real, getestet und von überschaubarem Risiko ist, statt vieler halbfertiger Stubs:

- **Dorfbewohner gründen brandneue Gemeinden oder Bünde von Grund auf.** Das bereits
  umgesetzte Beitreten per Einladung deckt "Bünde schließen" ab; das eigenständige *Gründen*
  eines Bundes erfordert eine automatisch generierte Name/Ebenen-Wahl ohne erkennbares
  Spielsignal dafür und wurde als zu beliebig eingeschätzt, um es sinnvoll zu automatisieren.
  Ebenso das Gründen einer komplett neuen Gemeinde "aus dem Nichts" (wo? mit welchem
  Gemeindehaus?) – das bräuchte eine eigene, größere Entscheidung über den Weltzustand.
- **Dorfbewohner gründen/leiten eigene Firmen, sind an der Börse aktiv, stellen Personal ein.**
  `CompanyService#found` verlangt aktuell einen `PlayerRef` als Gründer/Inhaber; Firmenbesitz auf
  `ActorRef` (und damit auf `PersonRef`) auszuweiten ist ein eigenständiger, nicht trivialer
  Schnitt durch `CompanyService`, `Enterprise` und die Börsenanbindung. Das würde dieses PR
  sprengen und ist ein guter Kandidat für ein eigenes, fokussiertes Issue/PR.

## Tests
`civ-server/.../villager/VillagerServiceTest.java`, neuer Test
`villagerPoliticsWhenMayorless`:
- Prüft, dass nach Austritt des einzigen Spieler-Bürgers der dienstälteste Dorfbewohner
  kommissarisch Bürgermeister wird (`mayor()` leer, `mayor(true)` liefert ihn).
- Prüft, dass die Gemeinde über mehrere Tage automatisch bis genau zum Claim-Limit wächst.
- Prüft, dass eine Bund-Einladung durch den kommissarischen Dorfbewohner-Bürgermeister
  entschieden wird (`FederationService#belongsTo` liefert danach einen Wert).

`JAVA_HOME=/opt/jdk25 ./gradlew :civ-domain:test :civ-application:test :civ-server:test` –
**BUILD SUCCESSFUL**, keine Fehlschläge (civ-fabric wurde für dieses PR nicht verändert und
daher nicht gebaut).
