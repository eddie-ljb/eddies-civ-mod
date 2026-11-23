# Firmen: Grundstückspflicht, Maschinen, Wartung, Zusatzwaren

**Admin-Option** `company_plot_required` (Standard `false`): ist sie an, produziert eine Firma nur, solange sie in einer
Gemeinde ein Grundstück hält. Dorfbewohner-Firmen kaufen sich eins (`VillagerBusiness`, Voraussetzung: ein Grundstück
steht zum Verkauf).

**Maschinen** (`MachineType`, 2 je Branche, 20 Typen, jeweils eigener Block mit eigener Textur): `/civ firma maschine
kaufen <Firma> <Typ>` (Firmenkasse) gibt den Block; er zählt nur, wenn die Leitung ihn auf einem Firmengrundstück
aufstellt. Ertrag +Prozent je Stufe (Tabelle in `MachineType`), `ausbauen <Firma> <Nr>` bis Stufe 5. Tägliche
Abnutzung; bei 100 % steht die Maschine still.

**Wartung:** `/civ firma maschine warten <Firma> <Nr> [selbst|firma]`. „selbst“ kostet die Kasse den Wartungspreis, „firma“
beauftragt eine Firma der neuen Branche `wartung` (mit Personal) gegen 125 % Aufpreis, der an sie geht.

**Arbeitsweg:** Angestellte gehen tagsüber zu den Maschinen; der Maschinenbonus gilt je Anteil anwesender
Angestellter von 50 % bis 100 %. Dorfbewohner-Firmen kaufen, bauen aus und warten Maschinen selbst (virtuell).

**Zusatzwaren:** `/civ firma einlagern <Firma>` legt die Ware in der Hand ins Lager, wenn sie thematisch zur Branche
passt (`Branch.extras()`, z. B. Kaktus bei `acker`); sie wird wie das übrige Lager verkauft.

**Menü:** `/civ firma maschine menue <Firma>` oder im Firmenmenü der Knopf „Maschinen“: Klick baut aus, Rechtsklick wartet
selbst, Schleichen + Klick beauftragt die Wartungsfirma; darunter kaufst du neue Typen der Branche.

**Arbeitsweg:** Alle 2 Sekunden werden Angestellte tagsüber zu ihrer Maschine geschickt (Arbeitsplätze alle 10 s aus den
Verträgen eingelesen). Kommen sie nach 5 Versuchen nicht an, werden sie neben die Maschine versetzt und gelten als anwesend.

Gespeichert in `machines.tsv`. Nicht umgesetzt: Verlagern fertig gebauter Maschinen zwischen Firmen.
