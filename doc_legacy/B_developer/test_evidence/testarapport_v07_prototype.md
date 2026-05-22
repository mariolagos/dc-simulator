# DC-Simulator – Test Report (v0.7)
## TP-1sub5t: Single Substation, Five Trains, Traction-Only Derating Stress Test

**Date:** 2025-12-xx  
**Version under test:** v0.7  
**Scope:** Traction derating, solver stability, high-load DC stress case

---

## 1. Purpose

Detta testprotokoll dokumenterar elektriskt beteende i en extrembelastad DC-järnvägsmiljö:

- 1 substation (SS0)
- 5 tåg med tidsförskjutna starter
- endast traction (ingen broms, ingen regen)

Testet verifierar:

- **Voltage derating** – korrekt reduktion av tractioneffekt vid låg linjespänning
- **Solver stability** – stabilitet i iterativ lösning under kraftig belastning
- **Power balance** – effektbalans mellan substation och tåg
- **Loss behaviour** – rimliga förluster (I²R) vid hög linjeström

Testet används inför v0.7 och är avsett som regressionstest även för v0.8 (med rörliga noder).

---

## 2. Scenario Description

**Scenario:** `1sub5t` – traction only

- DC-substation **SS0**
    - emf = 900 V
    - internalResistance = 0.1 Ω
    - allowBackfeed = false
- Fem identiska tåg, startförskjutning ~10 s
- Ingen bromsprofil (traction-only)
- Syfte: progressiv lastökning som pressar linjen in i deratingområdet

**Konfigurationsfil:**  
`project/1sub5t/scenario1/application.conf`

---

## 3. Signals of Interest

### Per tåg

| Signal              | Beskrivning                                      |
|---------------------|--------------------------------------------------|
| TrainX.V_node_V     | Linjespänning vid tågets nod                    |
| TrainX.P_net_W      | Faktisk levererad effekt                        |
| TrainX.req_W        | Begärd effekt efter GMA-behandling              |
| TrainX.alpha_derate | Deratingfaktor α(V), 0–1                         |

### Per substation

| Signal        | Beskrivning                                   |
|---------------|-----------------------------------------------|
| SS0.V_V       | Substationspänning                            |
| SS0.P_net_W   | Effekt som levereras till linjen              |
| SS0.I_A       | Ström genom substationen                      |
| SS0.P_loss_W  | I²R-förluster i internresistansen             |

---

## 4. Results Summary — **PASSED**

### ✔ Derating fungerar korrekt
- α sjunker när V_node < V_derate2
- α → 0 när V_node ≤ V_derate1
- P_net_W minskar proportionellt
- inga inversioner eller instabiliteter

### ✔ Solver stabil
- inga överspänningsspikar
- spänningen återgår mot 900 V vid minskad last
- iterationerna konvergerar stabilt

### ✔ Realistiska förluster
- SS0.P_loss_W toppar på ~2 MW
- följer tydlig **I²R-trend** vid 3–5 kA ström

### ✔ Effektbalans
- Σ Train.P_net_W ≈ SS0.P_net_W − SS0.P_loss_W
- inga signfel eller energiläckage

---

## 5. Result Charts

### 5.1 Train node voltage
![Train2.V_node_V](Train2.V_node_V.png)

**Figure: Train2.V_node_V**

Linjespänningen faller i takt med lastökning (ner mot 550–600 V) och återhämtar sig efteråt.

### 5.2 Actual traction power delivered
![TrainX.P_net_W + total](TrainX.P_net_W+total.png)

**Figure: TrainX.P_net_W+total**  

Tågen når 3–4 MW var; toppbelastning ~15–17 MW totalt.  
Derating syns som “avskurna toppar”.

### 5.3 Derating factor
![Train2.alpha_derate](Train2.alpha_derate.png)

**Figure: Train2.alpha_derate**

α faller till ~0.3 i dippen och återgår till 1 när spänningen stiger.

### 5.4 Substation voltage
![SS0.V_V](SS0.V_V.png)

**Figure: SS0.V_V**  

Speglas av V_node. Stabil utan överspänning.

### 5.5 Substation net power
![SS0.P_net_W](SS0.P_net_W.png)

**Figure: SS0.P_net_W**  

Topp ca 2.0–2.2 MW. Noll när inga tåg drar.

### 5.6 Substation losses
![SS0.P_loss_W](SS0.P_loss_W.png)

**Figure: SS0.P_loss_W**  
Toppbelastning 2.2 MW. Ren I²R-kurva.

### 5.7 Substation current
![SS0.I_A](SS0.I_A.png)

**Figure: SS0.I_A**  
Krönt last 4–5 kA, förklarar förlusterna.

---

## 6. Observations and Interpretation

### Belastningssekvens
Varje tågstart sänker spänningen. α begränsar effekten och stabiliserar nätet.

### Toppbelastning
Vid 4–5 tåg faller spänningen → α < 0.5 → traction reduceras naturligt.

### Förluster
Vid flera kA ström och 0.1 Ω internresistans uppstår toppförluster >2 MW — fysiskt korrekt.

### Lösningens robusthet
Ingen divergens. Ingen runaway-ström. Heuristisk derating fungerar som avsett.

---

## 7. Conclusion

TP-1sub5t (traction only) verifierar:

- voltage-deratingmodellen i DcStamps,
- solverns stabilitet,
- realistiska samband mellan last, spänningsfall och förluster,
- korrekt energibalans.

Scenariot används som regressionstest för v0.7 och v0.8  
och utgör baslinje för framtida tester med dynamiska noder.

---

## 8. Appendix

- `application.conf`
- longtable / wide-xlsx

