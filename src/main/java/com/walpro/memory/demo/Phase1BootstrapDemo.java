package com.walpro.memory.demo;

import com.walpro.memory.graph.GraphQuery;
import com.walpro.memory.graph.InMemoryGraphStore;
import com.walpro.memory.model.Artifact;
import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Episode;
import com.walpro.memory.model.Interpretation;
import com.walpro.memory.model.Relation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 1 demo:
 * - create one Episode
 * - add a few Artifacts + Interpretations
 * - connect everything via n-ary Relation nodes
 * - exercise navigation queries (relationsOf / neighborsOf / byTrace)
 */
public final class Phase1BootstrapDemo {

    private Phase1BootstrapDemo() {
    }

    public static void main(String[] args) {
        InMemoryGraphStore store = new InMemoryGraphStore();

        String trace = "phase0-bootstrap-" + Instant.now().toString();

        Episode ep = new Episode(
                id("ep"),
                Instant.now(),
                Instant.now(),
                trace
        );

        Artifact readme = new Artifact(
                id("art"),
                "DOC",
                "README.md",
                trace
        );

        Artifact contract = new Artifact(
                id("art"),
                "DOC",
                "CONTRACT.md",
                trace
        );

        Interpretation scope = new Interpretation(
                id("int"),
                "OBSERVATION",
                "Scope is intentionally constrained; the model avoids execution semantics.",
                0.85,
                trace
        );

        Interpretation revision = new Interpretation(
                id("int"),
                "REVISION",
                "The substrate is usable without persistence; adapters can come later if navigation pressure appears.",
                0.70,
                trace
        );

        store.putEpisode(ep);
        store.putArtifact(readme);
        store.putArtifact(contract);
        store.putInterpretation(scope);
        store.putInterpretation(revision);

        // Relations: keep relationType as simple strings for now.
        store.putRelation(rel("DESCRIBES", trace, ElementRef.episode(ep.id()), ElementRef.artifact(readme.id())));
        store.putRelation(rel("DESCRIBES", trace, ElementRef.episode(ep.id()), ElementRef.artifact(contract.id())));

        store.putRelation(rel("SUPPORTS", trace, ElementRef.interpretation(scope.id()), ElementRef.artifact(contract.id())));
        store.putRelation(Relation.of(
                id("rel"),
                "SUPPORTS",
                trace,
                List.of(
                        ElementRef.interpretation(scope.id()),
                        ElementRef.episode(ep.id()),
                        ElementRef.artifact(contract.id()) // justification artifact as an explicit link
                ),
                Map.of("strength", 0.8)
        ));


        // Revision / disagreement without overwriting anything.
        store.putRelation(rel("REFINES", trace, ElementRef.interpretation(revision.id()), ElementRef.interpretation(scope.id())));

        // ---- Navigation exercises ----
        GraphQuery q = store.query();

        ElementRef epRef = ElementRef.episode(ep.id());
        ElementRef scopeRef = ElementRef.interpretation(scope.id());

        System.out.println("Trace: " + trace);
        System.out.println();

        System.out.println("byTrace(trace):");
        q.byTrace(trace).forEach(r -> System.out.println("  - " + r.kind() + ":" + r.id()));
        System.out.println();

        System.out.println("relationsBetween(Episode, Interpretation scope):");
        q.relationsBetween(epRef, scopeRef).forEach(r -> System.out.println("  - " + r.relationType() + " " + r.links()));
        System.out.println();

        System.out.println("neighborhood(Episode, 2 hops):");
        q.neighborhood(epRef, 2).forEach(r -> System.out.println("  - " + r.kind() + ":" + r.id()));
        System.out.println();

        System.out.println("relationsOf(Episode):");
        q.relationsOf(epRef).forEach(Phase1BootstrapDemo::printRelation);
        System.out.println();

        System.out.println("relationsBetween(Episode, Interpretation scope):");
        q.relationsBetween(epRef, scopeRef).forEach(Phase1BootstrapDemo::printRelation);
        System.out.println();

        System.out.println("supports relations of Episode:");
        q.relationsOfType(epRef, "SUPPORTS").forEach(Phase1BootstrapDemo::printRelation);
        System.out.println();

        System.out.println("neighborsOf(Episode):");
        q.neighborsOf(epRef).forEach(r -> System.out.println("  - " + r.kind() + ":" + r.id()));
        System.out.println();

        System.out.println("neighborsOf(Interpretation scope):");
        q.neighborsOf(scopeRef).forEach(r -> System.out.println("  - " + r.kind() + ":" + r.id()));
    }

    private static Relation rel(String type, String trace, ElementRef a, ElementRef b) {
        return Relation.of(id("rel"), type, trace, List.of(a, b));
    }

    private static Relation rel(String type, String trace, ElementRef a, ElementRef b, Map<String, Object> meta) {
        return Relation.of(id("rel"), type, trace, List.of(a, b), meta);
    }

    private static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static void printRelation(Relation r) {
        if (r.meta() == null || r.meta().isEmpty()) {
            System.out.println("  - " + r.relationType() + " " + r.links());
        } else {
            System.out.println("  - " + r.relationType() + " " + r.links() + " meta=" + r.meta());
        }
    }
}
