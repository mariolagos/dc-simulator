# Milestones v0.9 (med ignore-strategi)

## Milestone 9.1 — Solver-paritet (Matlab ↔ Java)

**Mål:** låsa solver-API och kontrakt tidigt.

- [ ] Definiera `SolverResultSnapshot`
- [ ] Implementera Java-solver snapshot
- [ ] Implementera Matlab-solver snapshot
- [ ] Test: Java–Matlab paritet för 3S1T
- [ ] Test märks `@Ignore("Requires MATLAB engine")`

> Regeln: testet ska kompileras, men får ignoreras i CI tills MATLAB finns.

---

## Milestone 9.2 — Track graph + junctions

**Mål:** korrekt spårrepresentation.

- [ ] Track graph-modell (segment + junction)
- [ ] Symphony uppdaterad med förgrening
- [ ] NetBuilder stödjer graph-baserade tracks
- [ ] E2E test: Symphony med förgrening (rimlighetskontroller)

---

## Milestone 9.3 — Return-nät och jordkoppling

**Mål:** korrekt elektrisk retur.

- [ ] Explicit return-rail nät
- [ ] Tåg b-nod kopplas till return-rail
- [ ] Substationers retur kopplas till return-rail
- [ ] Regressiontest: inga NaN/Inf, rimliga spänningar

---

## Milestone 9.4 — Tågrörelse på graph

**Mål:** dynamisk topologi med rörelse.

- [ ] Tåg flyttar över track-segment
- [ ] Junction-val hanteras deterministiskt
- [ ] Dynamiska linjer uppdateras korrekt
- [ ] E2E test: tåg flyttar → V ändras rimligt

---

## Milestone 9.5 — Stabilisering

**Mål:** v0.9 kan stängas.

- [ ] Alla v0.9 tester gröna (förutom explicit `@Ignore`)
- [ ] 3S1T golden case stabil
- [ ] Kort v0.9 statusnot i dokumentation

---

## Testpolicy v0.9

- Endast **ett fåtal E2E-tester**
- E2E testar invarians och rimlighet, inte exakta tal
- Matlab-paritetstest:
    - finns i repo
    - ignorerat i CI
    - används manuellt vid behov
- Inget nytt test ska blockera funktionell utveckling utan mycket goda skäl

---
