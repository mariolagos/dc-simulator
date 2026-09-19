# WIP: inläsning av uppmätta fordonsloggar

> **Status:** Parkerat utredningsarbete. Den nuvarande prototypen är inte redo
> att användas med verkliga fordonsloggar. Den bygger fortfarande delvis på
> antagandet att en loggfil motsvarar ett tågläge.

## Bakgrund

Arbetet inleddes utifrån antagandet att uppmätta fordonsloggar skulle användas
för validering av dc-simulatorn. Det behovet verkar inte vara aktuellt just nu.
Koden och resonemanget sparas som WIP eftersom frågan sannolikt återkommer.

Innan arbetet återupptas behöver signalernas betydelse och metoden för
positionering utredas. Frågorna gäller oavsett om bearbetningen senare görs i
BA eller i dc-simulatorn.

## Allmänna observationer

Vissa kolumner verkar vara manuellt tillagda eller ha fått förenklade namn.
Det fungerar för en enstaka fil men blir svårt att hantera för en större mängd
loggar. En framtida import måste därför använda konfigurerbara kolumnnamn och
enheter samt utgå från rubrikerna i originalexporten.

Loggfilen är tabseparerad. Positiv effekt betyder förbrukning och negativ
effekt betyder återmatning.

## Kolumner som verkar användbara

- `Datum`: datum och tid med millisekundupplösning.
- `Local time`: verkar vara manuellt tillagd och saknar millisekundprecision.
  Det är sannolikt bättre att använda `Datum` och hantera tidszonen uttryckligt.
- `v (m/s)`: tågets hastighet.
- `Arr/Dep events`: anger ankomst eller avgång. Signalen kan vara användbar för
  segmentering och positionering, men det är ännu okänt hur händelserna skapas.
- `Average Voltage`: medelspänning, antagligen i V. Kan användas för verifiering.
- `Power [kW]`: total effekt, antagligen i kW. Det behöver klarläggas om
  rubriken är manuellt skapad.
- `MWT,MPW_L_MPW_LV_Mm_T4,MPW_LV_XI_SumLnCurCst`: antagligen tågets
  linjeström i A.
- `MWT,PIS_A_PIS_A_PisTcms_T4,PIS_AV_X_CurStn`: antagligen aktuell station
  eller aktuell PIS-referens.
- `MWT,PIS_A_PIS_A_PisTcms_T4,PIS_AV_X_NextStn`: antagligen nästa station
  eller nästa PIS-referens.
- `MWT,PIS_A_PIS_A_PisTcms_T4,PIS_AV_X_DestStn`: antagligen slutstation eller
  destination.

## Skillnader mot BA-beräknade körningar

`RunCsvFromExcel` läser ett beräknat tågläge. BA-körningar kan behöva
kompletteras med hjälpkraft och med effekt under uppehåll mellan två legs.
Syntetisk trafik kan dessutom skapas med `count`, `headway` och `departure`.

En fordonslogg innehåller däremot verklig tid och uppmätt total effekt. Vid
loggimport ska därför ingen extra hjälpkraft eller syntetisk uppehållseffekt
läggas till. Loggade fordon ska inte heller kopieras med `count` och `headway`
eller tidsförskjutas med `departure`. Alla loggar måste behålla samma verkliga
tidsreferens.

## Positionering

En loggfil motsvarar normalt inte ett enda tåg eller tågläge. Den följer ett
fordon eller tågsätt under en längre period. Samma fordon kan under en dag:

- köra flera tåglägen,
- byta rutt,
- köra fram och tillbaka,
- stå i terminaler mellan tåglägen,
- vända så att tågsättets orientering ändras.

Det räcker därför inte att integrera hastigheten kontinuerligt genom hela
loggfilen.

Ett framtida arbetssätt är att först dela upp fordonsloggen i enskilda
tåglägen. För varje tågläge behövs:

- tågets identitet,
- rutt,
- start- och sluttid,
- initial position eller känd positionsreferens,
- tågsättets orientering,
- avståndet mellan loggutrustningens referenspunkt och den punkt som
  representerar tåget i den elektriska simuleringen.

Positionen kan därefter uppskattas genom att:

1. utgå från en känd startposition,
2. integrera hastigheten med de verkliga tidsstämplarna,
3. använda observerade positionsreferenser som ankare,
4. ta hänsyn till antennens eller givarens placering och tågets färdriktning,
5. fördela skillnaden mellan integrerad och känd position över sträckan mellan
   två ankare,
6. rapportera residualen vid varje ankare.

En omedelbar korrigering vid ett ankare skulle ge ett artificiellt
positionshopp. För elektrisk simulering är det rimligare att fördela
korrigeringen över den föregående sträckan.

Koder som `2851` och `2841` ska tills vidare betraktas som
positionsreferenser, inte nödvändigtvis som stationer. De kan exempelvis
representera en PIS-referens, givare, balis eller annan logisk punkt.

## Frågor som behöver utredas

- Vilka kolumner är manuellt tillagda eller omdöpta?
- Vilka rubriker finns i den ursprungliga loggexporten?
- Vad betyder `CurStn`, `NextStn` och `DestStn` exakt?
- Vad representerar koder som `2851` och `2841`?
- Finns det en officiell mappning från dessa koder till järnvägspositioner?
- Hur skapas eller detekteras `Arr`- och `Dep`-händelserna?
- Vid vilken fysisk eller logisk händelse uppdateras `CurStn`?
- Hur förhåller sig uppdateringen till fysisk passage, ankomst och stopp?
- Finns det en känd tidsfördröjning i signalerna?
- Var på tågsättet sitter antennen eller givaren som ligger till grund för
  positionsreferensen?
- Vilken fysisk punkt bör representera tågets position i den elektriska
  simuleringen?
- Var finns strömavtagaren i förhållande till loggutrustningens referenspunkt?
- Ändras tågsättets orientering vid terminalvändning?
- Hur identifieras början och slutet på varje tågläge?
- Hur ska den uppmätta effekten under vänduppehåll representeras?
- Kan loggarna passera midnatt, och hur definieras då ett gemensamt trafikdygn
  och en gemensam tidsreferens?
- Hur stora positionsresidualer är acceptabla innan en positionsreferens,
  tidsfördröjning eller offset betraktas som felaktig?

## Möjlig framtida struktur

```text
VehicleLogReader
    -> tidsstämplade fordonsprov
    -> RunSegmenter
    -> tåglägen och vänduppehåll
    -> PositionEstimator
    -> run.csv
```

Den nuvarande prototypen bör inte utvecklas vidare förrän signalernas semantik
och positionsreferenserna har klarlagts.
