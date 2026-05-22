# Application Configuration

## Purpose

Defines the structure and contract of `application.conf`.

The configuration describes:

- railway topology
- electrical topology
- train movements
- export settings

The configuration is transformed into a MATLAB-compatible CSV package by `DcExporter`.

---

# General Principles

Naming follows snake_case and SI units.

Railway coordinates are authoritative in configuration.

Model coordinates are derived during loading and/or export.

Nodes are topology only. Electrical semantics are expressed via installations and installation connections.

No inference from identifiers or naming conventions is allowed.

---

# Coordinate Principles

Position is represented in two forms:

- railway coordinates
- model coordinates

Railway coordinates are used for input and reporting.

Model coordinates are represented by:

```text
track_id
position_m
```

and are used in exported CSV files and simulation.

Model coordinates are derived from the applicable track mapping and are therefore not authoritative in configuration.

---

# Top-Level Structure

```hocon
dcsim {
  simulation_control
  export
  grid
  track
  traffic
}
```

---

# Relation Between Sections

## track and grid

The grid section and the track section are related through railway coordinates.

- `track` defines railway structure and continuity
- `grid` defines electrical topology
- railway coordinates provide the explicit linkage between the two sections

The number of electrical nodes, electrical line boundaries, and resistance parameters may vary independently of the track structure.

---

## traffic and track

Traffic is defined in terms of stations and railway positions.

- `track` defines railway continuity and positions
- `traffic` references stations defined in the track section

No inference of routes shall be performed beyond what is defined by the track structure.

Train movement shall follow the connectivity defined by the track section.

---

## traffic and grid

Traffic interacts with the electrical network through positions on the track.

- trains are mapped to electrical nodes via railway position mapping
- the electrical network is defined independently in the grid section
- no direct dependency between traffic and grid topology shall exist

---

## simulation_control

Defines the global simulation time model.

```hocon
simulation_control {
  tick_duration_s
  simulation_start
  simulation_end
  simulation_speed
}
```

Fields:

- `tick_duration_s`: internal simulation timestep in seconds
- `simulation_start`: simulation start time (`HH:mm:ss`)
- `simulation_end`: simulation end time (`HH:mm:ss`)
- `simulation_speed`: optional execution speed multiplier for interactive execution environments

---

## export

Defines CSV export behaviour.

```hocon
export {
  csv_every_nth_step
  export_resolution_s
}
```

Fields:

- `csv_every_nth_step`: export every Nth internal timestep
- `export_resolution_s`: exported temporal resolution in seconds

---

# grid

The grid section defines the electrical topology exported to MATLAB.

Electrical topology is explicit.

No topology inference shall be performed.

---

## nodes

```hocon
nodes = [
  { node_id, position_rwy }
]
```

Nodes represent topology only.

No node type is part of the public API.

---

## lines

```hocon
lines = [
  { node_from_id, node_to_id, resistance_ohm_per_m }
]
```

Line length is derived from node positions after mapping to model coordinates.

---

## power_installations

```hocon
power_installations = [
  {
    installation_id,
    installation_type,
    emf_V,
    internal_resistance_ohm,
    rectifier_type
  }
]
```

```text
installation_type ∈ { SUBSTATION, POINT }
rectifier_type ∈ { DIODE, THYRISTOR }
```

Represents electrical devices connected to the network.

`POINT` represents non-substation electrical connection points such as feeder or return nodes.

---

## installation_connections

```hocon
installation_connections = [
  { installation_id, node_id, connection_type }
]
```

```text
connection_type ∈ { FEEDING, RETURN }
```

Used for topologic separation and as a sanity check preventing mixing of feeding and return networks.

---

## system_parameters

Defines global electrical system limits exported to MATLAB.

The following parameters are exported to `systemParameters.csv`:

```text
u_nominal_V
u_min_V
u_cutoff_V
u_max_V
i_train_max_A
```

Fields:

- `u_nominal_V`: nominal system voltage
- `u_min_V`: minimum allowed operating voltage
- `u_cutoff_V`: train cutoff voltage
- `u_max_V`: maximum allowed voltage
- `i_train_max_A`: maximum train current
---

# track

The track section defines railway coordinate continuity and named railway positions.

The track section supports:

- coordinate transformation
- section continuity
- connectivity between railway sections
- simulation export

The track section does not define electrical topology.

Electrical topology is defined explicitly in the grid section.

No automatic generation of grid nodes or grid lines from the track section shall be performed.

---

## stations

```hocon
stations = [
  { name, position_rwy, track_id }
]
```

Defines named locations in the railway network.

Fields:

- `name`: station name
- `position_rwy`: railway coordinate
- `track_id`: optional track identifier

Each station shall be defined by a single railway coordinate.

---

## kilometer_boards

```hocon
kilometer_boards = [
  { section, km, length }
]
```

Defines railway positioning within each section.

Fields:

- `section`: section identifier
- `km`: position within the section (`km+m`)
- `length`: distance to the next entry

Each entry defines a position and the distance to the next position in the same section.

The order of entries:

- shall be preserved
- shall not be sorted
- defines the direction and continuity of the section

---

## junctions

```hocon
junctions = [
  { from_position_rwy, to_position_rwy }
]
```

Each junction connects two railway coordinates and defines continuity between sections or branches.

A junction does not define an electrical node or electrical line.

Electrical topology remains explicitly defined in the grid section.

---

# traffic

Traffic defines planned train movements over the railway reference model.

```hocon
traffic {
  timetable {
    trains = [
      { id, template_id, departure }
    ]
  }

  templates = {
    <template_id> = {
      run_excel
      motoring_and_auxiliaries_in_same_model
      auxiliary_power_W

      stops = [
        { signature, arrival, departure }
      ]
    }
  }
}
```

---

## timetable.trains

Defines concrete train instances.

Fields:

- `id`: unique train instance identifier
- `template_id`: reference to a traffic template
- `departure`: departure time in simulation time

Each train shall reference exactly one traffic template.

Traffic shall not define electrical topology.

---

## templates

Defines reusable train movement templates.

Each template defines:

- movement structure
- stop sequence
- timing semantics
- associated run profile
- auxiliary power behaviour

Fields:

- `run_excel`: path to the train run profile
- `motoring_and_auxiliaries_in_same_model`
- `auxiliary_power_W`
- `stops`

---

## stops

Defines ordered station stops within a template.

Each stop contains:

- `signature`
- optional `arrival`
- optional `departure`

`signature` shall reference a station defined in `track.stations`.

The order of stops:

- shall be preserved
- defines planned movement direction
- shall not be reordered automatically

At least two stops shall be defined for a movement template.

No implicit route inference beyond the ordered stop sequence and track structure shall be performed.

---

# Path Resolution

Paths may be absolute or relative.

The `application.conf` argument may be absolute or relative to the current working directory.

The optional export directory may also be absolute or relative to the current working directory.

Paths inside `application.conf`, such as:

```hocon
run_excel = "templates/t1_up_A-B.xlsx"
```

may be absolute or relative.

Relative paths inside `application.conf` are resolved relative to the directory containing `application.conf`.

Example:

```text
project/validationTests/C1/
  application.conf
  templates/
    t1_up_A-B.xlsx
    t1_down_A-B.xlsx
  dc/
    exports/
```

In this example:

```hocon
run_excel = "templates/t1_up_A-B.xlsx"
```

resolves to:

```text
project/validationTests/C1/templates/t1_up_A-B.xlsx
```

---

