# Documentation Plan for DC-Simulator Project

Detta dokument sammanfattar projektets skriftliga dokumentation: vad varje fil är till för, vilket innehåll den bör ha och aktuell status.

---

## 0. Översikt (status v0.5)

- Migrerat till **Java** (Scala utfasad).
- Akka Typed-aktörer: `SimulationControllerActor`, `GridModelActor`, `TrainActor`.
- Körlägen via `simulationControl.simulationSpeed`: **FAST** och **REAL_TIME**.
- Dynamisk motoreffekt från `PowerProfile` i `TrainActor`.
- Nätlösning i `DcElectricSolver` (Norton-stationer, tåg som strömkällor).
- CSV-export: nodspänningar `V(n)`, per-enhetseffekter `P[id]`, summeringar.
- **Effektbalans**: `P_substations_out ≈ P_trains + P_lines` (Mismatch ≈ 0).

---

## 1. README / README2

**Purpose**  
Översikt och utvecklardokumentation.

**Content**
- Projektstruktur & bygginstruktioner (Gradle/IntelliJ).
- Körning via `DcSimApp`; exempelkonfiguration.
- Start i **FAST** vs **REAL_TIME**.
- Kataloger för in/utdata (profiler, CSV, loggar).
- Kort arkitekturoversikt (aktörer, solver, modeller).
- Länkar till `USER_GUIDE` och övriga dokument.

**Status**
- Två varianter:
  - `README`: generell intro.
  - `README2`: äldre “Basic bus models”.
- **Åtgärd:** Uppdatera båda för Java/Akka och länka `USER_GUIDE`.

---

## 2. USER_GUIDE

**Purpose**  
Praktisk användarguide.

**Content**
- Förutsättningar (JDK, Gradle).
- Bygga & köra i IntelliJ/terminal.
- `simulationControl`:
  - `tickDurationSec` (simtid/steg),
  - `simulationSpeed` = **FAST** | **REAL_TIME**,
  - `stopAfterSteps`.
- Import av power-profiler (Excel) → `PowerProfile.getPowerAtTime()`.
- CSV-kolumner: `V(n)`, `P[deviceId]`, `P_substations_out`, `P_trains`, `P_lines`, `Balance`.
- Felsökning (singulär matris, nollspänningar, loggnivåer).

**Status**
- **Uppdateras** i v0.5.

---

## 3. softwareSpecification

**Purpose**  
Funktionell/teknisk specifikation.

**Content**
- Mål & use-cases (DC-nät: tåg, stationer, linjer).
- Arkitektur (aktörer, solver, export).
- Matematik & teckenkonventioner:
  - Station: \(g=1/R\), \(I=E/R\) (b→a), \(P_\text{sub}=\frac{1}{R}(E\Delta V-\Delta V^2)\).
  - Tåg: \(I=P_\text{req}/\Delta V\) (klampad \(|\Delta V|\)).
  - Linje: förlust \(I^2R\).
  - Balans: \(P_\text{sub} - P_\text{trains} - P_\text{lines}\approx0\).
- Konfigmodell inkl. `simulationSpeed`.
- Prestanda (FAST burst, loggpolicy).

**Status**
- **Uppdateras** för Java/Akka och v0.5-algebra.

---

## 4. Presentation Outline (PowerPoint)

**Purpose**  
Material för intressenter.

**Content**
1. Scope & mål (v0.5).
2. Nätmodell & komponenter.
3. Aktörsflöde (Controller → Train → Grid → CSV).
4. Broms/regen (tecken och lastdelning).
5. Resultat & balans (exempelplots).
6. Demo av FAST-läge.
7. Nästa steg (topologi-koppling tåg→noder, flera tåg, validering).

**Status**
- Finns (`DC-simulator-presentation.pptx`), **utökas** med v0.5-plots.

---

## 5. Patch/version management notes

**Purpose**  
Synk- och versionsstrategi.

**Content**
- Snapshots (zip: `v0.2`, `v0.4`, `v0.5`).
- Patch-baserade uppdateringar.
- Branch/release-taggar, rollback-punkter.

**Status**
- **Uppdateras** med v0.5 och Java-migrering.

---

## 6. PrototypePlan.md *(ny)*

**Purpose**  
Plan för prototypfaser och milstolpar.

**Content**
- Målbilder per version (v0.3…v0.5).
- Definition of Done (ΣP-balans, FAST-läge, dynamisk profil).
- Risker & teststrategi (singularitet, numerik, profiler).

**Status**
- **Skapas** (v0.5 ✅).

---

## 7. CHANGELOG.md *(ny)*

**Purpose**  
Kompakt förändringslogg.

**Content (exempel)**
- **v0.5**: Java/Akka, FAST/REAL_TIME, dynamisk tågeffekt, solver-omskrivning, korrekt `P_sub`, CSV-summeringar.
- **v0.4**: Grundmodell, CSV-writer, enklare profiler.
- **v0.3**: ExcelProfileReader, m.m.

**Status**
- **Skapas**, fylls bakåt till v0.2 där möjligt.

---

## 8. ProgressLog.md *(ny)*

**Purpose**  
Löpande utvecklingsjournal.

**Content**
- Datumstämplade anteckningar: problem → fixar, mätvärden, beslut (Java-byte, speed-enum), plots/skärmdumpar.

**Status**
- **Skapas** och underhålls löpande.

---

## 9. technicalNotes.md *(ny)*

**Purpose**  
Tekniska fördjupningar utanför spec.

**Content**
- Nodal stämpling (G, J), lösare, iteration för P-beroende laster.
- Val av `VDIFF_FLOOR`, numerik, tecken.
- Akka-mönster (timers, burst), loggnivåer.
- Prestanda: minska logg, CSV-intervall, `-Ddcsim.fastBurst`.

**Status**
- **Skapas**, refereras från spec/README.

---

## 10. legalNotice *(ny)*

**Purpose**  
Licens & ansvarsfriskrivning.

**Content**
- Projektlicens (t.ex. MIT/Apache-2.0 eller internt).
- Tredjepartslicenser (Apache Commons Math, Akka).
- Ansvarsbegränsningar; ej för säkerhetskritisk drift.
- Data/sekretess (profiler, kunddata).

**Status**
- **Skapas** och inkluderas i distributionen.

---

## 11. Sammanfattning & nästa steg

- Dokumenten täcker: **design**, **användning**, **resultat**, **ändringshistorik** och **juridik**.
- **Omedelbara åtgärder**
  1. Uppdatera `README`/`USER_GUIDE` med `simulationControl.simulationSpeed` och FAST-läget.
  2. Lägg till: `PrototypePlan.md`, `CHANGELOG.md`, `ProgressLog.md`, `technicalNotes.md`, `legalNotice`.
  3. Bifoga exempel-CSV och kort tolkning av kolumner (P_sub/P_trains/P_lines/Balance).
- **Arkivering**: paketera allt i en zip **v0.5** (kod + dokument + exempeldata).

---
