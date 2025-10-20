# README_dev.md – tillägg / ändringar

## Now / How to test
- Kör alla tester lokalt
  ```bash
  ./gradlew test
  ```
- Kör snabb “smoke”
  ```bash
  ./gradlew test     --tests org.dcsim.unit.MiniMiniSolverTests     --tests org.dcsim.unit.StampMiniTests     --tests org.dcsim.unit.SolverMiniTests     --tests org.dcsim.unit.IslandSeparationMiniTests
  ```

## Verbosity (opt‑in)
- Default: tyst körning.
- Aktivera extra loggar när du felsöker:
  ```bash
  ./gradlew test -Ddcsim.verbose=true
  ```

## Graph export (opt‑in)
- Skriv ut DOT-filer endast när begärt via flagga (exempel):
  ```bash
  ./gradlew test -Ddcsim.graph=topology
  ```
- Rendera till PNG (kräver Graphviz `dot`):
  ```bash
  dot -Tpng build/graphs/<name>.dot -o build/graphs/<name>.png
  ```

## Java & OS
- Stödda CI-miljöer: Java 17 (och ev. 21) på Linux/Windows.
