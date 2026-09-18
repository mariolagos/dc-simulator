# Diagram source coordinates

`dcCheck` supplies grid railway coordinates and configured track junctions to
the electrical SVG exporter. Other callers retain the previous overloads and
can opt into `RouteSchematic.Context` explicitly.

Ordinary locations show the source railway position and section-local model
metres on separate lines. A physical junction shows its distinct railway
references; it does not claim a unique model-metre value across sections.

SVG drawing columns are grouped only when a selected electrical branch joins
the exact endpoints of a configured track junction and has
`0 < resistance_ohm_per_m <= 1e-12` and total resistance `<= 1e-8 ohm`.
Those connectors are omitted from the drawing. This does not merge solver
nodes, remove branches, or change route validation. DOT retains the explicit
electrical topology. Non-ideal or undeclared connectors remain visible.

Station events are loaded from each configured route-data worksheet:

- `dataType` is `P` (case-insensitive).
- `fromStationAbbr` and `toStationAbbr` are equal and nonblank.
- Railway coordinates come from `trackSection`, `bisKm`, `bisMeter`, and
  optional `trackNumber`.

Unequal from/to names describe an interval and are not guessed to be a station
at either endpoint. Missing station columns are optional for older minimal
workbooks. Invalid coordinates on identified stations fail loading.
Configured stations are preserved. Only exact duplicate name/coordinate/track
events are removed; one name at distinct positions remains distinct.

The previously generated synthetic TUB workbook has no named P station rows.
This code cannot reconstruct the missing names or their positions from that
file. A source workbook containing valid station events is still needed to
show its intermediate stations. No synthetic positions are invented here.
