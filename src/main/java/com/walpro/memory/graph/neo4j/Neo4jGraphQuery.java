package com.walpro.memory.graph.neo4j;

import com.walpro.memory.graph.GraphQuery;
import com.walpro.memory.graph.PathExplanation;
import com.walpro.memory.model.Artifact;
import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Relation;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.neo4j.driver.Values.parameters;

public final class Neo4jGraphQuery implements GraphQuery {

    private final Driver driver;
    private final Neo4jMetaCodec metaCodec;

    public Neo4jGraphQuery(Driver driver, Neo4jMetaCodec metaCodec) {
        this.driver = Objects.requireNonNull(driver, "driver");
        this.metaCodec = Objects.requireNonNull(metaCodec, "metaCodec");
    }

    @Override
    public List<Relation> relationsOf(ElementRef ref) {
        if (ref == null) return List.of();
        try (Session s = driver.session()) {
            return s.executeRead(tx -> tx.run(
                    """
                    // (1) relations that link to the target
                    MATCH (r:Relation)-[:LINKS_TO]->(t {id: $id})
                    OPTIONAL MATCH (r)-[:LINKS_TO]->(tgt)
                    RETURN r, collect(tgt) as targets
                    UNION
                    // (2) if target itself is a relation node, include it directly (matches in-memory behavior)
                    MATCH (r:Relation {id: $id})
                    OPTIONAL MATCH (r)-[:LINKS_TO]->(tgt)
                    RETURN r, collect(tgt) as targets
                    """,
                    parameters("id", ref.id())
            ).list()).stream().map(rec -> Neo4jMappers.toRelation(rec, metaCodec)).toList();
        }
    }

    @Override
    public List<ElementRef> neighborsOf(ElementRef ref) {
        if (ref == null) return List.of();
        return relationsOf(ref).stream()
                .flatMap(r -> r.links().stream())
                .filter(other -> !other.equals(ref))
                .distinct()
                .toList();
    }

    @Override
    public List<ElementRef> byTrace(String trace) {
        if (trace == null || trace.isBlank()) return List.of();
        try (Session s = driver.session()) {
            return s.executeRead(tx -> tx.run(
                    """
                    MATCH (n)
                    WHERE n.trace = $trace
                    RETURN n
                    """,
                    parameters("trace", trace)
            ).list()).stream().map(rec -> {
                var n = rec.get("n").asNode();
                return new ElementRef(Neo4jMappers.kindFromLabels(n.labels()), n.get("id").asString());
            }).toList();
        }
    }

    @Override
    public List<Relation> relationsBetween(ElementRef a, ElementRef b) {
        if (a == null || b == null) return List.of();
        if (a.equals(b)) return relationsOf(a);

        try (Session s = driver.session()) {
            return s.executeRead(tx -> tx.run(
                    """
                    MATCH (r:Relation)-[:LINKS_TO]->(x {id: $a})
                    MATCH (r)-[:LINKS_TO]->(y {id: $b})
                    OPTIONAL MATCH (r)-[:LINKS_TO]->(all)
                    RETURN r, collect(all) as targets
                    """,
                    parameters("a", a.id(), "b", b.id())
            ).list()).stream().map(rec -> Neo4jMappers.toRelation(rec, metaCodec)).toList();
        }
    }

    @Override
    public List<ElementRef> neighborhood(ElementRef start, int hops) {
        if (start == null) return List.of();
        if (hops <= 0) return List.of(start);

        // Same semantics as in-memory: reachability-only using neighborsOf()
        java.util.Set<ElementRef> visited = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<ElementRef> q = new java.util.ArrayDeque<>();
        java.util.ArrayDeque<Integer> d = new java.util.ArrayDeque<>();

        visited.add(start);
        q.add(start);
        d.add(0);

        while (!q.isEmpty()) {
            ElementRef cur = q.removeFirst();
            int depth = d.removeFirst();
            if (depth >= hops) continue;

            for (ElementRef n : neighborsOf(cur)) {
                if (visited.add(n)) {
                    q.addLast(n);
                    d.addLast(depth + 1);
                }
            }
        }

        return visited.stream().toList();
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

        try (Session s = driver.session()) {
            return s.executeRead(tx -> tx.run(
                    """
                    MATCH (a:Artifact {kind: $kind, reference: $ref})
                    RETURN a
                    """,
                    parameters("kind", kind, "ref", reference)
            ).list()).stream().map(rec -> {
                var n = rec.get("a").asNode();
                return new Artifact(
                        n.get("id").asString(),
                        n.get("kind").asString(),
                        n.get("reference").asString(),
                        n.get("trace").asString()
                );
            }).toList();
        }
    }

    @Override
    public List<ElementRef> pathBetween(ElementRef a, ElementRef b, int maxHops) {
        return explainPathBetween(a, b, maxHops).nodes();
    }

    @Override
    public PathExplanation explainPathBetween(ElementRef a, ElementRef b, int maxHops) {
        return explainPathBetween(a, b, maxHops, Comparator.naturalOrder());
    }

    @Override
    public PathExplanation explainPathBetween(ElementRef a, ElementRef b, int maxHops, Comparator<String> relationTypePreference) {
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
        return Neo4jPathBfs.explain(driver, metaCodec, a, b, maxHops, excludedRelationTypes, relationTypePreference);
    }
}
