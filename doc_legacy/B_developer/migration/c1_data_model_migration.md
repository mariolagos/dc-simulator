## Migration Note (Internal)

This release introduces a transition towards a new data model aligned with the MATLAB interface (API v1.2).

### Key changes

- Node identity is now **string-based (`node_id`)** in all external interfaces
- Nodes are **topology-only** (no type or semantics in API)
- Substations are replaced by the unified concept:
    - `power_installations`
    - `installation_connections`
- Naming is standardized to **snake_case** and **SI units**
- Positioning is defined by:
    - `track_id`
    - `position_m`

### Transitional state (C1)

The current implementation is a **hybrid**:

- Internal model still uses:
    - numeric node identifiers (`internal_id`)
- External interface uses:
    - string-based `node_id`

Some legacy structures remain in the codebase for compatibility, but are not part of the public API.

### Known gaps

- `track_id` in `run.csv` is currently hardcoded (single-track assumption)
- Position offset is handled manually in `nodes.csv`
- No full mapping yet between:
    - railway coordinates
    - model coordinates

### Post-C1 direction

- Fully migrate the internal model to `string node_id`
- Remove legacy node handling and dual representations
- Implement proper derivation of:
    - `track_id`
    - position mapping
- Consolidate configuration and remove duplicated structures

### Summary

This release establishes the external contract and data model.

Internal implementation will be aligned in subsequent iterations.