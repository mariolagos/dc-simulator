# CR03 — Chaining Across Track Sections (“s km+m”)

**Status:** Open  
**Owner:** TBD  
**Impacted areas:** Position model, parsers, aggregators, plotting, DcSimApp main pipeline  
**Goal:** Robustly handle multi‑section track coordinates formatted as **`s km+m`**, enabling deterministic chaining across sections (A→B→C
), unambiguous distance math, and clean conversions between per‑section and absolute positions.

---

## Background
The project uses positions like `s km+m` (section s, kilometer km, meters m). We need to:
- Parse and normalize these positions (e.g., handle m ≥ 1000, decimals truncated when formatting).
- Chain multiple sections with known lengths into one continuous distance axis for simulation, aggregation, and plotting.
- Convert both ways:
    - `(s, km, m)` → absolute distance along the chained route (meters from start)
    - `abs[m]` → `(s, km, m)`

This is foundational for time–position interpolation, power profiles, timetable checks, and graphs.

---

## Scope (MVP)
1. **Position type**
    - `TrackPosition(s: Int, km: Int, m: Double)` with:
        - `parse("s km+m")`, `format()` (truncate m on output), `toMetersInSection()`.
        - `normalized()` (rollover ≥1000 m → km+1, truncate on format).
2. **Section chain**
    - `Section(id: Int, lengthMeters: double)`
    - `SectionChain(sections: List<Section>)` with prefix sums.
    - Conversions: `toAbsolute(TrackPosition) → absMeters`, `fromAbsolute(absMeters) → TrackPosition`.
    - Distance math: `addDistance(TrackPosition, deltaMeters) → TrackPosition` (wrap across sections).
3. **Parsing & validation**
    - Accept `s km+m` where `s, km` are integers; `m` may be decimal **but format/truncate** when printing.
    - Validate that resulting position is inside chain bounds (unless explicitly allowed to clamp).
4. **Integration points**
    - Aggregators and readers accept `TrackPosition` or convert to absolute early.
    - Plotting: provide helpers to label axes by `s km+m` while computing on absolute meters.

---

## Out of Scope (MVP)
- Dynamic topology or runtime re‑sectioning.
- Complex projections; we assume straight cumulative meters.

---

## Design Notes
- **Normalization:** Keep internal `m` as `double`; when formatting, **truncate** (not round).
- **Determinism:** All conversions rely on fixed section lengths (inputs are part of scenario config).
- **Safety:** Clamp or throw on out‑of‑chain references (configurable).

---

## Test Plan
- **Unit:** `parse/format/normalize`, `toAbsolute/fromAbsolute` round‑trip, `addDistance` across section boundaries.
- **Integration:** Aggregated profile computed identically whether inputs are in absolute meters or `s km+m` with the same chain.

---

## Acceptance Criteria
- No ambiguity at section boundaries (e.g., 3 km+1000 m → 4 km+0 m).
- Round‑trips stable within truncation expectations.
- Plot labels show `s km+m` while calculations use absolute meters.

---

## Rollout
1) Introduce `TrackPosition` and `SectionChain` (no behavior change).
2) Update parsers to emit `TrackPosition` (or immediate absolute via chain).
3) Update plotting helpers to support labeling by `s km+m`.
4) Add unit tests; keep performance neutral.

---
