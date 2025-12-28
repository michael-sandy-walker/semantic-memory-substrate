package com.walpro.memory.demo;

import com.walpro.memory.graph.GraphQuery;
import com.walpro.memory.graph.InMemoryGraphStore;
import com.walpro.memory.graph.PathExplanation;
import com.walpro.memory.model.Artifact;
import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Episode;
import com.walpro.memory.model.Interpretation;
import com.walpro.memory.model.Relation;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 2 demo:
 * - create two Episodes with different traces
 * - add one "same kind" Artifact in each episode
 * - add cross-trace links (SIMILAR_TO, SAME_AS, ABOUT) between those artifacts
 * - prove "how are these connected?" via a bounded BFS path query
 */
public final class Phase2CrossTraceDemo {

    private Phase2CrossTraceDemo() {
    }

    public static void main(String[] args) {
        InMemoryGraphStore store = new InMemoryGraphStore();

        Instant now = Instant.now();
        String trace1 = "phase2-ep1-" + now;
        String trace2 = "phase2-ep2-" + now;
        String stitchTrace = "phase2-stitch-" + now;
        String stitchTrace2 = "phase2-stitch2-" + now;
        String bridgeTrace = "phase2-bridge-" + now;

        Episode ep1 = new Episode(id("ep"), Instant.now(), Instant.now(), trace1);
        Episode ep2 = new Episode(id("ep"), Instant.now(), Instant.now(), trace2);

        // "Identity hint" (non-binding): stable reference token + optional fingerprint in meta.
        String refToken = "runs/latest/run.log";
        Artifact runLog1 = new Artifact(id("art"), "RUN_LOG", refToken, trace1);
        Artifact runLog2 = new Artifact(id("art"), "RUN_LOG", refToken, trace2);

        Interpretation bridge = new Interpretation(
                id("int"),
                "bridge",
                "bridge: these artifacts refer to the same run log identity hint",
                0.8,
                bridgeTrace
        );

        store.putEpisode(ep1);
        store.putEpisode(ep2);
        store.putArtifact(runLog1);
        store.putArtifact(runLog2);
        store.putInterpretation(bridge);

        // Within each trace: connect episode to its artifact (no semantics beyond linkage).
        store.putRelation(rel("HAS_ARTIFACT", trace1, ElementRef.episode(ep1.id()), ElementRef.artifact(runLog1.id())));
        store.putRelation(rel("HAS_ARTIFACT", trace2, ElementRef.episode(ep2.id()), ElementRef.artifact(runLog2.id())));

        // Cross-trace stitch: similarity without enforcing identity.
        store.putRelation(Relation.of(
                id("rel"),
                "SIMILAR_TO",
                stitchTrace,
                List.of(ElementRef.artifact(runLog1.id()), ElementRef.artifact(runLog2.id())),
                Map.of(
                        "strength", 0.6,
                        "reason", "same reference token",
                        "fingerprint", refToken
                )
        ));

        // add SAME_AS (competing, “stronger”)
        store.putRelation(Relation.of(
                id("rel"),
                "SAME_AS",
                stitchTrace2,
                List.of(ElementRef.artifact(runLog1.id()), ElementRef.artifact(runLog2.id())),
                Map.of("strength", 0.9, "fingerprint", refToken)
        ));

        // Connect interpretation to each artifact (no semantics beyond linkage)
        store.putRelation(Relation.of(
                id("rel"),
                "ABOUT",
                bridgeTrace,
                List.of(ElementRef.interpretation(bridge.id()), ElementRef.artifact(runLog1.id())),
                Map.of("role", "lhs")
        ));
        store.putRelation(Relation.of(
                id("rel"),
                "ABOUT",
                bridgeTrace,
                List.of(ElementRef.interpretation(bridge.id()), ElementRef.artifact(runLog2.id())),
                Map.of("role", "rhs")
        ));

        GraphQuery q = store.query();

        ElementRef a = ElementRef.artifact(runLog1.id());
        ElementRef b = ElementRef.artifact(runLog2.id());

        var sameFirst = q.explainPathBetween(a, b, 4, preferType("SAME_AS"));
        var similarFirst = q.explainPathBetween(a, b, 4, preferType("SIMILAR_TO"));
        var aboutFirst = q.explainPathBetweenExcludingTypes(
                a, b, 6,
                java.util.Set.of("SAME_AS", "SIMILAR_TO"),
                preferType("ABOUT")
        );


        System.out.println("Trace 1: " + trace1);
        System.out.println("Trace 2: " + trace2);
        System.out.println("Stitch trace: " + stitchTrace);
        System.out.println("Stitch trace2: " + stitchTrace2);
        System.out.println("Bridge trace: " + bridgeTrace);
        System.out.println();

        System.out.println("Explain path preferring SAME_AS:");
        printExplanation(sameFirst);

        System.out.println();
        System.out.println("Explain path preferring SIMILAR_TO:");
        printExplanation(similarFirst);

        System.out.println();
        System.out.println("Explain path preferring ABOUT (indirect meaning trail):");
        printExplanation(aboutFirst);

        System.out.println();
        System.out.println("Direct connective tissue (relationsBetween):");
        q.relationsBetween(a, b).forEach(r ->
                System.out.println("  - " + r.relationType() + " " + r.links() + " meta=" + r.meta())
        );
    }

    private static Relation rel(String type, String trace, ElementRef a, ElementRef b) {
        return Relation.of(id("rel"), type, trace, List.of(a, b));
    }

    private static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static void printExplanation(PathExplanation explanation) {
        if (explanation.isEmpty()) {
            System.out.println("  (no path found)");
            return;
        }

        var nodes = explanation.nodes();
        var hops = explanation.hops();

        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);
            System.out.print("  " + node.kind() + ":" + node.id());
            if (i < hops.size()) {
                var hop = hops.get(i);
                System.out.print(" --" + hop.relationType() + "@" + hop.id()
                        + " trace=" + hop.trace()
                        + " meta=" + hop.meta()
                        + "--> ");
            }
        }
        System.out.println();
    }

    private static Comparator<String> preferType(String preferred) {
        return Comparator.<String>comparingInt(t -> t.equals(preferred) ? 0 : 1)
                .thenComparing(Comparator.naturalOrder());
    }
}
