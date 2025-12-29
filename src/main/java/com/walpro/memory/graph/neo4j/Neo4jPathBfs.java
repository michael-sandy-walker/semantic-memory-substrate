package com.walpro.memory.graph.neo4j;

import com.walpro.memory.graph.PathExplanation;
import com.walpro.memory.model.ElementKind;
import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Relation;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import java.util.*;

import static org.neo4j.driver.Values.parameters;

final class Neo4jPathBfs {

    private Neo4jPathBfs() {}

    static PathExplanation explain(
            Driver driver,
            Neo4jMetaCodec metaCodec,
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

        while (!queue.isEmpty()) {
            State curS = queue.removeFirst();
            int d = depth.removeFirst();

            ElementRef cur = curS.ref();
            ElementKind cameFromKind = curS.cameFromKind();

            if (d >= maxHops) continue;

            List<Relation> outgoing = new ArrayList<>(relationsOf(driver, metaCodec, cur));
            outgoing.removeIf(r -> excluded.contains(r.relationType()));
            outgoing.sort(
                    Comparator.comparing(Relation::relationType, pref)
                            .thenComparing(Relation::id)
            );

            for (Relation r : outgoing) {

                ElementRef relNode = ElementRef.relation(r.id());

                // Step into the relation node first (prevents clique effect)
                if (!cur.equals(relNode)) {
                    State next = new State(relNode, cur.kind());
                    if (!parent.containsKey(next)) {
                        parent.put(next, curS);
                        parentHop.put(next, r);

                        if (next.ref().equals(b)) {
                            return reconstruct(parent, parentHop, next);
                        }

                        queue.addLast(next);
                        depth.addLast(d + 1);
                    }
                    continue;
                }

                // We are on the relation node: step out to linked elements with routing rules.
                List<ElementRef> candidates = r.links();

                // ABOUT routing rule
                if ("ABOUT".equals(r.relationType()) && cameFromKind != null) {
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

                // SELECTS routing rule
                if ("SELECTS".equals(r.relationType()) && cameFromKind != null) {
                    boolean hasInterpretation = candidates.stream().anyMatch(x -> x.kind() == ElementKind.INTERPRETATION);
                    if (hasInterpretation) {
                        if (cameFromKind == ElementKind.ARTIFACT) {
                            candidates = candidates.stream()
                                    .filter(x -> x.kind() == ElementKind.INTERPRETATION || x.kind() == ElementKind.EPISODE)
                                    .toList();
                        } else if (cameFromKind == ElementKind.INTERPRETATION) {
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
                        return reconstruct(parent, parentHop, next);
                    }

                    queue.addLast(next);
                    depth.addLast(d + 1);
                }
            }
        }

        return new PathExplanation(List.of(), List.of());
    }

    private static List<Relation> relationsOf(Driver driver, Neo4jMetaCodec metaCodec, ElementRef ref) {
        try (Session s = driver.session()) {
            return s.executeRead(tx -> tx.run(
                    """
                    // relations that mention this element
                    MATCH (r:Relation)-[:LINKS_TO]->(t {id: $id})
                    OPTIONAL MATCH (r)-[:LINKS_TO]->(tgt)
                    RETURN r, collect(tgt) as targets
                    UNION
                    // if this is a relation ref, include it directly
                    MATCH (r:Relation {id: $id})
                    OPTIONAL MATCH (r)-[:LINKS_TO]->(tgt)
                    RETURN r, collect(tgt) as targets
                    """,
                    parameters("id", ref.id())
            ).list()).stream().map(rec -> Neo4jMappers.toRelation(rec, metaCodec)).toList();
        }
    }

    private static PathExplanation reconstruct(
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
            if (hop != null) hops.add(hop);
            cur = parent.get(cur);
        }

        Collections.reverse(nodes);
        Collections.reverse(hops);
        return new PathExplanation(nodes, hops);
    }

    private record State(ElementRef ref, ElementKind cameFromKind) {}
}
