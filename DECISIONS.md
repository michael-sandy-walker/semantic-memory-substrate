This file captures small, stable choices that keep the project focused.

## Current choices

- Language: Java
- Initial storage: in-memory graph
- Persistence: optional later (Neo4j adapter), not required for the first milestone
- Data elements: Episode, Artifact, Interpretation, Relation
- Relations: modeled with relation nodes (n-ary), not only pairwise edges
- Artifact content: stored as references first (path/hash/URI), not embedded blobs by default
- Goal: support navigation and pattern linking across time and context, without assuming a domain model

## Working constraints

- Keep the model small and easy to traverse.
- Prefer explicit links over implicit meaning.
- Add new node types only when repeated pressure appears in usage examples.
- 
## Phases and stabilization points

The project is developed in small, explicit phases.
Each phase introduces structure, then freezes it via constraints and validation
before moving on.

### Phase 1 — Core graph vocabulary (stabilized)

Established the minimal element set and invariants:

- Elements: Episode, Artifact, Interpretation, Relation
- All meaning expressed via explicit relations
- No implicit hierarchy or causality
- Graph may contain cycles

This phase defines *what can exist* in the graph.

### Phase 2 — Trace stitching and navigation (stabilized)

Established how elements relate across time and context:

- Traces are opaque grouping identifiers
- Episodes may be stitched without implying causality
- Navigation and path explanation precede persistence
- Relations are traversed as first-class nodes (hyperedges)

This phase defines *how the graph can be navigated*.

### Phase 3 — Interpretation weaving and selection (stabilized)

Established how meaning evolves without overwriting history:

- Interpretations must be connected via relations
- `ABOUT` attaches interpretations to concrete elements
- Interpretations may relate via debate relations
  (`CONTRADICTS`, `REFINES`, `SUPPORTS`)
- `SELECTS` expresses the current interpretation explicitly
- `SUPERSEDES` models change in current understanding
- GraphValidation enforces these invariants

This phase defines *how understanding changes over time*.