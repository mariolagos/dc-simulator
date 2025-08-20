# Train Load Model – Constant Power with Nonlinearities

## 1. Modell

Låt \(v = V_{\text{from}} - V_{\text{to}}\) vara linjespänningen vid strömskena/pantograf.

**Önskad effekt från profil (före begränsningar):**
- \(P_{\text{req}}(t)\) > 0 motordrift, < 0 broms/återmatning.

**Hjälpkraft (aux):**
- Under dwell (uppehåll): dra \(P_{\text{aux}}\) *utöver* primär effekt:  
  \[
  P_{\text{target}} = P_{\text{req}} + P_{\text{aux}}
  \]
- I rörelse: kan ingå i samma modell eller hanteras separat. Här antas samma modell.

**Strömbegränsningar & spänningsfönster:**
- \(|i| \le I_{\max}^{\text{mot}}\) (drag) respektive \(|i| \le I_{\max}^{\text{regen}}\) (återmatning).
- Spänningsgränser:
    - Lågnivå: under \(V_{\min}\) kan effekten inte hållas ⇒ levererad effekt begränsas av \(i_{\max}\cdot v\).
    - Återmatning: över \(V_{\text{block}}\) (≈ likriktar-EMF) blockeras backfeed ⇒ överskjutande bromseffekt går till bromsmotstånd.

**Återmatningsdelning (linje vs. bromsmotstånd):**
\[
\alpha(v) = \text{clip}\!\left(\frac{V_{\text{block}}-v}{V_{\text{block}}-V_{\text{cut}}},\,0,\,1\right)
\]
- Linjeeffekt: \(P_{\text{line}} = \alpha\,P_{\text{brake}}\) (negativ).
- Bromsmotstånd: \(P_{\text{br}} = (1-\alpha)\,P_{\text{brake}}\).

**Konstant-effekt som Norton kring driftpunkt \(v^*\):**
\[
G = \frac{|P_{\text{line}}|}{(v^*)^2}, \quad I = \frac{P_{\text{line}}}{v^*}
\]

---

## 2. Algoritm – per tidssteg och Newton-iteration

1. Läs \(v^*\) ur `xVector` (eller fallback till \(V_{\text{nom}}\)).
2. Beräkna \(P_{\text{target}}\) (profil ± auxiliaries + dwell-logik).
3. **Motordrift** \(P_{\text{target}}>0\):
    - “Önskad” ström \(i=P_{\text{target}}/v^*\).
    - Klipp \(|i|\) till \(I_{\max}^{\text{mot}}\).
    - \(P_{\text{line}} = i\cdot v^*\).
4. **Broms/återmatning** \(P_{\text{target}}<0\):
    - “Önskad” linjeström \(i=P_{\text{target}}/v^*\) (negativ).
    - Klipp \(|i|\) till \(I_{\max}^{\text{regen}}\).
    - Dela enligt \(\alpha(v^*)\):  
      \[
      P_{\text{line}} = \alpha\,i\,v^*, \quad P_{\text{br}}=(1-\alpha)\,i\,v^*
      \]
5. Linjärisera och **stämpla** Norton \(G,I\) för *linjedelen* \(P_{\text{line}}\).
6. Efter lösning: beräkna **i\_net**, **P\_line**, **P\_br**, **P\_aux** och lagra i `GridResult`.

---

## 3. Java-skiss

```java
private Real iMaxMot = Real.fromDouble(300.0);
private Real iMaxRegen = Real.fromDouble(300.0);
private Real vCut = Real.fromDouble(850.0);
private Real vBlock = Real.fromDouble(1000.0);
private Real vMin = Real.fromDouble(450.0);
private Real auxKW = Real.fromDouble(50.0);
private boolean dwell = false; // styrs via tidtabell

// Helper
private static double clip(double x, double a, double b){ return Math.max(a, Math.min(b, x)); }

// Compute current and split between line and brake resistor
public Real computeCurrent(Real fromV, Real toV) {
    double v = Math.max(1e-6, fromV.minus(toV).asDouble());
    double Preq = requestedPower.asDouble();
    double Paux = dwell ? auxKW.asDouble() : 0.0;
    double Ptar = Preq + Paux;

    double iNet, Pline, Pbr = 0.0;

    if (Ptar >= 0.0) {
        double i = clip(Ptar / v, 0.0, iMaxMot.asDouble());
        iNet  = i;
        Pline = i * v;
    } else {
        double i = clip(Ptar / v, -iMaxRegen.asDouble(), 0.0);
        double a = (vBlock.asDouble() - v) / Math.max(1e-6, (vBlock.asDouble() - vCut.asDouble()));
        double alpha = clip(a, 0.0, 1.0);
        double Ptot = i * v;
        Pline = alpha * Ptot;
        Pbr   = (1.0 - alpha) * Ptot;
        iNet  = Pline / v;
    }

    this.current = Real.fromDouble(iNet);
    return this.current;
}

// Linearized Norton equivalent stamping
@Override
public void stamp(RealMatrix Y, RealVector J, RealVector X,
                  int timestep, Map<Integer,Integer> idx) {

    int i = idx.get(fromNode), j = idx.get(toNode);
    double vStar = 750.0;
    if (X != null) {
        double vi = X.getEntry(i), vj = X.getEntry(j);
        vStar = Math.max(1e-3, vi - vj);
    }

    double Preq = requestedPower.asDouble();
    double Paux = dwell ? auxKW.asDouble() : 0.0;
    double Ptar = Preq + Paux;

    double Pline;
    if (Ptar >= 0.0) {
        double iVal = clip(Ptar / vStar, 0.0, iMaxMot.asDouble());
        Pline = iVal * vStar;
    } else {
        double iVal = clip(Ptar / vStar, -iMaxRegen.asDouble(), 0.0);
        double a = (vBlock.asDouble() - vStar) / Math.max(1e-6, (vBlock.asDouble() - vCut.asDouble()));
        double alpha = clip(a, 0.0, 1.0);
        Pline = alpha * (iVal * vStar);
    }

    double G = Math.abs(Pline) / (vStar * vStar);
    double I = (vStar > 0.0) ? (Pline / vStar) : 0.0;

    Y.addToEntry(i, i, G);
    Y.addToEntry(j, j, G);
    Y.addToEntry(i, j, -G);
    Y.addToEntry(j, i, -G);
    J.addToEntry(i,  I);
    J.addToEntry(j, -I);
}
