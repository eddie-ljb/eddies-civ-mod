# Issue #12: Eigene Flaggen, Banner, Wahlplakate und Leuchtreklamen

## Problem

> Es sollen eigen erstellte Flaggen und Banner / Wappen über die Webseite als .PNG hochgeladen werden
> können und für die jeweilige Einheit (Gemeinde, Land, Nation, usw.) definiert werden können. Diese
> sollen dann auch Ingame einsehbar und setzbar sein. Gerne auch als neue Items und setzbare Blöcke
> statt als Minecraft Banner. Dies soll auch für Bünde und Unternehmen möglich sein sowie es soll auch
> Schilder, Wahlplakate und Leuchtreklamen geben, die genauso funktionieren.

Bisher kannte der Mod nur `Flag` (Grundfarbe + Banner-Musterschichten, siehe `civ-fabric/.../content/Flags.java`)
als Hoheitszeichen. Ein eigenes, frei gezeichnetes Bild (Wappen) ließ sich nicht hinterlegen, und es gab
keine Möglichkeit, Wahlplakate oder Leuchtreklamen mit eigenem Bildmaterial zu versehen.

## Lösung

Ein neuer, von `Flag` unabhängiger Mechanismus `CustomImage` (civ-domain, Paket `civic`) ergänzt das
bestehende Flaggen-System:

- **`ImageKind`**: `WAPPEN`, `WAHLPLAKAT`, `LEUCHTREKLAME`.
- **`CustomImage`**: Metadatensatz (Polity, Art, SHA-256-Hash der PNG-Bytes, Breite/Höhe, Hochladedatum).
  Die eigentlichen Bilddaten liegen als Datei auf der Platte, benannt nach dem Hash — das Register
  bleibt dadurch klein und mehrfach hochgeladene identische Bilder teilen sich eine Datei.
- Ein neues Wappen **ersetzt** das alte (es gibt nur eines je Polity); Wahlplakate und Leuchtreklamen
  dürfen bis zu `CustomImage.MAX_PER_KIND` (10) gleichzeitig nebeneinander bestehen.
- Löst sich eine Polity auf (`CivicRegistry.forget(...)`), werden auch ihre Bilder entfernt.

## Implementierung

- **civ-domain** (`civic/CustomImage.java`, `civic/ImageKind.java`): Wertobjekte, Größenvalidierung
  (max. 128×128 Pixel — größer würde ohnehin nur verkleinert dargestellt).
- **`CivicRegistry`**: `addImage(...)` (liefert verdrängte Bilder beim Wappen-Wechsel zurück, damit der
  Aufrufer verwaiste Dateien aufräumen kann), `removeImage(...)`; `CivicView.imagesOf(...)`/`image(...)`
  als lesende Default-Methoden, analog zu `flagOf(...)`.
- **civ-application** (`CivicUseCases.uploadImage(...)`/`removeImage(...)`): prüft Berechtigung
  (Bürgermeister/Vorsitz der jeweiligen Einheit) und ruft die Domänenlogik auf.
- **civ-persistence**: neue Migration `V021__polity_image.sql` (Tabelle `polity_image`), Laden/Speichern
  in `CivicJusticeTables.java`.
- **civ-server**:
  - `CivicService.uploadImage(...)/removeImage(...)/imagesOf(...)/image(...)` als Fassade für die
    Web-Schicht.
  - `CivServer.imagesDirectory()`: Ordner `<Weltdaten>/images/`, in dem die PNG-Bytes nach Hash
    abgelegt werden.
  - `WebApi`: `POST /api/image` (Upload, inkl. Validierung: eingeloggt, PNG-Format, Größe ≤ 128×128,
    Zielpolity existiert und Aufrufer darf sie vertreten), `GET /api/image?id=...` (liefert die PNG-Bytes
    mit Cache-Header), `POST /api/image/remove`. Die Polity-Ansicht (`GET /api/polity/...` o. Ä.) liefert
    zusätzlich `wappenImage` (aktuelles Wappen-Bild, falls vorhanden) und `images` (alle weiteren Bilder).
  - `index.html`: Formular "Eigenes Wappen, Wahlplakat oder Leuchtreklame hochladen" (Art wählen,
    PNG-Datei wählen, hochladen).

### Umfang und bewusst nicht umgesetzte Teile

- **Unternehmen (Firmen)**: `CustomImage` ist an `PolityId` gebunden; Firmen (`CompanyService`) haben
  derzeit keine `PolityId`-kompatible Kennung im Domänenmodell. Firmen-Bilder sind deshalb in dieser
  PR **nicht** umgesetzt — das würde entweder eine Erweiterung des Domänenmodells um einen
  `PolityId`-fähigen Firmen-Bezug oder einen zweiten, parallelen Speichermechanismus erfordern. Beides
  ist ein separater, bewusst ausgeklammerter Schritt.
- **Ingame-Anzeige als echtes Bild**: `Flags.java` übersetzt Banner ausschließlich über Grundfarbe plus
  vanilla Muster-Schichten (`BannerPatternLayers`) — es gibt keinen Mechanismus, ein beliebiges PNG
  direkt auf einen Block/Item zu zeichnen. Eine echte Ingame-Darstellung des hochgeladenen Bildes wäre
  über eine **dynamisch zur Laufzeit registrierte Textur** möglich (Minecraft/Fabric erlaubt das Laden
  eines `NativeImage` aus Bytes und die Registrierung über `TextureManager.register(...)` mit einem
  generierten `Identifier`, kombiniert mit einem eigenen `BlockEntityRenderer`, der diese Textur auf ein
  Quad zeichnet). Das ist technisch machbar, aber ein eigenständiges, nicht triviales Stück Client-Code
  (Renderer, Modell, Netzwerk-Sync der Bild-Kennung zum Client), das den Rahmen dieser bereits mehrfach
  unterbrochenen Session gesprengt hätte. Stattdessen ist das Bild vorerst **nur über die Webseite**
  einsehbar (`GET /api/image?id=...`, in der Gemeinde-/Land-/Nations-Ansicht verlinkt). Dies ist die
  konkrete, empfohlene Richtung für eine Folge-PR.
- **Eigene Items/Blöcke statt Minecraft-Banner** ("Gerne auch…" — als Wunsch, nicht als Pflicht
  formuliert): ebenfalls zurückgestellt, da es von der oben beschriebenen Renderer-Arbeit abhängt.
- **Schilder** im Sinne von Straßen-/Ortsschildern sind bereits Gegenstand von Issue #11 und dort
  umgesetzt; dieses Issue beschränkt sich auf Wappen/Wahlplakat/Leuchtreklame.

## Tests

- `civ-domain`: neue `CustomImageTest.java` — Größenvalidierung, Wappen-Ersetzung samt verdrängtem
  Bild, Limit für Wahlplakate/Leuchtreklamen, Entfernen, Aufräumen beim Auflösen einer Polity.
- `./gradlew :civ-domain:test :civ-application:test :civ-server:test :civ-fabric:compileJava :civ-fabric:test`
  → **BUILD SUCCESSFUL**, inklusive bestehender `TranslationCoverageTest` (neue Sprachschlüssel für
  Upload/Validierungs-Meldungen in `de_de.json`/`en_us.json`).
