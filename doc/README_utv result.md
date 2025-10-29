# README_utv.md — Utvecklarguide & resultat-exportkontrakt

> Senast uppdaterad: _2025-09-10_

Detta dokument sammanfattar hur simuleringen är uppbyggd (actors, flöden) och 
specificerar **resultat-exportens kontrakt** – vilka datakällor som matar CSV, 
namngivningsregler, prioritetsordning och hur summeringar beräknas.

Syftet är att göra felsökning och vidareutveckling snabb och förutsägbar.

---

## 1) Översikt av aktörer

- **SimulationControllerActor**  
  Driver klockan och tickar övriga aktörer.

- **TrainActor**  
  Läser profil (effekt + valfritt x/v), beräknar begärd effekt och 
  skickar `GridModelActor.UpdateTrainPower` varje tick.

- **GridModelActor**  
  Äger `GridModel<Real>`, kör DC-lösaren per tick (`solver.solve`), 
  loggar tågtelemetri till modellen, räknar summer och skriver CSV via
  `ResultCsvWriter`.

- **(Valfritt) TrainAnchorComponent**  
  En icke-Device komponent som simulerar ett tåg på bansegment; kan leverera 
  spänning/ström/effekt/x/v per tick genom getters som `getAbsoluteProgressM()` och
  `getSpeedMS()`.

  *not: jag tror att denna valfrihet komplicerar kontrakten i onödan*

---

## 2) Resultat-export (CSV)

`ResultCsvWriter.append(res, timeSec, step)` producerar en rad i CSV. Första anropet låser headern (kolumnordning) och använder kända tåg-ID:n för stabilitet.

### 2.1 Datakällor

**A. Från DC-lösaren (`GridResult res`)**
- **Nodspänningar**: `res.getLatestNodeVoltage(nodeId)`
- **Device P/I**: `res.getLatestDevicePower(id)`, `res.getLatestDeviceCurrent(id)`
  - Device-ID: `Substation`, `Line`, `TrainLoad`  
  - **Pseudo-device**: `"<deviceId>#brake"` för bromsmotstånd om Device publicerar det

**B. Tågtelemetri (tidsserier i modellen)** — matas av `GridModelActor`:
```java
model.appendTrainTelemetry("Train:<id>", time,
    V_train, I_train, P_train, x_train, v_train);
```

**C. Virtuella per-tåg-serier (icke-device)** — matas av `GridModelActor`:
```java
model.appendResult("Train:<id>#req",   time, 0, P_requested_W);
model.appendResult("Train:<id>#brake", time, 0, P_brake_W);
```

**D. Metadata**  
`GridModel` listar noder, devicer och (om finns) linjekategori.

> **Header-stabilisering**: `GridModelActor` skickar in kända tågnycklar (både `"T1"` och `"Train:T1"`) till `ResultCsvWriter` innan första `append`. Vid installation av ett anchor-tåg seeds även `"Train:<id>"` med en dummy-punkt vid `t = −1e-6`.

---

### 2.2 Namngivningsregler i CSV

- **Nodspänningar**:  
  - Om noden är en **substationbus**: `V[SS0]`, `V[SS1]`, …  
  - Annars: `V[<nodeId>]` (ex. `V[2]`)  
  - **GND** skrivs inte ut.
  
  *Not: borde inte ges `V[deviceId]`?*

- **Substations**: `P[SSi]`, `I[SSi]` (P>0 = matar nätet)

- **Linjer**: `P[L_a_b]`, `I[L_a_b]`  
  - Kategori (om finns) suffixas: `P[L_a_b_<category>]`  
  - Saknas kategori → **ingen** suffix (ex. `P[L_1_2]`)  
  - Om lösaren inte satt linje-P används fallback **I²R**.

  *Not: det är inte uppdrag till resultatskrivare att beräkna linjeeffekt*

- **Tåg**:  
  - `V[T1]`, `P[T1]`, `I[T1]`, `x[T1]`, `v[T1]`  
  - `P_req[T1]` (begärd total: traktion + aux; regen negativ)  
  - `P_brake[T1]` (bromsmotstånd, positivt – off-network)

- **Summer (aggregat)**: se 2.4.

---

### 2.3 Prioritet & fallback per kolumn

*Not: Vad menas med detta avsnitt?*

Låt `altKey("T1") = "Train:T1"` och tvärtom.

- **V[T1]** → `model.getUpdatedTrainVoltages().get("Train:T1")` → `...get(altKey)`
- **P[T1]** → `res.getLatestDevicePower(id)` → `res.getLatestDevicePower(altKey)` → tidsserie `model.getUpdatedDevicePowers().get("Train:T1")`
- **I[T1]** → samma mönster som P
- **x[T1]**, **v[T1]** → `model.getUpdatedTrainPositions()/Speeds().get("Train:T1")` (eller `altKey`) — **måste** matas varje tick av `GridModelActor` (från profil eller anchor)
- **P_req[T1]** → `res.getLatestDeviceRequestedPower(id/altKey)` → `"Train:<id>#req"` tidsserie
- **P_brake[T1]** → device-pseudo `res.getLatestDevicePower(id + "#brake")` → `"Train:<id>#brake"` tidsserie

---

### 2.4 Aggregat (definitioner)

Låt:
- `sumSub`  = Σ P för alla substations (positiv när de **matar**)
- `sumTrain` = Σ P för tåg
  - inkluderar solver-device (`TrainLoad`) **och** (om tillgängligt) senaste värde i `"Train:*"`-serier
- `sumLine` = Σ linjeförluster (≥0)
- `sumBrake` = Σ broms (off-network)
- `sumReq`  = Σ per-tåg begärd effekt

Då skrivs kolumnerna:
- `P_substations_out = sumSub`
- `P_trains = sumTrain`
- `P_lines = sumLine`
- `P_brake = sumBrake`
- `P_req_trains = sumReq`
- `Mismatch = sumSub − sumTrain − sumLine`
- `Balance  = Mismatch − sumBrake`
- `UnderSupply = max(0, sumReq − sumTrain)`
- `UnderReceptivity` (regenspill) =  
  om `sumTrain < 0`: `max(0, (−sumTrain) − absorption)`, där `absorption = max(0, −sumSub)`

Småvärden (< 1e-9) nollas.

---

### 2.5 Samplingsfrekvens & filstorlek

- I `application.conf` (endast en behöver sättas):
  - `dcsim.simulationControl.csvEveryNthStep = 5`
  - `dcsim.export.csvEveryNthStep = 5`
- `ResultCsvWriter` skriver då **var N:te** tick.

  *Not: Låt samplinfrekvensen styras av `dcsim.simulationControl.csvEveryNthStep`. Eliminera
  `dcsim.export.csvEveryNthStep`*

---

## 3) Vanliga symptom & åtgärder

### A) `x[T1]` och `v[T1]` = 0 (ev. första punkten ≈ 25)
**Orsak:** Telemetri matas inte per tick för dessa storheter.  
**Åtgärd (profilstyrt tåg):**
Skicka position/hastighet från `TrainActor`:
```java
grid.tell(new GridModelActor.UpdateTrainPower(
    trainId,
    motKW, brkKW, auxKW,
    profilePosMeters,     // Double (nullable)
    profileSpeedMS        // Double (nullable)
));
```
`GridModelActor` prioriterar då profilens x/v när telemetri loggas.

**Åtgärd (ankartåg):**
Säkerställ att komponenten implementerar
`getAbsoluteProgressM()` **eller** `getXM()` samt `getSpeedMS()`.

---

### B) `P_trains = 0`
**Orsaker att kontrollera:**
- `TrainLoad` finns men får 0-begäran **och** ingen virtuell `"Train:<id>"`-serie matas.  
- Fel nyckel i tidsserien: **måste** vara `"Train:<id>"`, inte bara `"T1"`.

**Åtgärd:**
- För ankare: `GridModelActor` ska append:a virtuell serie per tick (den gör det i anchor-grenen).
- För profilstyrt tåg: se att `TrainLoad.setRequestedComponents()` anropas varje tick med profilens effekt.

---

### C) `P_brake = 0`
**Orsak:** Varken device-pseudo `#brake` eller virtuell serie summeras.  
**Åtgärd:**  
Antingen publicerar Device `id + "#brake"` **eller** (rekommenderat som fallback) låter `ResultCsvWriter` summera `"Train:*#brake"` till aggregatet. (Se patch-förslag i Appendix A om du behöver det.)

---

## 4) Kodmönster (reference)

**TrainActor.onTick (profilstyrt)** — _enkel mall_
```java
double localT = timeSec - departureSec;

double netW = profile.getPowerAtTime(localT).asDouble(); // W (±)
double posM = profile.getPositionAtTime(localT);         // m
double vMS  = profile.getSpeedAtTime(localT);            // m/s

double tractionKW = netW / 1000.0;
double motKW = Math.max(0.0, tractionKW);
double brkKW = Math.min(0.0, tractionKW); // regen ≤ 0
double auxKWout = sameModel ? 0.0 : auxiliaryKW;

grid.tell(new GridModelActor.UpdateTrainPower(trainId, motKW, brkKW, auxKWout, posM, vMS));
```

**GridModelActor** (redan implementerat)
- Tar emot `UpdateTrainPower`, sätter begäran på `TrainLoad`.
- Loggar telemetri: **föredrar profilens x/v** om de fanns i uppdateringen, annars anchor-kinematik (och sist known-position med v=0).
- Loggar `"Train:<id>#req"` och `"Train:<id>#brake"` per tick.

---

## 5) Namn & konventioner (snabbtabell)

| Typ | Exempel | Kommentar |
|---|---|---|
| Substationbus V | `V[SS0]` | Om noden identifierats som substation. |
| Rå nod V | `V[2]` | Används om ingen SS-etikett finns. |
| Substation P/I | `P[SS1]`, `I[SS1]` | P>0 = leverans till nät. |
| Linje P/I | `P[L_1_2]`, `I[L_1_2]` | Kategori opt.: `P[L_1_2_cat]`. |
| Tåg telemetri | `V[T1]`, `P[T1]`, `I[T1]`, `x[T1]`, `v[T1]` | Från `appendTrainTelemetry`. |
| Tåg begäran | `P_req[T1]` | Device requested power eller `"Train:<id>#req"`. |
| Tåg broms | `P_brake[T1]` | Device `#brake` eller `"Train:<id>#brake"`. |
| Aggregat | `P_substations_out`, `P_trains`, … | Se 2.4. |

---

## Appendix A — (valfri) patchidé för aggregatet `P_brake`

Om din Device inte publicerar `id + "#brake"` men du ändå alltid vill att total **P_brake** inkluderar de virtuella serierna:

```java
// I ResultCsvWriter.append(...) efter sumBrake från devices:
Map<String, List<Real>> updP = model.getUpdatedDevicePowers();
if (updP != null) {
    for (var e : updP.entrySet()) {
        String k = e.getKey();
        if (k != null && k.startsWith("Train:") && k.endsWith("#brake")) {
            Real last = lastOf(e.getValue());
            if (last != null) sumBrake += Math.max(0.0, last.asDouble());
        }
    }
}
```

---

## Appendix B — Konfig för CSV-nedsampling

```hocon
dcsim {
  simulationControl {
    tickDurationSec = 1.0
    # Skriv var 5:e tick:
    csvEveryNthStep = 5
  }
}
# eller
dcsim.export.csvEveryNthStep = 5
```

---

## Appendix C — Sanity-kontroller

- **Linjeströmmar ≈ 0 men tåg utbyter effekt** → ankare på substationbus eller topologiproblem.  
- **Substation P överskrider rimligt max** → loggvarning med (E, R, ΔV) och tid.

---

**Kontaktpunkt**: Om något i exporten inte stämmer (t.ex. kolumnnamn eller summer), börja med att verifiera att telemetri‐serierna faktiskt matas per tick (x/v/req/brake) och att nycklarna har formen `"Train:<id>"`.
