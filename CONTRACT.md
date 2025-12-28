# Contract

This document defines the structural guarantees of the Semantic Memory Substrate.
Anything stored in the system must satisfy these rules.

The contract is intentionally small. It may evolve, but changes should be deliberate and rare.

## Common rules (apply to all elements)

- Every element has a stable `id`.
- Every element has a `trace` value.
- Traces are opaque identifiers used for grouping and comparison.
- Elements do not embed meaning implicitly through position or naming.
- All meaning is expressed through explicit links.

## Episode

An Episode represents a bounded context in which something occurred.

Required fields:
- id
- startedAt
- endedAt (may be equal to startedAt)
- trace

Rules:
- An Episode may involve zero or more Artifacts.
- An Episode may be referenced by zero or more Interpretations.
- An Episode does not imply success, failure, or intent by itself.

## Artifact

An Artifact represents an inspectable output or reference.

Required fields:
- id
- kind
- reference (path, hash, URI, or similar)
- trace

Rules:
- An Artifact may be linked to multiple Episodes.
- Artifact content is not required to be stored inline.
- Artifacts do not carry semantic judgments.

## Interpretation

An Interpretation represents a structured statement about one or more elements.

Required fields:
- id
- type
- summary
- confidence
- trace

Rules:
- An Interpretation must reference at least one Episode or Artifact.
- Interpretations describe observations, not actions.
- Interpretations may be revised or contradicted by later Interpretations.

## Relation

A Relation represents a connection between multiple elements.

Required fields:
- id
- relationType
- trace

Rules:
- A Relation must link at least two elements.
- Relations may link any combination of Episodes, Artifacts, and Interpretations.
- Relations may carry optional metadata (e.g. strength, justification reference).
- Relations do not imply direction unless explicitly modeled.

## Graph-level invariants

- The graph may contain cycles.
- The graph is expected to grow over time.
- Deletion is allowed but should be rare and explicit.
- No element type is required to form a hierarchy.

## Out of scope (for now)

- Execution logic
- Agent behavior
- Planning or decision enforcement
- Scoring systems
- Domain-specific semantics
