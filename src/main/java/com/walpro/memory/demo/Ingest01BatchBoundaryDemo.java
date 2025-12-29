package com.walpro.memory.demo;

import com.walpro.memory.graph.InMemoryGraphStore;
import com.walpro.memory.graph.PathExplanation;
import com.walpro.memory.ingest.GraphWriter;
import com.walpro.memory.model.Artifact;
import com.walpro.memory.model.ElementKind;
import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Episode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class Ingest01BatchBoundaryDemo {

    private Ingest01BatchBoundaryDemo() {
    }

    public static void main(String[] args) {
        InMemoryGraphStore store = new InMemoryGraphStore();
        GraphWriter w = new GraphWriter(store);

        Instant now = Instant.now();

        String traceA = "ingest-batch-A-" + now;
        String traceB = "ingest-batch-B-" + now;

        System.out.println("Batch A trace: " + traceA);
        System.out.println("Batch B trace: " + traceB);
        System.out.println();

        Artifact a1;
        Artifact a2;

        // ---- Batch A ----
        try (GraphWriter.Batch b = w.batch(traceA)) {
            Episode ep = b.episodeNow();
            a1 = b.artifact("RUN_LOG", "runs/latest/run.log");

            b.relation(
                    "HAS_ARTIFACT",
                    List.of(ElementRef.episode(ep.id()), ElementRef.artifact(a1.id()))
            );
        }

        // ---- Batch B ----
        try (GraphWriter.Batch b = w.batch(traceB)) {
            Episode ep = b.episodeNow();
            a2 = b.artifact("RUN_LOG", "runs/latest/run.log");

            b.relation(
                    "HAS_ARTIFACT",
                    List.of(ElementRef.episode(ep.id()), ElementRef.artifact(a2.id()))
            );
        }

        // Explicit stitch (separate act)
        String traceStitch = "ingest-stitch-" + now;
        try (GraphWriter.Batch b = w.batch(traceStitch)) {
            b.relation(
                    "SIMILAR_TO",
                    List.of(ElementRef.artifact(a1.id()), ElementRef.artifact(a2.id())),
                    Map.of("reason", "same reference token", "reference", "runs/latest/run.log")
            );
        }

        System.out.println("Explain path between artifacts (prefer SIMILAR_TO):");
        PathExplanation p = store.query().explainPathBetween(
                ElementRef.artifact(a1.id()),
                ElementRef.artifact(a2.id()),
                6,
                preferTypes("SIMILAR_TO", "SAME_AS", "ABOUT", "HAS_ARTIFACT")
        );
        printExplanation(p);

        System.out.println();
        System.out.println("byTrace(batch A):");
        printByTrace(store, traceA);

        System.out.println();
        System.out.println("byTrace(batch B):");
        printByTrace(store, traceB);

        System.out.println();
        System.out.println("byTrace(stitch):");
        printByTrace(store, traceStitch);
    }

    private static void printByTrace(InMemoryGraphStore store, String traceA) {
        store.query().byTrace(traceA).forEach(ref -> {
            if (ref.kind() == ElementKind.RELATION) {
                store.getRelation(ref.id()).ifPresent(r ->
                        System.out.println("  - RELATION:" + r.id() + " type=" + r.relationType())
                );
            } else {
                System.out.println("  - " + ref.kind() + ":" + ref.id());
            }
        });
    }

    private static java.util.Comparator<String> preferTypes(String... preferredInOrder) {
        List<String> pref = List.of(preferredInOrder);
        return java.util.Comparator.<String>comparingInt(t -> {
                    int idx = pref.indexOf(t);
                    return (idx >= 0) ? idx : 999;
                })
                .thenComparing(java.util.Comparator.naturalOrder());
    }

    private static void printExplanation(PathExplanation explanation) {
        if (explanation.isEmpty()) {
            System.out.println("  (no path found)");
            return;
        }

        var nodes = explanation.nodes();
        var hops = explanation.hops();

        String lastHopId = null;

        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);

            if (node.id().equals(lastHopId)) {
                continue;
            }

            System.out.print("  " + node.kind() + ":" + node.id());

            if (i < hops.size()) {
                var hop = hops.get(i);
                lastHopId = hop.id();
                System.out.print(" --" + hop.relationType() + "@" + hop.id()
                        + " trace=" + hop.trace()
                        + " meta=" + hop.meta()
                        + "--> ");
            }
        }
        System.out.println();
    }
}
