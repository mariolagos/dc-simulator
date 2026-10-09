# TrainsSupply — Functional specification derived from tests

## Status and scope

This document reconstructs functional expectations from the supplied `test(1).zip` archive. It describes behavior asserted in test source, not a complete statement of intended product requirements. The tests were inspected but not executed. No repository commit or build configuration accompanied the archive; test inclusion in the active build and compatibility with current production code have not been established.

The archive contains 42 Java files: 41 test classes with 134 `@Test` methods and one shared fixture class. It also contains `ObserverSnapshotTest.md`, which explains a test-local design experiment. Archive SHA-256: `a27104b0b543183fdab926e03705a8b8d609d4d1a7f40f56ad6934b77c0898f3`.

Requirement IDs below are documentation references, not existing project requirement IDs. “Shall” expresses an expectation reconstructed from the cited tests within their fixture scope. It does not imply that arbitrary inputs or complete studies have been verified. Test references use `ClassName.methodName`; the complete package and method register is appended below.

Evidence is separated into:

- **Production component:** tests call production classes and check their output.
- **Fixture integration:** several production components are exercised together on a constructed example.
- **Standalone component:** production code is tested with synthetic evaluation conditions; integration into `dcStudy` is not established.
- **Test-local prototype:** the model or mechanism being asserted is implemented inside the test itself.
- **Artifact smoke check:** an output exists; its physical or visual correctness is not established.

### Maintenance note — 2026-10-09

The original archive inventory, reconstructed requirements and Appendix A are
retained unchanged. Their statements about unexecuted tests and missing evidence
refer to that archive review. Appendix C adds later implementation, diagnostic
and user-run evidence; it does not retroactively change the archive's contents
or turn every later observation into an automated assertion.

## 1. Configuration and installation input

| ID | Reconstructed functional expectation | Test evidence |
| --- | --- | --- |
| IN-01 | Load a fixed-load installation with its identity, physical position, power and feeding/return connections. The example retains 1 MW at position 500 m and two typed connections. | `GridModelLoaderTest.loadsFixedLoadAndItsElectricalConnections` |
| IN-02 | Load a six-terminal rectifier with six connection entries: four feeding and two return terminals. Preserve the terminal-to-node mapping. | `GridModelLoaderTest.loadsSixTerminalRectifierInstallation` |
| IN-03 | Reject a legacy substation with multiple connections where exactly one feeding and one return connection are required. | `GridModelLoaderTest.rejectsMultipleConnectionsForLegacySubstation` |
| IN-04 | A six-terminal installation shall accept all required terminals and reject a missing terminal with an installation-specific diagnostic. | `SixTerminalRectifierInstallationTest.acceptsAllSixTerminalConnections`, `rejectsMissingTerminalConnection` |
| IN-05 | Copy the supplied terminal map so later caller mutations do not alter the installation. Reject null installation ID, rectifier type or terminal map. | `SixTerminalRectifierInstallationTest.copiesTerminalConnections`, `rejectsNullRequiredValues` |
| IN-06 | Load electrical routes from traffic configuration and retain the configured feeding/return line ordering. | `RouteFactoryTest.loadsRoutesFromTrafficConfig` |

These tests do not provide a comprehensive configuration schema. In particular, this archive has no dedicated `SystemParametersFactoryTest` verifying voltage-threshold ordering.

## 2. Track data and coordinate transformations

| ID | Reconstructed functional expectation | Test evidence |
| --- | --- | --- |
| CO-01 | Read a trackdata route view containing route identity, description, path distances, section IDs, track IDs and railway positions across section transitions. | `RouteViewExcelReaderTest.readsRouteViewFromTrackdataWorkbook` |
| CO-02 | Accept absent or blank optional `trackNumber` and `trackInformation` columns. Missing track identity remains null; explicit track identity is retained. | `RouteViewExcelReaderOptionalColumnsTest.acceptsAbsentOptionalColumns`, `acceptsBlankOptionalColumns`, `preservesExplicitTrackAndRoute` |
| CO-03 | Reject an explicit `trackInformation` value that differs from the requested route ID. | `RouteViewExcelReaderOptionalColumnsTest.rejectsExplicitRouteMismatch` |
| CO-04 | Require intermediate kilometre-board rows within each continuous section visit, for both forward and reverse traversal. Report a missing board. Do not satisfy a visit using a board from a separate visit or require boards outside that visit's interval. | `RouteViewExcelReaderOptionalColumnsTest.reportsMissingBoardForwardAndReverse`, `doesNotBorrowBoardsFromSeparateVisit`, `doesNotRequireBoardsOutsideSectionInterval` |
| CO-05 | Accept the tested reverse route with partial first/last kilometre intervals. Route distance shall be non-decreasing; identical duplicate rows at the same distance are accepted, conflicting rows are rejected. | `RouteViewExcelReaderOptionalColumnsTest.acceptsReverseAndPartialIntervals`, `rejectsDecreasingRouteDistance`, `acceptsIdenticalRowsAtSameRouteDistance`, `rejectsDifferentRowsAtSameRouteDistance` |
| CO-06 | Map route distance to railway section, track and position across boundaries. In the W–E fixture, distance 6349 m maps to section 101, while 6350 m maps to the start of section 200. | `RouteViewMapperTest.mapsWeRouteAcrossTrackSections` |
| CO-07 | Map railway distance proportionally to model length, including shorter/longer model segments and decreasing railway coordinates. Preserve section and track identity. | `TrackSectionMapperTest.scalesRailwayDistanceToShorterModelSegment`, `scalesRailwayDistanceToLongerModelSegment`, `mapsDecreasingRailwayCoordinates` |
| CO-08 | Compose route-to-railway and railway-to-model transformations so model position resets at the tested section transitions. | `DefaultTrackTransformServiceTest.mapsWeRoutePositionAcrossSections` |
| CO-09 | Build ascending infrastructure segments from the tested kilometre-board input, with section-local model distances 0–1000–2000 m. | `TrackLoaderTest.keepsAscendingInfrastructureGeometryIndependentOfRouteDirection` |

CO-09 is supported by an ascending fixture; the test does not supply two oppositely directed route views. Its name expresses a broader architectural intention than its assertions alone demonstrate.

## 3. Stations

| ID | Reconstructed functional expectation | Test evidence |
| --- | --- | --- |
| ST-01 | Read named `P` events whose from/to station abbreviations agree. Deduplicate identical station events while retaining distinct positions for the same name. Missing track numbers are accepted. Other event types, unnamed events and unequal station abbreviations are excluded in the fixture. | `TrackStationExcelReaderTest.readsNamedPEventsWithoutTrackNumberAndKeepsDistinctPositions` |
| ST-02 | Read stations from a workbook sheet; a sheet without station columns yields no stations. Reject the tested invalid coordinate on a named station. | `TrackStationExcelReaderTest.readsWorkbookAndTreatsMissingStationColumnsAsOptional`, `rejectsInvalidCoordinateOnNamedStation` |
| ST-03 | Explicit Excel stations replace configured positions with the same station name. Preserve unrelated configured stations and multiple explicit positions. | `TrackStationExcelReaderTest.explicitExcelStationsReplaceConfiguredPositionsWithSameName` |

## 4. Traffic generation and measured input

| ID | Reconstructed functional expectation | Test evidence |
| --- | --- | --- |
| TR-01 | Expand repeated departures into suffixed train IDs and headway-shifted start times. The fixture yields `Train-001`–`Train-003` at 18000, 18090 and 18180 s. For count one, retain the original train ID. | `RunCsvInputFactoryTest.expandsTrainCountUsingHeadway`, `keepsOriginalTrainIdWhenCountIsOne` |
| TR-02 | Expand every ordered leg for every train. Retain file/sheet selection, common train departure, optional relative departure and inherited/overridden auxiliary settings. | `RunCsvInputFactoryTest.expandsMultipleLegTemplateForEveryTrain` |
| TR-03 | Construct a configured measured-log source with auxiliary settings represented as already included and zero completion power. | `RunCsvInputFactoryTest.buildsConfiguredMeasuredLogSource` |
| TR-04 | Use run-sheet route position rather than BIS position for both tested directions. | `RunCsvFromExcelTest.usesRoutePositionInsteadOfBisPosition` |
| TR-05 | Clip a run to the absolute simulation window and interpolate positions at the clipped boundaries. Omit samples for a run entirely outside the window, leaving the CSV header. | `RunCsvFromExcelTest.clipsRunToAbsoluteSimulationWindow`, `omitsRunOutsideSimulationWindow` |
| TR-06 | When a configured leg departure is missing, use the workbook timetable departure. Join the tested two-leg journey with continuous positions and the expected absolute timestamps. | `RunCsvFromExcelTest.usesRunExcelRelativeDepartureWhenLegDepartureIsMissing` |
| TR-07 | Accept a next leg starting exactly at the preceding endpoint, keep continuous positions, and export one row at the shared timestamp. Reject a next leg starting before the preceding leg ends. | `RunCsvFromExcelTest.acceptsNextLegStartingWhenPreviousLegEnds`, `rejectsNextLegStartingBeforePreviousLegEnds` |
| TR-08 | Add auxiliary demand to source power when it is absent from the leg's model. During the following dwell, use that leg's auxiliary demand. Leave the next leg's source power unchanged when its auxiliaries are already modelled. | `RunCsvFromExcelTest.appliesAuxiliaryPowerToLegAndFollowingDwell` |
| TR-09 | Carry the auxiliary policy from configuration through the complete writer export. The fixture checks 120 kW during the first leg, 100 kW during dwell and 20 kW at the second leg start. | `RunCsvWriterTest.carriesAuxiliaryConfigurationThroughCompleteExport` |
| TR-10 | Derive positions by integrating measured speed when position is absent, and convert supplied kW to W. The fixture yields positions 0, 0.5 and 1.5 m. | `RunCsvFromLogTest.integratesSpeedAndConvertsKilowatts` |
| TR-11 | Derive measured power from voltage × current when no power column is supplied, retaining consumption-positive signs in the tested format: +7000 W and −3550 W. | `RunCsvFromLogTest.derivesPowerFromVoltageAndCurrentAndNormalizesSign` |

TR-11 does not test the alternate regeneration-positive convention despite the method name. The supplied traffic tests do not assert positive-resolution resampling, invalid count/headway handling, missing input files, or a complete ten-train, three-leg study.

## 5. Calculation network and train placement

| ID | Reconstructed functional expectation | Test evidence |
| --- | --- | --- |
| NW-01 | Build calculation nodes retaining configured identity and coordinates. Create a conductor branch with resistance derived from geometry: the fixture's 1000 m at 0.01 ohm/m gives 10 ohm. | `CalculationNetworkBuilderTest.buildBaseCreatesGridNodesAndBranches` |
| NW-02 | Accept a substation connected to existing feeding/return nodes. Reject a missing terminal node and identify it. Preserve the enabled flag in its diode element. | `CalculationNetworkBuilderTest.acceptsSubstationWithExistingFeedingAndReturnNodes`, `rejectsSubstationWithMissingFeedingNode`, `rejectsSubstationWithMissingReturnNode`, `propagatesSubstationEnabledFlagToDiodeElement` |
| NW-03 | Build a six-terminal rectifier network with six nodes, four internal branches and one rectifier element connected to canonical feeding/return terminals. | `CalculationNetworkBuilderTest.connectsSixTerminalRectifierWithInternalBranches` |
| NW-04 | Build a fixed-load element retaining ID, feeding/return nodes and requested power. | `CalculationNetworkBuilderTest.buildsFixedLoadElement` |
| NW-05 | Insert separate feeding/return train nodes at interior positions and split each conductor proportionally. Two trains at 300 and 700 m split each 1-ohm conductor into 0.3, 0.4 and 0.3 ohm. | `TrainNodeInserterTest.insertsTwoTrainNodesAndSplitsBranchDeterministically` |
| NW-06 | Reuse existing nodes for a train exactly at a branch endpoint, without splitting that branch. Reject a train that cannot obtain an electrical connection. | `TrainNodeInserterTest.treatsTrainExactlyAtExistingNodeAsPlacedWithoutChangingTopology`, `rejectsTrainThatCannotBePlacedOnAnyBranch` |
| NW-07 | Produce identical checked node/branch topology for the tested permutations of train input and for repeated identical calls. | `TrainNodeInserterTest.producesIdenticalTopologyForDifferentTrainInputOrder`, `producesIdenticalTopologyAcrossRepeatedRuns` |
| NW-08 | In the constructed C1 example, inserting one train produces six nodes, four branches and one train load connected to its generated feeding/return nodes. | `C1SingleTimestepTopologyTest.buildsC1SingleTimestepTopologyWithOneTrainInsertedIntoLineSegment` |

NW-07's comparison checks node IDs/positions, branch IDs/endpoints/resistances and train-load count; it does not compare every train-load field. Train route filtering, coincident interior trains and resistance preservation over arbitrary layouts need separate evidence.

## 6. Input checking and electrical route validation

| ID | Reconstructed functional expectation | Test evidence |
| --- | --- | --- |
| CK-01 | For invalid study input, produce a failure report under `checks/<study>_input_check.txt` without overwriting existing export/result files. | `DcCheckTest.invalidInputProducesFailureReportWithoutOverwritingResultsOrExports` |
| CK-02 | Accept adjacent route lines connected through separate terminals of the same internal bus, and include that internal connection in the diagnostic topology. | `ElectricalRouteCheckTest.acceptsRouteAcrossSeparateTerminalsOfSameInternalBus` |
| CK-03 | Report disconnected consecutive lines, unknown line IDs and an unknown selected route. | `ElectricalRouteCheckTest.reportsDisconnectedConsecutiveLines`, `reportsUnknownLineAndSelectedRoute` |
| CK-04 | Report the tested feeding-node prefix violation and feeding/return electrical short circuit. | `ElectricalRouteCheckTest.reportsFeedingReturnShortCircuitAndWrongPlacementPrefix` |
| CK-05 | Selecting one route limits its printed topology but does not suppress validation errors in other configured routes. | `ElectricalRouteCheckTest.routeSelectionDoesNotSkipValidationOfOtherRoutes` |

CK-01 checks one invalid-input path. It does not establish every successful check stage or the entire command-line orchestration.

## 7. Matrix assembly and electrical models

| ID | Reconstructed functional expectation | Test evidence |
| --- | --- | --- |
| EL-01 | Expose deterministic active-node indices in network order and omit inactive nodes. Repeated builds retain indices for unchanged input. Unknown lookup/reference/injection nodes are rejected. | `AdmittanceSystemTest` and `AdmittanceSystemBuilderTest` (all registered methods) |
| EL-02 | Stamp a resistor as diagonal +1/R and off-diagonal −1/R. Stamp the tested substation Norton equivalent as conductance 1/R and equal/opposite terminal injections E/R. | `AdmittanceSystemBuilderTest.stampsPassiveBranchConductanceIntoMatrix`, `stampsSubstationNortonEquivalentIntoMatrixAndCurrentVector` |
| EL-03 | Add explicit current injections at their node indices and sum repeated injections on the same node. Expose the reference node ID and index. | `AdmittanceSystemBuilderTest.stampsCurrentInjectionsIntoCurrentVector`, `sumsMultipleCurrentInjectionsOnSameNode`, `exposesReferenceNodeIndex` |
| EL-04 | Stamp the enabled diode-substation Norton equivalent. Omit all contributions for the disabled substation in the tested builder path. | `DiodeSubstationElementTest.enabledSubstationStampsNortonEquivalent`, `disabledSubstationDoesNotStampElectricalContribution` |
| EL-05 | For the fixed 1 MW load and preceding terminal voltage of 750 V, stamp equal/opposite consuming currents of 1,000,000/750 A. | `FixedLoadElementTest.stampsConstantPowerLoadUsingPreviousVoltage` |
| EL-06 | For Q = 1.2 MW, Us = 850 V and Um = 900 V, generate current Q/Us at Us, half that current at 875 V, and zero at Um. | `RegenerativeTrainElementTest.deliversFullRegenerationAtStartVoltage`, `deliversHalfCurrentHalfwayThroughVoltageRamp`, `deliversNoRegenerationAtMaximumVoltage` |
| EL-07 | Stamp the regenerative ramp region using r = (Um − Us)/(Q/Us), conductance 1/r and Norton injection Um/r. Below Us, use conductance Q/U² and injection 2Q/U with the tested terminal signs. | `RegenerativeTrainElementTest.stampsTheveninEquivalentInRegenerativeVoltageControlRegion`, `stampsRequestedRegenerativeCurrentBelowStartVoltage` |

EL-06 specifies a current ramp, not a power ramp. Direct diode blocking is not asserted in `DiodeSubstationElementTest`; a broader fixture exercises the blocked state below.

## 8. Numerical solution and regenerative fallback

| ID | Reconstructed functional expectation | Test evidence |
| --- | --- | --- |
| SO-01 | For the one-substation fixture, solve 750 V at open circuit, 600 V under a 500 A consuming injection and 500 A through both 0.1-ohm conductor branches. A one-shot train element produces the asserted 600 V. | `ElectricalSolverValidationTest.openCircuitProducesNominalTrainVoltage`, `loadedCurrentInjectionProducesVoltageDrop`, `loadedCurrentInjectionProducesExpectedBranchCurrents`, `trainLoadElementProducesExpectedVoltageDrop`; fixture `ElectricalTestCases` |
| SO-02 | For a negative train power in the one-shot stamping fixture, return the asserted 900 V terminal voltage. | `ElectricalSolverValidationTest.regenerativeBrakingInjectsCurrentIntoNetwork` |
| SO-03 | Converge the tested 150 kW constant-power consuming load in more than one iteration to the higher root of U² − E·U + Rtotal·P = 0. | `ElectricalSolverValidationTest.constantPowerIterationConvergesForTractionLoad` |
| SO-04 | Converge the tested simultaneous two-train load and return both feeding/return voltages with positive terminal voltages. | `SingleTimestepSolverTest.solvesTwoTrainLoadsInSameTimestep` |
| SO-05 | Return regenerative terminal voltages above 850 V and no more than 900 V in the tested diode-network examples. In the explicit blocked-state matrix fixture, the substation voltage exceeds 780 V. | `SingleTimestepSolverTest.solvesRegeneratingTrainWithDiodeSubstation`, `regenerativeTrainEstablishesVoltageWhenDiodeSubstationIsBlocked` |
| SO-06 | Converge the fixture with an explicit regenerative element and a 200 kW motoring train; retain regenerative voltage in (850, 900] V, motoring/substation voltages above 780 V and calculated regenerative delivery greater than 200 kW. | `SingleTimestepSolverTest.regenerativeTrainSuppliesMotoringTrainWhileSubstationIsBlocked` |
| SO-07 | Converge the tested non-receptive regeneration case using voltage control, with train voltage in (850, 900] V and substation voltage above 780 V. | `SingleTimestepSolverTest.solvesNonReceptiveRegenerationUsingVoltageControl` |
| SO-08 | Preserve both requested allocations in the directly convergent −800/+600 kW and −700/+700 kW fixtures, within 1 kW. | `DcSolverFallbackTest.excessRegenerationDoesNotReduceMotoringTrain`, `balancedRegenerationAndMotoringAreFullyAccepted` |
| SO-09 | In the −600/+800 kW fallback fixture, retain the +800 kW motoring allocation and reduce the magnitude of the negative allocation while leaving it negative. | `DcSolverFallbackTest.regenerationCurtailmentDoesNotReduceMotoringPower` |
| SO-10 | Report regenerative current from its voltage-dependent characteristic: −900 kW allocated at 872.738 V yields approximately −577.3 A and −503.8 kW. | `DcSolverFallbackTest.reportsRegenerativeCurrentFromVoltageDependentCharacteristic` |

SO-02 is a linear assembly solve without the nonlinear diode-state updates. It is not evidence that a diode substation absorbs braking energy. SO-06 constructs an explicit regenerative element with a fixed stamping voltage; it is not equivalent to importing a two-train journey through `dcStudy`.

The manually implemented bisection in `SingleTimestepSolverTest.findsFeasibleRegenerationLevelForNonReceptiveNetwork` verifies convergence at zero regeneration and a final multiplier in [0, 1). It does not invoke `DcSolver.solveWithFallback` or independently assert final physical acceptance. SO-08 tests allocations, not full delivered regeneration. SO-10 tests a reporting helper, not fallback-to-longtable energy propagation.

## 9. Power, losses and reporting

| ID | Reconstructed functional expectation | Test evidence |
| --- | --- | --- |
| RP-01 | Aggregate losses from split branches by physical source line ID and exclude internal installation branches from that line-loss output. The fixture sums to 150 W. | `DcSolverLineLossTest.aggregatesSplitBranchesByPhysicalLineAndExcludesInternalBranches` |
| RP-02 | Accumulate line-loss energy over result intervals; the checked sequence totals 350 J. | `DcSolverLineLossTest.accumulatesLineLossEnergyOverResultIntervals` |
| RP-03 | Calculate train power deviation as requested minus delivered power, including a negative deviation for curtailed negative demand. | `DcSolverLineLossTest.trainPowerDeltaIsRequestedMinusDeliveredPower` |
| RP-04 | Calculate the system balance by subtracting loads and the supplied loss terms from substation power. Aggregate positive consumption and the magnitude of negative regeneration separately. | `DcSolverLineLossTest.systemBalanceSubtractsLoadsAndLossesFromSubstationPower`, `consumptionAndRegenerationAreAggregatedSeparately` |
| RP-05 | Export supplied longtable values into study-prefixed train, installation, line and system workbooks. Preserve train speed in m/s and convert 12.5 m/s to 45 km/h; preserve checked power-deviation and energy values. | `DcReporterTest.exportsTrainPowerDeltaAndLineLosses` |
| RP-06 | Export graphical-timetable CSV with `time_s,train_id,base_route_position_m,p_delta_W`. Map the tested train positions to the configured base route; retain a blank position when it cannot be mapped. Planned output uses zero power deviations. | `DcReporterTest.exportsGraphicalTimetableInputOnConfiguredBaseRoute` |
| RP-07 | Export station, supply-point and section-boundary markers on the base route, including CSV quoting for a station name containing a comma. Planned and result marker output shall agree in the fixture. | `DcReporterTest.exportsGraphicalTimetableInputOnConfiguredBaseRoute` |
| RP-08 | Orient graphical-timetable sections using trackdata. For the two-section fixture, export distances 0, 500, 2000, 2000, 2500 and 4000 m, stations at 500/1500/2500/3500 m and a boundary at 2000 m. | `DcReporterTest.orientsGraphicalTimetableSectionsFromTrackdata` |

RP-05 supplies already computed longtable values. It verifies report transport and selected columns, not the physical computation of those energies. RP-08's fixture includes route endpoints at 0 and 4000 m; it does not prove trains stop at A and D. No SPlot SVG timetable rendering test is present in this archive.

## 10. Diagnostic visualization

| ID | Reconstructed functional expectation | Test evidence |
| --- | --- | --- |
| VI-01 | Export selected electrical lines and internal closure to DOT, omit unrelated nodes, escape the tested line label, and draw undirected connections. | `ElectricalTopologyGraphTest.exportsSelectedLinesAndInternalClosureWithoutCurrentArrows` |
| VI-02 | Group a station's six terminals in a DOT symbol while retaining line labels, terminal ports and terminal identity. | `ElectricalStationSymbolTest.groupsSixTerminalsAndKeepsLineNamesAndPorts` |
| VI-03 | Draw straight feeding/return schematic lanes with configured resistance-per-metre labels. Reversing the route retains terminal identities and all return lines. | `RouteSchematicTest.drawsBothStraightRailsWithConfiguredOhmPerMeter`, `reverseRouteKeepsTerminalIdentityAndAllReturns` |
| VI-04 | Draw a combined two-route schematic with four straight lanes, feeding/return distinction and installation EMF/internal resistance. Accept missing track numbers and preserve output when route input order is reversed in the tested case. | `CombinedRouteSchematicTest.rendersFourStraightLanesAndStationElectricalValues`, `CombinedRouteWithoutTrackNumberTest.rendersTwoRoutesWithMissingTrackNumbers` |
| VI-05 | Visually group only explicitly configured near-ideal junction connectors, retain source railway labels, and leave calculation-network nodes/branches intact. Do not collapse unconfigured or non-ideal connectors. | `RouteSchematicJunctionTest.groupsOnlyExplicitNearIdealConnectorsAndRetainsSourceLabels` |
| VI-06 | Locate physical objects on reverse routes without mixing track identities. Do not interpolate across a section/track transition. | `TrackRouteDiagramTest.mapsReverseRouteAndDoesNotMixTracks`, `doesNotInterpolateAcrossSectionOrTrackTransitions` |
| VI-07 | Preserve the checked relative station spacing and kilometre-board labels. Draw a vertical route band containing distinct station, supply and connection objects and route endpoint labels. | `TrackRouteDiagramTest.preservesDistancesAndDrawsConfiguredBoards`, `drawsVerticalBandWithDistinctObjects` |
| VI-08 | Consolidate coincident junction references without duplicate entry/exit/connection labels, but retain transitions separated by real distance. | `TrackRouteDiagramCompactTest.groupsCoincidentReferencesWithoutDuplicateConnections`, `retainsTransitionsSeparatedByRealDistance` |

These checks inspect SVG/XML structure, labels, counts or exact layout attributes. They are not visual approval of arbitrary diagrams. `TrainPowerCurveImageTest.writesTrainPowerCurrentCurveImage` creates a PNG using production current evaluations, but its sole assertion is file existence; it is an artifact smoke check rather than verification of the plotted characteristic.

## 11. Standalone optimization component

`PowerAllocationOptimizerTest` calls the production optimizer with synthetic callbacks. The following expectations apply to that component; the archive does not establish its use in the study execution path.

| ID | Component expectation | Test evidence |
| --- | --- | --- |
| OP-01 | Retain feasible requested powers within 1 kW when the callback imposes no penalty. | `findsRequestedPowersWhenAllRequestsAreFeasible` |
| OP-02 | Under the supplied quadratic capacity penalty, return net allocation near 1.5 MW within 5 kW; positive allocations do not exceed requests and the negative allocation does not become more negative than requested. | `reducesRequestedPowersWhenNetworkCapacityIsLimited` |
| OP-03 | Under an 800 kW regenerative limit penalty, retain the two motoring allocations and return approximately −800 kW regeneration. Under a zero-receptivity penalty, return approximately zero regeneration. | `limitsRegenerationWhenNetworkIsPartiallyReceptive`, `blocksRegenerationWhenNetworkIsNotReceptive` |
| OP-04 | Under the tested 1 MW total regenerative constraint, retain 500 kW motoring and return approximately −700/−300 kW allocations for −1.2/−0.8 MW requests, within 5 kW. | `allocatesLimitedReceptivityBetweenMultipleRegeneratingTrains` |

The synthetic penalties do not solve a railway electrical network. OP-04 does not establish proportional fairness, a general optimum or the policy of `DcSolver`'s common-multiplier fallback.

## 12. Test-local design experiments

These are useful design references but must not be promoted to verified product behavior.

| ID | Prototype expectation | Evidence and limit |
| --- | --- | --- |
| PT-01 | A consuming-current formula returns zero at/below 600 V, ramps its current limit to 7000 A at 700 V, and otherwise takes the lesser of P/U and that limit. | All six methods of `TrainPowerModelTest` call private helpers in the same test class, not `TrainLoadElement`. The threshold names/order must not be copied into a production specification without reconciliation. |
| PT-02 | Capturing observer snapshots retains accepted values while live observers continue changing; a registry publishes heterogeneous snapshot types. | Both methods of `ObserverSnapshotTest` exercise locally defined observers, registry, caller and collector. The companion Markdown illustrates this local experiment. Production adoption is not shown. |

## 13. Evidence limits and handover

The following remain unestablished by this archive, even where related behavior is described in implementation stories:

- Complete `dcStudy --check` / export / solve / report command execution and Gradle task selection.
- General input validation, including the current voltage-ordering rule and invalid traffic expansion settings.
- Production traction-current boundary behavior: the dedicated formula test duplicates its own model.
- Warm-start retry, singular-matrix handling, iteration limits and complete zero-regeneration failure behavior.
- Fallback allocation propagation through longtable output and cumulative train/substation energy.
- Independent KCL residual and whole-system physical power/energy balance across solved timesteps. Helper arithmetic and a small analytic circuit are covered; comprehensive residual checks are not.
- Positive-resolution traffic resampling, final off-grid endpoints and multi-leg joins across a clipped-out leg.
- Full bidirectional ten-train traffic, branching-route placement and coincident interior train positions.
- SPlot timetable rendering, interactive operation, electrical section switching, batteries or AC networks.

These are coverage observations, not a claim that the implementation is defective or that new work is required immediately. To adopt this document as a maintained specification, associate it with a code revision and test run, confirm the active test source set, and review the reconstructed expectations with the project owner. Keep prototype and standalone-component evidence visibly separate.

## Appendix A — Complete test register

The register below lists every annotated test method found in the archive. Paths are relative to the repository root assuming the archive's `test` directory corresponds to `src/test`. This assumption must be checked against the build configuration.

### org.supply.app.DcCheckTest

Source: `src/test/java/org/supply/app/DcCheckTest.java`  
Evidence scope: Production component or fixture integration.

- `invalidInputProducesFailureReportWithoutOverwritingResultsOrExports`

### org.supply.app.DcReporterTest

Source: `src/test/java/org/supply/app/DcReporterTest.java`  
Evidence scope: Production component or fixture integration.

- `exportsTrainPowerDeltaAndLineLosses`
- `exportsGraphicalTimetableInputOnConfiguredBaseRoute`
- `orientsGraphicalTimetableSectionsFromTrackdata`

### org.supply.app.DcSolverFallbackTest

Source: `src/test/java/org/supply/app/DcSolverFallbackTest.java`  
Evidence scope: Production component or fixture integration.

- `excessRegenerationDoesNotReduceMotoringTrain`
- `balancedRegenerationAndMotoringAreFullyAccepted`
- `regenerationCurtailmentDoesNotReduceMotoringPower`
- `reportsRegenerativeCurrentFromVoltageDependentCharacteristic`

### org.supply.app.DcSolverLineLossTest

Source: `src/test/java/org/supply/app/DcSolverLineLossTest.java`  
Evidence scope: Production component or fixture integration.

- `aggregatesSplitBranchesByPhysicalLineAndExcludesInternalBranches`
- `accumulatesLineLossEnergyOverResultIntervals`
- `trainPowerDeltaIsRequestedMinusDeliveredPower`
- `systemBalanceSubtractsLoadsAndLossesFromSubstationPower`
- `consumptionAndRegenerationAreAggregatedSeparately`

### org.supply.domain.SixTerminalRectifierInstallationTest

Source: `src/test/java/org/supply/domain/SixTerminalRectifierInstallationTest.java`  
Evidence scope: Production component or fixture integration.

- `acceptsAllSixTerminalConnections`
- `rejectsMissingTerminalConnection`
- `copiesTerminalConnections`
- `rejectsNullRequiredValues`

### org.supply.io.export.RunCsvFromExcelTest

Source: `src/test/java/org/supply/io/export/RunCsvFromExcelTest.java`  
Evidence scope: Production component or fixture integration.

- `usesRoutePositionInsteadOfBisPosition`
- `clipsRunToAbsoluteSimulationWindow`
- `omitsRunOutsideSimulationWindow`
- `usesRunExcelRelativeDepartureWhenLegDepartureIsMissing`
- `acceptsNextLegStartingWhenPreviousLegEnds`
- `appliesAuxiliaryPowerToLegAndFollowingDwell`
- `rejectsNextLegStartingBeforePreviousLegEnds`

### org.supply.io.export.RunCsvFromLogTest

Source: `src/test/java/org/supply/io/export/RunCsvFromLogTest.java`  
Evidence scope: Production component or fixture integration.

- `integratesSpeedAndConvertsKilowatts`
- `derivesPowerFromVoltageAndCurrentAndNormalizesSign`

### org.supply.io.export.RunCsvWriterTest

Source: `src/test/java/org/supply/io/export/RunCsvWriterTest.java`  
Evidence scope: Production component or fixture integration.

- `carriesAuxiliaryConfigurationThroughCompleteExport`

### org.supply.loader.GridModelLoaderTest

Source: `src/test/java/org/supply/loader/GridModelLoaderTest.java`  
Evidence scope: Production component or fixture integration.

- `loadsFixedLoadAndItsElectricalConnections`
- `loadsSixTerminalRectifierInstallation`
- `rejectsMultipleConnectionsForLegacySubstation`

### org.supply.loader.RouteFactoryTest

Source: `src/test/java/org/supply/loader/RouteFactoryTest.java`  
Evidence scope: Production component or fixture integration.

- `loadsRoutesFromTrafficConfig`

### org.supply.loader.RunCsvInputFactoryTest

Source: `src/test/java/org/supply/loader/RunCsvInputFactoryTest.java`  
Evidence scope: Production component or fixture integration.

- `buildsConfiguredMeasuredLogSource`
- `expandsTrainCountUsingHeadway`
- `keepsOriginalTrainIdWhenCountIsOne`
- `expandsMultipleLegTemplateForEveryTrain`

### org.supply.solver.build.C1SingleTimestepTopologyTest

Source: `src/test/java/org/supply/solver/build/C1SingleTimestepTopologyTest.java`  
Evidence scope: Production component or fixture integration.

- `buildsC1SingleTimestepTopologyWithOneTrainInsertedIntoLineSegment`

### org.supply.solver.build.CalculationNetworkBuilderTest

Source: `src/test/java/org/supply/solver/build/CalculationNetworkBuilderTest.java`  
Evidence scope: Production component or fixture integration.

- `buildBaseCreatesGridNodesAndBranches`
- `acceptsSubstationWithExistingFeedingAndReturnNodes`
- `rejectsSubstationWithMissingFeedingNode`
- `rejectsSubstationWithMissingReturnNode`
- `propagatesSubstationEnabledFlagToDiodeElement`
- `connectsSixTerminalRectifierWithInternalBranches`
- `buildsFixedLoadElement`

### org.supply.solver.build.CombinedRouteSchematicTest

Source: `src/test/java/org/supply/solver/build/CombinedRouteSchematicTest.java`  
Evidence scope: Production component or fixture integration.

- `rendersFourStraightLanesAndStationElectricalValues`

### org.supply.solver.build.CombinedRouteWithoutTrackNumberTest

Source: `src/test/java/org/supply/solver/build/CombinedRouteWithoutTrackNumberTest.java`  
Evidence scope: Production component or fixture integration.

- `rendersTwoRoutesWithMissingTrackNumbers`

### org.supply.solver.build.ElectricalRouteCheckTest

Source: `src/test/java/org/supply/solver/build/ElectricalRouteCheckTest.java`  
Evidence scope: Production component or fixture integration.

- `acceptsRouteAcrossSeparateTerminalsOfSameInternalBus`
- `reportsDisconnectedConsecutiveLines`
- `reportsUnknownLineAndSelectedRoute`
- `reportsFeedingReturnShortCircuitAndWrongPlacementPrefix`
- `routeSelectionDoesNotSkipValidationOfOtherRoutes`

### org.supply.solver.build.ElectricalStationSymbolTest

Source: `src/test/java/org/supply/solver/build/ElectricalStationSymbolTest.java`  
Evidence scope: Production component or fixture integration.

- `groupsSixTerminalsAndKeepsLineNamesAndPorts`

### org.supply.solver.build.ElectricalTopologyGraphTest

Source: `src/test/java/org/supply/solver/build/ElectricalTopologyGraphTest.java`  
Evidence scope: Production component or fixture integration.

- `exportsSelectedLinesAndInternalClosureWithoutCurrentArrows`

### org.supply.solver.build.RouteSchematicJunctionTest

Source: `src/test/java/org/supply/solver/build/RouteSchematicJunctionTest.java`  
Evidence scope: Production component or fixture integration.

- `groupsOnlyExplicitNearIdealConnectorsAndRetainsSourceLabels`

### org.supply.solver.build.RouteSchematicTest

Source: `src/test/java/org/supply/solver/build/RouteSchematicTest.java`  
Evidence scope: Production component or fixture integration.

- `drawsBothStraightRailsWithConfiguredOhmPerMeter`
- `reverseRouteKeepsTerminalIdentityAndAllReturns`

### org.supply.solver.build.TrainNodeInserterTest

Source: `src/test/java/org/supply/solver/build/TrainNodeInserterTest.java`  
Evidence scope: Production component or fixture integration.

- `insertsTwoTrainNodesAndSplitsBranchDeterministically`
- `rejectsTrainThatCannotBePlacedOnAnyBranch`
- `treatsTrainExactlyAtExistingNodeAsPlacedWithoutChangingTopology`
- `producesIdenticalTopologyForDifferentTrainInputOrder`
- `producesIdenticalTopologyAcrossRepeatedRuns`

### org.supply.solver.electrical.AdmittanceSystemBuilderTest

Source: `src/test/java/org/supply/solver/electrical/AdmittanceSystemBuilderTest.java`  
Evidence scope: Production component or fixture integration.

- `buildsNodeIndexOrderingFromActiveNodesInNetworkOrder`
- `excludesInactiveNodesFromAdmittanceSystem`
- `stampsPassiveBranchConductanceIntoMatrix`
- `stampsSubstationNortonEquivalentIntoMatrixAndCurrentVector`
- `exposesReferenceNodeIndex`
- `rejectsUnknownReferenceNode`
- `stampsCurrentInjectionsIntoCurrentVector`
- `sumsMultipleCurrentInjectionsOnSameNode`
- `rejectsCurrentInjectionForUnknownNode`
- `preservesMatrixIndicesAcrossRepeatedBuilds`

### org.supply.solver.electrical.AdmittanceSystemTest

Source: `src/test/java/org/supply/solver/electrical/AdmittanceSystemTest.java`  
Evidence scope: Production component or fixture integration.

- `exposesDeterministicNodeIndexMapping`
- `rejectsUnknownNodeIdLookup`

### org.supply.solver.electrical.ElectricalSolverValidationTest

Source: `src/test/java/org/supply/solver/electrical/ElectricalSolverValidationTest.java`  
Evidence scope: Production component or fixture integration.

- `openCircuitProducesNominalTrainVoltage`
- `loadedCurrentInjectionProducesVoltageDrop`
- `loadedCurrentInjectionProducesExpectedBranchCurrents`
- `trainLoadElementProducesExpectedVoltageDrop`
- `regenerativeBrakingInjectsCurrentIntoNetwork`
- `constantPowerIterationConvergesForTractionLoad`

### org.supply.solver.electrical.SingleTimestepSolverTest

Source: `src/test/java/org/supply/solver/electrical/SingleTimestepSolverTest.java`  
Evidence scope: Production component or fixture integration.

- `findsFeasibleRegenerationLevelForNonReceptiveNetwork`
- `solvesTwoTrainLoadsInSameTimestep`
- `solvesRegeneratingTrainWithDiodeSubstation`
- `regenerativeTrainEstablishesVoltageWhenDiodeSubstationIsBlocked`
- `regenerativeTrainSuppliesMotoringTrainWhileSubstationIsBlocked`
- `solvesNonReceptiveRegenerationUsingVoltageControl`

### org.supply.solver.electrical.TrainPowerCurveImageTest

Source: `src/test/java/org/supply/solver/electrical/TrainPowerCurveImageTest.java`  
Evidence scope: Artifact smoke check.

- `writesTrainPowerCurrentCurveImage`

### org.supply.solver.model.DiodeSubstationElementTest

Source: `src/test/java/org/supply/solver/model/DiodeSubstationElementTest.java`  
Evidence scope: Production component or fixture integration.

- `enabledSubstationStampsNortonEquivalent`
- `disabledSubstationDoesNotStampElectricalContribution`

### org.supply.solver.model.FixedLoadElementTest

Source: `src/test/java/org/supply/solver/model/FixedLoadElementTest.java`  
Evidence scope: Production component or fixture integration.

- `stampsConstantPowerLoadUsingPreviousVoltage`

### org.supply.solver.model.RegenerativeTrainElementTest

Source: `src/test/java/org/supply/solver/model/RegenerativeTrainElementTest.java`  
Evidence scope: Production component or fixture integration.

- `deliversFullRegenerationAtStartVoltage`
- `deliversHalfCurrentHalfwayThroughVoltageRamp`
- `deliversNoRegenerationAtMaximumVoltage`
- `stampsTheveninEquivalentInRegenerativeVoltageControlRegion`
- `stampsRequestedRegenerativeCurrentBelowStartVoltage`

### org.supply.solver.model.TrainPowerModelTest

Source: `src/test/java/org/supply/solver/model/TrainPowerModelTest.java`  
Evidence scope: Test-local prototype.

- `tractionCurrentIsZeroBelowLowerVoltageLimit`
- `tractionCurrentIsZeroAtLowerVoltageLimit`
- `tractionCurrentDeratesLinearlyBetweenLowerVoltageLimits`
- `tractionAllowsFullCurrentAtUpperLowVoltageLimit`
- `tractionCurrentIsBasedOnRequestedPowerAboveDeratingRegion`
- `tractionCurrentIsLimitedByTrainMaximumCurrent`

### org.supply.solver.optimization.PowerAllocationOptimizerTest

Source: `src/test/java/org/supply/solver/optimization/PowerAllocationOptimizerTest.java`  
Evidence scope: Standalone component with synthetic evaluator.

- `findsRequestedPowersWhenAllRequestsAreFeasible`
- `reducesRequestedPowersWhenNetworkCapacityIsLimited`
- `limitsRegenerationWhenNetworkIsPartiallyReceptive`
- `blocksRegenerationWhenNetworkIsNotReceptive`
- `allocatesLimitedReceptivityBetweenMultipleRegeneratingTrains`

### org.supply.solver.testsupport.ObserverSnapshotTest

Source: `src/test/java/org/supply/solver/testsupport/ObserverSnapshotTest.java`  
Evidence scope: Test-local prototype.

- `snapshotPreservesAcceptedStateWhileObserversContinueIterating`
- `registryHandlesDifferentObserverAndSnapshotTypes`

### org.supply.track.DefaultTrackTransformServiceTest

Source: `src/test/java/org/supply/track/DefaultTrackTransformServiceTest.java`  
Evidence scope: Production component or fixture integration.

- `mapsWeRoutePositionAcrossSections`

### org.supply.track.RouteViewExcelReaderOptionalColumnsTest

Source: `src/test/java/org/supply/track/RouteViewExcelReaderOptionalColumnsTest.java`  
Evidence scope: Production component or fixture integration.

- `acceptsAbsentOptionalColumns`
- `acceptsBlankOptionalColumns`
- `preservesExplicitTrackAndRoute`
- `rejectsExplicitRouteMismatch`
- `reportsMissingBoardForwardAndReverse`
- `acceptsReverseAndPartialIntervals`
- `doesNotRequireBoardsOutsideSectionInterval`
- `doesNotBorrowBoardsFromSeparateVisit`
- `rejectsDifferentRowsAtSameRouteDistance`
- `acceptsIdenticalRowsAtSameRouteDistance`
- `rejectsDecreasingRouteDistance`

### org.supply.track.RouteViewExcelReaderTest

Source: `src/test/java/org/supply/track/RouteViewExcelReaderTest.java`  
Evidence scope: Production component or fixture integration.

- `readsRouteViewFromTrackdataWorkbook`

### org.supply.track.RouteViewMapperTest

Source: `src/test/java/org/supply/track/RouteViewMapperTest.java`  
Evidence scope: Production component or fixture integration.

- `mapsWeRouteAcrossTrackSections`

### org.supply.track.TrackLoaderTest

Source: `src/test/java/org/supply/track/TrackLoaderTest.java`  
Evidence scope: Production component or fixture integration.

- `keepsAscendingInfrastructureGeometryIndependentOfRouteDirection`

### org.supply.track.TrackRouteDiagramCompactTest

Source: `src/test/java/org/supply/track/TrackRouteDiagramCompactTest.java`  
Evidence scope: Production component or fixture integration.

- `groupsCoincidentReferencesWithoutDuplicateConnections`
- `retainsTransitionsSeparatedByRealDistance`

### org.supply.track.TrackRouteDiagramTest

Source: `src/test/java/org/supply/track/TrackRouteDiagramTest.java`  
Evidence scope: Production component or fixture integration.

- `mapsReverseRouteAndDoesNotMixTracks`
- `doesNotInterpolateAcrossSectionOrTrackTransitions`
- `preservesDistancesAndDrawsConfiguredBoards`
- `drawsVerticalBandWithDistinctObjects`

### org.supply.track.TrackSectionMapperTest

Source: `src/test/java/org/supply/track/TrackSectionMapperTest.java`  
Evidence scope: Production component or fixture integration.

- `scalesRailwayDistanceToShorterModelSegment`
- `scalesRailwayDistanceToLongerModelSegment`
- `mapsDecreasingRailwayCoordinates`

### org.supply.track.TrackStationExcelReaderTest

Source: `src/test/java/org/supply/track/TrackStationExcelReaderTest.java`  
Evidence scope: Production component or fixture integration.

- `readsNamedPEventsWithoutTrackNumberAndKeepsDistinctPositions`
- `readsWorkbookAndTreatsMissingStationColumnsAsOptional`
- `rejectsInvalidCoordinateOnNamedStation`
- `explicitExcelStationsReplaceConfiguredPositionsWithSameName`

## Appendix B — Supporting material

- `src/test/java/org/supply/solver/testsupport/ElectricalTestCases.java`: shared circuit fixtures and analytic expected values; no annotated tests.
- `src/test/java/org/supply/solver/testsupport/ObserverSnapshotTest.md`: diagrams and explanation of the test-local snapshot experiment; no additional executable evidence.

## Appendix C — Electrical object input and verification update, 2026-10-09

### Evidence and scope

The user reported a successful full `gradlew test` run after the graphical
reporting fixture was corrected. The preceding full run counted 169 tests and
had one failure in `DcReporterTest`; that class and then the full suite passed
after correction. No commit ID for the final source state was supplied with this
update. The original 134-method archive register remains a historical inventory,
not the register of the later active suite.

Additional evidence comprises supplied production source, the supplied
`TwoSectionsOrderDiagnosticTest` wrapper and diagnostic reports, study
configurations, and exported results from user-run two_sections and WE object
studies. The latter are scenario observations, not JUnit assertions or a proof
for arbitrary networks.

### Supplementary behavior and evidence

These IDs extend the document without renumbering the original requirements.
Unlike the original test-derived tables, the evidence type is explicit here.

| ID | Supplementary expectation or observed behavior | Evidence and limit |
| --- | --- | --- |
| UP-01 | Accept an object grid using reusable `substationTypes` and `lineTypes`, named `electricalObjects` and `electricalAssociations`, expanding it to nodes, conductor lines and installation connections. | two_sections and WE object configurations completed user-run calculations. `ElectricalObjectExpanderTest` is present in the reported working tree; its latest assertions were not supplied for this update. |
| UP-02 | Electrical routes and the reference node shall use IDs from the expanded grid, for example `L01.N.F`, `L01.N.R` and `START.N.R`. Reports and loaders shall see the same supplied expanded `dcsim` block. | Supplied configurations and `DcStudyContext` source. The constructor replaces `scenario.dcsim` with the supplied block; this is implementation evidence. |
| UP-03 | Retain station, supply-point and section-boundary markers on the configured base route in the reporting fixture. | `DcReporterTest.exportsGraphicalTimetableInputOnConfiguredBaseRoute`: the supplied expected rows include SS0 at 300 m, SS1 at 1000 m and SS2 at 1300 m. Passing an empty `dcsim` block removed the grid; supplying `scenario.getConfig("dcsim")` restored the fixture and the user reported the class passing. |
| UP-04 | Compare explicit and object networks, train attachments and replayed solutions across the two_sections diagnostic. | `TwoSectionsOrderDiagnosticTest.replayOriginalAndObjectGridWithOriginalOrdering` calls the diagnostic main method. Its report establishes structural equivalence over 343 timesteps and no replayed voltage difference above 1 V after the numerical change. The report is the evidence for these quantities; a passing wrapper alone is not a general numerical assertion. |
| UP-05 | Reject a floating matrix component rather than assigning it an apparently valid absolute voltage. | Supplied `LinearSystemSolver` change checks connectivity to the reference through nonzero off-diagonal entries. This is implementation evidence for the current branch/two-terminal model; no dedicated new connectivity test source was supplied. |
| UP-06 | Handle a small regenerative upper-boundary overshoot consistently in iteration and result acceptance. | Supplied `SingleTimestepSolver` and `DcSolver` changes: when the negative-power train guess is below `uMaxV`, an overshoot of at most twice the convergence tolerance uses `uMaxV` as the convergence/relaxation target. Raw node voltages remain unchanged; production acceptance allows `uMaxV + 0.002 V`. The physical current cutoff remains at `uMaxV`. The cold diagnostic orderings both converged in 142 iterations, at about 3600.001283 V and 3600.000634 V. |
| UP-07 | Export calculation results separately from graphical timetable rendering. | Supplied Gradle configuration: `dcStudy` runs exporter, solver and reporter; `dcStudyGt` then invokes SPlot with its own wrapper. The rendering `-PstudyId` must match configured `study.id`. Multiple base routes are selected from the generated timetable manifest. User-run WE_objects outputs include WE and NwSe CSVs and SVGs. |

### Scenario observations and result interpretation

The two_sections object result archive contains five trains in each direction.
Positive requested powers were delivered without a material deficit; minimum
train voltage was about 2948.8 V. Regeneration was curtailed near the 3600 V
upper limit. These observations apply to that loading and traffic pattern.

The WE_objects result archive covers 10:00:00–10:59:59. Reported train voltage
ranges from about 2705 to 3251 V. At 10:01:29, `we-047` delivers about 3.85 MW
of its 5.81 MW requested regenerative power while other trains consume power.
The system workbook records about 1467 kWh of train regeneration, 10960 kWh
supplied by stations and 208 kWh of conductor losses over this window.
Positive power deficits also occur. Their cause must be distinguished from
voltage, train-characteristic limits and numerical fallback before prescribing
an operational power reduction.

Use the system workbook to locate demanding periods, the timetable to identify
trains and positions, and the train workbook or longtable to quantify requested
and actual powers, voltage and current. `p_delta_W` is requested minus actual
power: positive values indicate traction shortfall; negative values indicate
curtailed regeneration. A blue timetable segment can still deliver regenerative
power. An empty deviations workbook does not establish unrestricted regeneration;
its positive-deficit and undervoltage thresholds must be checked against the
study's voltage level.

### Remaining evidence limits

The successful suite and supplied scenario results supplement, rather than
replace, Section 13. General convergence, independent KCL residual checks,
complete fallback coverage and feedback into train motion are not established
by these runs. Updated test sources are still needed before extending Appendix A
or assigning exact new test-method coverage to expansion and placement rules.

For the input schema, numerical equations, full result-file inventory and run
commands, see README Stories 6–7 and its result-file section. Keep this document
focused on functional expectations and the evidence supporting them.
