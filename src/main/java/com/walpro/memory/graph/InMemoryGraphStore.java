package com.walpro.memory.graph;

import com.walpro.memory.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryGraphStore implements GraphStore {

    private final Map<String, Episode> episodes = new ConcurrentHashMap<>();
    private final Map<String, Artifact> artifacts = new ConcurrentHashMap<>();
    private final Map<String, Interpretation> interpretations = new ConcurrentHashMap<>();
    private final Map<String, Relation> relations = new ConcurrentHashMap<>();
    private final Map<String, Action> actions = new ConcurrentHashMap<>();

    // indexes for navigation
    private final Map<ElementRef, Set<String>> relationIdsByRef = new ConcurrentHashMap<>();
    private final Map<String, Set<ElementRef>> refsByTrace = new ConcurrentHashMap<>();

    @Override
    public void putEpisode(Episode episode) {
        Objects.requireNonNull(episode, "episode");
        episodes.put(episode.id(), episode);
        indexTrace(ElementRef.episode(episode.id()), episode.trace());
    }

    @Override
    public void putArtifact(Artifact artifact) {
        Objects.requireNonNull(artifact, "artifact");
        artifacts.put(artifact.id(), artifact);
        indexTrace(ElementRef.artifact(artifact.id()), artifact.trace());
    }

    @Override
    public void putInterpretation(Interpretation interpretation) {
        Objects.requireNonNull(interpretation, "interpretation");
        interpretations.put(interpretation.id(), interpretation);
        indexTrace(ElementRef.interpretation(interpretation.id()), interpretation.trace());
    }

    @Override
    public void putRelation(Relation relation) {
        List<String> problems = GraphValidation.validateRelation(this, relation);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", problems));
        }

        Objects.requireNonNull(relation, "relation");
        relations.put(relation.id(), relation);

        ElementRef relRef = ElementRef.relation(relation.id());
        indexTrace(relRef, relation.trace());
        indexRelationLinks(relation);
    }

    @Override
    public void putAction(Action action) {
        Objects.requireNonNull(action, "action");
        actions.put(action.getId(), action);

        indexTrace(ElementRef.action(action.getId()), action.getTrace());
    }

    @Override
    public Optional<Episode> getEpisode(String id) {
        return Optional.ofNullable(episodes.get(id));
    }

    @Override
    public Optional<Artifact> getArtifact(String id) {
        return Optional.ofNullable(artifacts.get(id));
    }

    @Override
    public Optional<Interpretation> getInterpretation(String id) {
        return Optional.ofNullable(interpretations.get(id));
    }

    @Override
    public Optional<Relation> getRelation(String id) {
        return Optional.ofNullable(relations.get(id));
    }

    @Override
    public Optional<Action> getAction(String actionId) {
        if (actionId == null || actionId.isBlank()) return Optional.empty();
        return Optional.ofNullable(actions.get(actionId));
    }

    @Override
    public GraphQuery query() {
        return new GraphQuery() {
            @Override
            public List<Relation> relationsOf(ElementRef ref) {
                if (ref == null) return List.of();
                Set<String> ids = relationIdsByRef.getOrDefault(ref, Set.of());
                return ids.stream()
                        .map(relations::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }

            @Override
            public List<ElementRef> neighborsOf(ElementRef ref) {
                if (ref == null) return List.of();
                return relationsOf(ref).stream()
                        .flatMap(r -> r.links().stream())
                        .filter(other -> !other.equals(ref))
                        .distinct()
                        .collect(Collectors.toList());
            }

            @Override
            public List<ElementRef> byTrace(String trace) {
                if (trace == null || trace.isBlank()) return List.of();
                return refsByTrace.getOrDefault(trace, Set.of()).stream().toList();
            }

            @Override
            public List<Relation> relationsBetween(ElementRef a, ElementRef b) {
                if (a == null || b == null) return List.of();
                if (a.equals(b)) return relationsOf(a);

                // Intersect relation-id sets for cheap direct-connect lookup.
                Set<String> aIds = relationIdsByRef.getOrDefault(a, Set.of());
                Set<String> bIds = relationIdsByRef.getOrDefault(b, Set.of());
                if (aIds.isEmpty() || bIds.isEmpty()) return List.of();

                // Iterate smaller set for performance.
                Set<String> smaller = aIds.size() <= bIds.size() ? aIds : bIds;
                Set<String> larger  = smaller == aIds ? bIds : aIds;

                return smaller.stream()
                        .filter(larger::contains)
                        .map(relations::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }

            @Override
            public List<ElementRef> neighborhood(ElementRef start, int hops) {
                if (start == null) return List.of();
                if (hops <= 0) return List.of(start);

                Set<ElementRef> visited = new LinkedHashSet<>();
                ArrayDeque<ElementRef> queue = new ArrayDeque<>();
                ArrayDeque<Integer> depth = new ArrayDeque<>();

                visited.add(start);
                queue.add(start);
                depth.add(0);

                while (!queue.isEmpty()) {
                    ElementRef cur = queue.removeFirst();
                    int d = depth.removeFirst();
                    if (d >= hops) continue;

                    // Neighborhood is reachability-only (node set). It uses neighborsOf(), which discards relation identity.
                    // If you need a “why chain”, use explainPathBetween* instead.
                    for (ElementRef n : neighborsOf(cur)) {
                        if (visited.add(n)) {
                            queue.addLast(n);
                            depth.addLast(d + 1);
                        }
                    }
                }
                return new ArrayList<>(visited);
            }

            @Override
            public List<Relation> relationsOfType(ElementRef ref, String relationType) {
                if (ref == null) return List.of();
                if (relationType == null || relationType.isBlank()) return List.of();

                return relationsOf(ref).stream()
                        .filter(r -> relationType.equals(r.relationType()))
                        .toList();
            }

            @Override
            public List<Artifact> findArtifactsByReference(String kind, String reference) {
                if (kind == null || kind.isBlank()) return List.of();
                if (reference == null || reference.isBlank()) return List.of();

                return artifacts.values().stream()
                        .filter(a -> kind.equals(a.kind()))
                        .filter(a -> reference.equals(a.reference()))
                        .toList();
            }

            @Override
            public List<ElementRef> pathBetween(ElementRef a, ElementRef b, int maxHops) {
                return explainPathBetween(a,b,maxHops).nodes();
            }

            @Override
            public PathExplanation explainPathBetween(ElementRef a, ElementRef b, int maxHops) {
                return explainPathBetween(a, b, maxHops, Comparator.naturalOrder());
            }

            @Override
            public PathExplanation explainPathBetween(
                    ElementRef a,
                    ElementRef b,
                    int maxHops,
                    Comparator<String> relationTypePreference
            ) {
                return explainPathBetweenExcludingTypes(a, b, maxHops, Set.of(), relationTypePreference);
            }

            @Override
            public PathExplanation explainPathBetweenExcludingTypes(
                    ElementRef a,
                    ElementRef b,
                    int maxHops,
                    Set<String> excludedRelationTypes,
                    Comparator<String> relationTypePreference
            ) {
                if (a == null || b == null) return new PathExplanation(List.of(), List.of());
                if (a.equals(b)) return new PathExplanation(List.of(a), List.of());
                if (maxHops <= 0) return new PathExplanation(List.of(), List.of());

                Set<String> excluded = (excludedRelationTypes == null) ? Set.of() : excludedRelationTypes;
                Comparator<String> pref = (relationTypePreference != null) ? relationTypePreference : Comparator.naturalOrder();

                Deque<State> queue = new ArrayDeque<>();
                Deque<Integer> depth = new ArrayDeque<>();

                Map<State, State> parent = new HashMap<>();
                Map<State, Relation> parentHop = new HashMap<>();

                State start = new State(a, null);
                queue.addLast(start);
                depth.addLast(0);
                parent.put(start, null);

                // depth d counts hops (relations traversed) from the start node.
                // We only expand nodes with d < maxHops, so maxHops=1 permits one hop.
                while (!queue.isEmpty()) {
                    State curS = queue.removeFirst();
                    int d = depth.removeFirst();

                    ElementRef cur = curS.ref();
                    ElementKind cameFromKind = curS.cameFromKind();

                    if (d >= maxHops) continue;

                    List<Relation> outgoing = new ArrayList<>(relationsOf(cur));
                    outgoing.removeIf(r -> excluded.contains(r.relationType()));
                    outgoing.sort(
                            Comparator.comparing(Relation::relationType, pref)
                                    .thenComparing(Relation::id)
                    );

                    for (Relation r : outgoing) {

                        ElementRef relNode = ElementRef.relation(r.id());

                        // If we are currently NOT on the relation node, we only step into the relation node.
                        // This prevents the clique effect (Artifact -> Artifact via the same ABOUT).
                        if (!cur.equals(relNode)) {
                            State next = new State(relNode, cur.kind());

                            if (!parent.containsKey(next)) {
                                parent.put(next, curS);
                                parentHop.put(next, r);

                                // If target is a RELATION (unlikely here), check by ref:
                                if (next.ref().equals(b)) {
                                    return reconstructExplanation(parent, parentHop, next);
                                }

                                queue.addLast(next);
                                depth.addLast(d + 1);
                            }
                            continue;
                        }

                        // If we ARE on the relation node, we can step to linked elements,
                        // but for ABOUT we route Artifact->Interpretation->Artifact when possible.

                        List<ElementRef> candidates = r.links();

                        // Special routing rule for ABOUT:
                        if (RelationTypes.ABOUT.equals(r.relationType()) && cameFromKind != null) {
                            boolean hasInterpretation = candidates.stream().anyMatch(x -> x.kind() == ElementKind.INTERPRETATION);

                            if (hasInterpretation) {
                                if (cameFromKind == ElementKind.ARTIFACT) {
                                    candidates = candidates.stream()
                                            .filter(x -> x.kind() == ElementKind.INTERPRETATION)
                                            .toList();
                                } else if (cameFromKind == ElementKind.INTERPRETATION) {
                                    candidates = candidates.stream()
                                            .filter(x -> x.kind() != ElementKind.INTERPRETATION)
                                            .toList();
                                }
                            }
                        }

                        // Special routing rule for SELECTS:
                        if (RelationTypes.SELECTS.equals(r.relationType()) && cameFromKind != null) {
                            boolean hasInterpretation = candidates.stream().anyMatch(x -> x.kind() == ElementKind.INTERPRETATION);

                            if (hasInterpretation) {
                                if (cameFromKind == ElementKind.ARTIFACT) {
                                    // from artifact: step to the chosen interpretation (and optionally the episode),
                                    // but NOT directly to other artifacts
                                    candidates = candidates.stream()
                                            .filter(x -> x.kind() == ElementKind.INTERPRETATION || x.kind() == ElementKind.EPISODE)
                                            .toList();
                                } else if (cameFromKind == ElementKind.INTERPRETATION) {
                                    // from interpretation: allow stepping to artifacts (and maybe episode)
                                    candidates = candidates.stream()
                                            .filter(x -> x.kind() == ElementKind.ARTIFACT || x.kind() == ElementKind.EPISODE)
                                            .toList();
                                }
                            }
                        }

                        for (ElementRef n : candidates) {
                            if (n.equals(cur)) continue;

                            State next = new State(n, cur.kind());
                            if (parent.containsKey(next)) continue;

                            parent.put(next, curS);
                            parentHop.put(next, r);

                            if (n.equals(b)) {
                                return reconstructExplanation(parent, parentHop, next);
                            }

                            queue.addLast(next);
                            depth.addLast(d + 1);
                        }
                    }
                }

                return new PathExplanation(List.of(), List.of());
            }

        };
    }

    @Override
    public List<Interpretation> getAllInterpretations() {
        return new ArrayList<>(interpretations.values());
    }

    private PathExplanation reconstructExplanation(
            Map<State, State> parent,
            Map<State, Relation> parentHop,
            State end
    ) {
        List<ElementRef> nodes = new ArrayList<>();
        List<Relation> hops = new ArrayList<>();

        State cur = end;
        while (cur != null) {
            nodes.add(cur.ref());

            Relation hop = parentHop.get(cur);
            if (hop != null) {
                hops.add(hop);
            }

            cur = parent.get(cur);
        }

        Collections.reverse(nodes);
        Collections.reverse(hops);

        return new PathExplanation(nodes, hops);
    }

    private void indexRelationLinks(Relation relation) {
        ElementRef relRef = ElementRef.relation(relation.id());

        // Make the relation node itself reachable via relationsOf(relRef)
        relationIdsByRef
                .computeIfAbsent(relRef, __ -> ConcurrentHashMap.newKeySet())
                .add(relation.id());

        // And keep existing indexing for every linked element
        for (ElementRef ref : relation.links()) {
            relationIdsByRef
                    .computeIfAbsent(ref, __ -> ConcurrentHashMap.newKeySet())
                    .add(relation.id());
        }
    }

    private void indexTrace(ElementRef ref, String trace) {
        refsByTrace
                .computeIfAbsent(trace, __ -> ConcurrentHashMap.newKeySet())
                .add(ref);
    }

    private record State(ElementRef ref, ElementKind cameFromKind) {
    }
}
