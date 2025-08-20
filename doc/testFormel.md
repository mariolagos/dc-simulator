# Test av MathJax i Obsidian

Här är några ekvationer från vår elsimuleringsmodell:

## Ohms lag

Formel för spänning, ström och resistans:

$V = I \cdot R$

## Effekt i en linje

Effektflödet från nod $i$ till nod $j$:

$$
P_{ij} = V_i \cdot \frac{V_i - V_j}{R_{ij}}
$$

## Substationens Norton-ekvivalent

Substationen modelleras som en strömkälla + ledningskonduktans:

$$
I_\text{net} = G \cdot (E - V)
$$

där
- $G = \frac{1}{R_\text{int}}$ är ledningskonduktansen
- $E$ är matningsspänningen (EMF)
- $V$ är nodspänningen

## Effektbalans

Systemets balans beräknas som:

$$
P_\text{substations} - P_\text{trains} - P_\text{lines} = 0
$$

---

✅ Om detta renderar snyggt i Obsidian så har du MathJax fullt fungerande.
