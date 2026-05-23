# DcExporter

## Purpose

`DcExporter` generates a MATLAB-compatible input package from an `application.conf` railway scenario configuration.

The exporter transforms:

- railway track structure
- electrical grid topology
- traffic definitions
- train run profiles

into a set of CSV files consumed by the MATLAB electrical solver.

`DcExporter` does not perform electrical simulation itself.

---

# Responsibilities

`DcExporter` is responsible for:

- loading and validating scenario configuration
- transforming railway coordinates into model coordinates
- exporting MATLAB input CSV files
- generating `run.csv` from traffic templates and run profiles

`DcExporter` is not responsible for:

- electrical calculations
- solver algorithms
- result interpretation
- numerical verification of MATLAB results

Those responsibilities belong to the MATLAB solver and downstream verification workflows.

---

# Overview

The export flow is:

```text
application.conf
        ↓
DcExporter
        ↓
CSV export package
        ↓
MATLAB solver
        ↓
result CSV files
```

The exported package contains:

- electrical topology
- railway topology
- train movement and power demand

---

# Configuration Contract

The structure and semantics of `application.conf` are defined in:

```text
application-configuration.md
```
---

# Command Line Usage

```bash
gradlew dcExporter -Pargs="<application.conf>" ["<export folder>"]
```

Arguments:

1. path to `application.conf`
2. optional export directory

If no export directory is provided, exports are written to:

```text
<directory containing application.conf>/dc/exports
```

---

# CSV Interface

The generated CSV input package is described in:

```text
matlab-csv-interface.md
```

---

# Generated Files

`DcExporter` generates the following CSV files:

## Electrical topology

- `nodes.csv`
- `lines.csv`
- `powerInstallations.csv`
- `installationConnections.csv`
- `systemParameters.csv`

## Railway topology

- `track_stations.csv`
- `track_segments.csv`
- `track_junctions.csv`

## Dynamic train model

- `run.csv`

---

# Current Scope

The current implementation is aligned with the C1 validation scenario and MATLAB export interface.

The exporter currently targets DC railway simulations using the MATLAB solver contract.