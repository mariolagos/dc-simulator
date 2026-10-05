# *Under utveckling – ännu inte verifierad*

# Compact six-terminal installation input — step 1

Replace the two files under src/main/java/org/supply/loader. Add the JUnit 4 test under src/test/java/org/supply/loader. No solver or domain class changes are needed.

GridModelLoader now generates compact terminal nodes after explicit grid nodes and before line endpoint lookup. Existing terminal strings continue to refer to existing nodes.

A SIX_TERMINAL_RECTIFIER station can specify position_rwy and omit terminals entirely. This generates F_<id>_A/B/C/D and R_<id>_N/S, all at that position. This default does not imply a second physical track.

For custom node IDs or different track coordinates, supply all six named terminals as objects. Each object may set node_id and position_rwy; omitted values inherit the generated ID and station position. A terminal may also remain a string referring to an existing node. Partial terminal maps and node collisions are rejected.

```hocon
installation_model = SIX_TERMINAL_RECTIFIER
position_rwy = "101 1+000 U"
terminals {
  NORTH_POWER_LEFT { node_id = "F_SS1" }
  NORTH_POWER_RIGHT {}
  SOUTH_POWER_LEFT {}
  SOUTH_POWER_RIGHT {}
  NORTH_RETURN { node_id = "R_SS1" }
  SOUTH_RETURN {}
}
```

Remove generated station nodes from grid.nodes and the station's manual installation_connections. Keep explicit endpoint and junction nodes. Grid nodes must still be supplied as a list (possibly empty). Lines use the resulting node IDs in node_from_id / node_to_id. No connection is guessed from coordinates, route direction or names.

The example configs preserve existing line and route IDs. A-D and D-A still traverse the same electrical infrastructure. Unused south terminals are placed on U, as in the current single-track input; they do not introduce a D track. Incoming feeding lines connect to NORTH_POWER_LEFT; outgoing lines connect to NORTH_POWER_RIGHT. Both return lines use NORTH_RETURN. Breakers are not introduced in this step.

## Verification

Run in the dc-simulator project:

```bat
gradlew test --tests org.supply.loader.CompactInstallationFactoryTest
```

Then run dcStudy --check on both supplied configs using the existing study working directory. Both describe the same explicit six-terminal network: expected 20 nodes and 18 branches before train insertion (10 external branches and 8 internal branches).

Compare full study results with the existing two_sections configuration as well. It has 12 nodes and 10 branches. Six-terminal expansion adds 1e-9 ohm internal connections, so numerical equivalence to that older input requires a tolerance comparison; exact equality is not claimed. Verify train placement as well as voltages, station powers and losses before committing.

Keep conf/two_sections_compact.conf as the candidate configuration and conf/two_sections_explicit_terminals.conf as its explicit reference. Both retain study.id=two_sections and therefore write the same study output directory: preserve results between comparison runs.

The code and configs have been inspected structurally here. JUnit, Java compilation and full study runs have not been run because the complete project, dependencies and a Java compiler were not supplied.
