# Semantic Memory Substrate

Semantic Memory Substrate is a small graph-based system for recording what happened, what was produced, how it was interpreted, and how those elements relate across time and context.

The focus is on representing meaning explicitly, rather than inferring it from logs or execution order.

## Scope

The system records four kinds of elements:

- Episodes  
  Bounded contexts in which something occurred.

- Artifacts  
  Inspectable outputs or references, such as code fragments, diffs, text, metrics, or file paths.

- Interpretations  
  Structured statements describing how an episode or artifact is understood.

- Relations  
  Explicit links connecting multiple elements, used to express similarity, support, contradiction, dependency, or other associations.

The system does not assume a specific domain. Episodes may represent cycles, tasks, runs, experiments, or any other bounded activity.

## Design approach

- Meaning is expressed through explicit relations.
- Elements are linked based on observed structure or similarity.
- Temporal order is recorded but not treated as causal by default.
- The model favors compact representations that allow grouping and reuse.

## Intended use

The project is intended as a shared memory layer for systems that need to reason about past activity, recurring patterns, and contextual relationships.

It can be used independently or as a backing store for larger systems.

## Status

The project is in an early exploratory stage.
The current goal is to establish clear data structures and navigation patterns before adding persistence or integrations.
