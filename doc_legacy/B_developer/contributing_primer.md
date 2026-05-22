# Snabbguide: CI, -Xlint, JaCoCo och taggar

**CI (Continuous Integration)**: en server (t.ex. GitHub Actions) som automatiskt bygger och kör tester vid varje PR/push. Målet: `main` ska alltid vara grön.

**-Xlint:unchecked, -Xlint:deprecation**: kompilatorflaggor som varnar för osäkra generics-casts (unchecked) och användning av utfasade API:er (deprecation). Vi vill hålla varningslistan tom så att riktiga problem syns direkt.

**JaCoCo**: verktyg som mäter testtäckning. Kör `./gradlew jacocoTestReport` och öppna
`build/reports/jacoco/test/html/index.html` för en visuell rapport.

**Skapa tagg vX.Y.Z**: versionsmärkning i git. Exempel:
```bash
git tag v0.1.0
git push origin v0.1.0
```
Taggen kopplas till en exakt commit, så att release alltid kan återskapas.

