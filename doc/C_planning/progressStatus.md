# progressStatus.md
**Document Level:** C (Planning)  
**Last updated:** 2025-10-10

---

## 1. Overview
Execution tracker aligned with `prototypePlan_v1_1.md` and `testPlan.md`.

## 2. MUST Items (toward Deliverable 0.9)
- [ ] W0 Stabilize: 6 blocking unit tests green
- [ ] W1 Chainage (s km+m): parser + SectionTable + node mapping
- [ ] W2 Double track: two parallel tracks, directional trains, optional crossover
- [ ] W3 Profile import: CSV/XLSX per-train profiles aligned to tick
- [ ] W4 Reporting: long-table schema locked; summary metrics
- [ ] W5 Case run: one real case **or** 3subs2train end-to-end

## 3. Detailed TODOs
### W1 — Chainage
- [ ] Implement ChainageParser with strict format (`S 12+345`, `12+000`, `0+50`)
- [ ] Map chainage → SectionTable → node indices (monotonic, unique)
- [ ] Unit tests: valid/invalid strings, round-trip meters↔chainage
- [ ] Update USER_GUIDE (§coordinates) and terms

### W2 — Double Track
- [ ] Extend TopologyBuilder for two tracks with explicit track IDs
- [ ] Directional running and isolation between tracks
- [ ] Optional crossover segments (enable/disable in config)
- [ ] Integration test: expected voltage drops per track (no ghost coupling)

### W3 — Profile Import
- [ ] CSV/XLSX reader with header validation
- [ ] Resample/align to simulation tick; warn on gaps/out-of-range
- [ ] Attach profile to Train instances via config
- [ ] Visible in outputs; regression baseline

## 4. Risks & Mitigations (live)
- Time pressure → Parallelize W0/W1/W3; prioritize MUST vs SHOULD
- Chainage edge-cases → strict parsing + clear errors
- Profile misalignment → resampling; tolerance docs in testPlan §6

## 5. Links
- Prototype Plan: `prototypePlan_v1_1.md`
- Software Spec: `softwareSpecification_v1_1.md`
- Test Plan: `testPlan.md`

## Test Framework Roll-out (Checklist)
- [ ] README_dev.md uppdaterad med JUnit 4-konventioner
- [ ] testPlan.md uppdaterad (strategi, tracks-matris, CI-jobs)
- [ ] CI-jobs definierade: unit / integration / regression
- [ ] Baseline-mapp initierad och refererad i tester
- [ ] README.md länkar till detaljerad testdokumentation
