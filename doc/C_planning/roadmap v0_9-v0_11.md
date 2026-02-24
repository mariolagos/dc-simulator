# Roadmap v0.9 – v0.11

Detta dokument definierar scope, mål och gränser för versionerna v0.9–v0.11.
Syftet är att styra utvecklingen, undvika scope creep och tydliggöra när arbete ska stoppas.

---

## v0.9 — Funktionalitet färdig (core simulation)

### Övergripande mål

v0.9 ska slutföra all **funktionell simulering**:

- korrekt nätmodell
- korrekt spårsystem
- korrekt tågrörelse
- korrekt elektrisk retur (return rail / ground)

Efter v0.9 ska simulatorn kunna användas för realistiska nätfall,
även om verktygsstöd, rapportering och polish saknas.

### Scope v0.9

#### 1. Nätverk och spårsystem

- Track-system som first-class concept (inte bara trackId på noder)
- Stöd för:
    - flera spår (K1/K2/K3)
    - förgreningar/junctions
- Symphony används som **golden reference network**, utökat med förgrening

#### 2. Tredje spår / retur-nät

- Explicit modellering av:
    - catenary (matning)
    - rail/return-nät
- Tåg och stationers jord/b-nod kopplas till **return-nätet**, inte implicit global ground
- Global ground används endast som elektrisk referens

#### 3. Korrekt tågrörelse

- Tåg flyttar sin elektriska anslutning längs spår
- Rörelse över segmentgränser och förgreningar
- Dynamisk topologi uppdateras deterministiskt

#### 4. Matlabisering (solver-paritet)

- Pipeline:
    - `GridModel → Java solver → logging`
    - `GridModel → Matlab solver → logging`
- Java–Matlab–Java-paritet verifieras för 3S1T
- Resultat jämförs via normaliserad snapshot (inte via logging)

#### 5. Funktionell teknisk skuld

- Endast skuld som blockerar funktion tas i v0.9
- Ingen kosmetisk refaktor
- Inga stora rensningar

### Explicit utanför scope (v0.9)

- long → wide konvertering
- rapportering
- projekt/scenario-hantering
- grafisk tidtabell
- större kodstädning
- dokumentationsgenomgång

---

## v0.10 — Projektstöd och användbarhet

### Övergripande mål

Göra simulatorn praktiskt användbar i projektarbete och batch-körningar.

### Scope v0.10

- long → wide CSV-konvertering
- rapportering (summary per scenario: min V, max I, losses, etc.)
- hantering av projekt och scenarion
- hash-baserad körning och återanvändning av tidigare resultat
- återintroduktion av **grafisk tidtabell**

### Explicit utanför scope (v0.10)

- större API-brott
- djup kodrengöring
- release-arbete

---

## v0.11 — Konsolidering och release

### Övergripande mål

Städa, konsolidera och leverera en tydlig release.

### Scope v0.11

- rensning av legacy och dubletter
- skärpning eller borttagning av mildrade tester
- uppdatering av dokumentation (softwareSpecification, dev-notes)
- tydliggörande av canonical paths
- release process och versionering

---

