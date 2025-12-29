package com.walpro.memory.demo;

import com.walpro.memory.graph.GraphQuery;
import com.walpro.memory.graph.GraphStore;
import com.walpro.memory.graph.InMemoryGraphStore;
import com.walpro.memory.graph.PathExplanation;
import com.walpro.memory.ingest.GraphWriter;
import com.walpro.memory.model.Artifact;
import com.walpro.memory.model.ElementKind;
import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Episode;
import com.walpro.memory.model.Interpretation;
import com.walpro.memory.model.Relation;
import com.walpro.memory.model.RelationTypes;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phase 3 demo:
 * - two episodes with different traces
 * - two artifacts that look related but aren't asserted as SAME_AS
 * - three interpretations that "argue" across time (hypothesis, counter, refinement)
 * - relations BETWEEN interpretations (CONTRADICTS, REFINES, SUPPORTS)
 * - prove connectivity via "meaning trail" paths (ABOUT + interpretation-relations)
 */
public final class Phase3InterpretationWeaveDemo {

    private Phase3InterpretationWeaveDemo() {
    }

    public static void main(String[] args) {
        InMemoryGraphStore store = new InMemoryGraphStore();
        GraphWriter w = new GraphWriter(store);

        Instant now = Instant.now();
        String traceA = "phase3-epA-" + now;
        String traceB = "phase3-epB-" + now;
        String traceSel = "phase3-select-" + now;
        String traceSel2 = "phase3-select2-" + now;

        String traceHyp = "phase3-hyp-" + now;
        String traceCtr = "phase3-ctr-" + now;
        String traceRef = "phase3-ref-" + now;

        Episode epA = w.episode(traceA, now, now);
        Episode epB = w.episode(traceB, now, now);
        Episode selectionEp = w.episode(traceSel, now, now);
        Episode selectionEp2 = w.episode(traceSel2, now, now);

        // Two artifacts that share a "schema hint" but differ by content hash.
        Artifact a1 = w.artifact(traceA, "RESULT_JSON", "runs/epA/result.json#sha=AAA");
        Artifact a2 = w.artifact(traceB, "RESULT_JSON", "runs/epB/result.json#sha=BBB");

        // Interpretations (the connective tissue)
        Interpretation hyp = w.interpretation(traceHyp, "hypothesis", "Hypothesis: both results are the same dataset version (same schema token).", 0.60);
        Interpretation ctr = w.interpretation(traceCtr, "counter", "Counter: content hashes differ; likely not the same dataset instance.", 0.70);
        Interpretation ref = w.interpretation(traceRef, "refinement", "Refinement: same schema, different payload; treat as comparable but not identical.", 0.80);

        // Episode -> Artifact linkage (just structural)
        w.relation(traceA, RelationTypes.HAS_ARTIFACT, List.of(ElementRef.episode(epA.id()), ElementRef.artifact(a1.id())));
        w.relation(traceB, RelationTypes.HAS_ARTIFACT, List.of(ElementRef.episode(epB.id()), ElementRef.artifact(a2.id())));

        // Interpretation -> Artifact (ABOUT links)
        w.relation(traceHyp, RelationTypes.ABOUT,
                List.of(ElementRef.interpretation(hyp.id()), ElementRef.artifact(a1.id()), ElementRef.artifact(a2.id())),
                Map.of("basis", "schemaToken=RESULT_JSON"));
        w.relation(traceCtr, RelationTypes.ABOUT,
                List.of(ElementRef.interpretation(ctr.id()), ElementRef.artifact(a1.id()), ElementRef.artifact(a2.id())),
                Map.of("basis", "sha differs", "lhsSha", "AAA", "rhsSha", "BBB"));
        w.relation(traceRef, RelationTypes.ABOUT,
                List.of(ElementRef.interpretation(ref.id()), ElementRef.artifact(a1.id()), ElementRef.artifact(a2.id())),
                Map.of("basis", "same kind; comparable fields; payload differs"));

        // Debate relations: Interpretation -> Interpretation (weaving meaning across time)
        w.relation(traceCtr, RelationTypes.CONTRADICTS,
                List.of(ElementRef.interpretation(ctr.id()), ElementRef.interpretation(hyp.id())),
                Map.of("scope", "identity claim", "strength", 0.8));
        w.relation(traceRef, RelationTypes.REFINES,
                List.of(ElementRef.interpretation(ref.id()), ElementRef.interpretation(hyp.id())),
                Map.of("scope", "downgrade identity->comparability", "strength", 0.7));
        w.relation(traceRef, RelationTypes.SUPPORTS,
                List.of(ElementRef.interpretation(ref.id()), ElementRef.interpretation(ctr.id())),
                Map.of("scope", "not identical", "strength", 0.6));

        // SELECTS relation (selection episode chooses the current interpretation for a topic)
        Relation select1 = w.relation(
                traceSel,
                RelationTypes.SELECTS,
                List.of(
                        ElementRef.episode(selectionEp.id()),
                        ElementRef.interpretation(ref.id()),
                        ElementRef.artifact(a1.id()),
                        ElementRef.artifact(a2.id())
                ),
                Map.of("topic", "identity",
                        "reason", "schema matches but payload differs; treat as comparable, not identical",
                        "confidence", 0.80,
                        "selector", "demo")
        );

        // second selection chooses "ctr" (counter) as current, for demonstration
        Relation select2 = w.relation(
                traceSel2,
                RelationTypes.SELECTS,
                List.of(
                        ElementRef.episode(selectionEp2.id()),
                        ElementRef.interpretation(ctr.id()),
                        ElementRef.artifact(a1.id()),
                        ElementRef.artifact(a2.id())
                ),
                Map.of(
                        "topic", "identity",
                        "reason", "hash differs -> not identical (choose counter as current)",
                        "confidence", 0.85,
                        "selector", "demo"
                )
        );

        // SUPERSEDES: select2 supersedes select1 (new, old)
        w.relation(
                traceSel2,
                RelationTypes.SUPERSEDES,
                List.of(
                        ElementRef.relation(select2.id()),
                        ElementRef.relation(select1.id())
                ),
                Map.of("topic", "identity")
        );

        GraphQuery q = store.query();

        ElementRef left = ElementRef.artifact(a1.id());
        ElementRef right = ElementRef.artifact(a2.id());

        System.out.println("Trace A: " + traceA);
        System.out.println("Trace B: " + traceB);
        System.out.println();

        // 1) “Meaning trail” path: prefer ABOUT + interpretation-weave relations
        // (If your query supports excluding types, you can exclude SAME_AS/SIMILAR_TO, but this works even without them.)
        PathExplanation meaningTrail = q.explainPathBetween(left, right, 6, preferTypes(RelationTypes.ABOUT, RelationTypes.REFINES, RelationTypes.CONTRADICTS, RelationTypes.SUPPORTS, RelationTypes.HAS_ARTIFACT));

        System.out.println("Explain path preferring meaning relations (ABOUT / REFINE / CONTRADICT / SUPPORT / HAS_ARTIFACT):");
        printExplanation(meaningTrail);

        System.out.println();
        System.out.println("Direct connective tissue (relationsBetween):");
        q.relationsBetween(left, right).forEach(r ->
                System.out.println("  - " + r.relationType() + " " + r.links() + " meta=" + r.meta())
        );

        System.out.println();
        System.out.println("Interpretations that mention BOTH artifacts (via ABOUT):");

        q.relationsBetween(left, right).stream()
                .filter(r -> RelationTypes.ABOUT.equals(r.relationType()))
                .forEach(r -> {
                    // find the interpretation linked by this ABOUT relation
                    ElementRef interpRef = r.links().stream()
                            .filter(er -> er.kind() == ElementKind.INTERPRETATION)
                            .findFirst()
                            .orElse(null);

                    if (interpRef == null) {
                        System.out.println("  - " + r.id()
                                + " trace=" + r.trace()
                                + " meta=" + r.meta()
                                + " (no interpretation?)");
                        return;
                    }

                    store.getInterpretation(interpRef.id()).ifPresentOrElse(i -> {
                        System.out.println("  - " + interpRef.id()
                                + " [" + i.type() + "] "
                                + i.summary()
                                + " (conf=" + i.confidence() + ")"
                                + " | rel=" + r.id()
                                + " meta=" + r.meta());
                    }, () -> {
                        System.out.println("  - " + interpRef.id()
                                + " (interpretation missing)"
                                + " | rel=" + r.id()
                                + " meta=" + r.meta());
                    });
                });

        System.out.println();
        System.out.println("Explain path preferring interpretation debate (CONTRADICTS / REFINES / SUPPORTS), excluding ABOUT / SELECTS / SUPERSEDES:");

        var debatePref = Comparator.<String>comparingInt(t -> switch (t) {
            case RelationTypes.CONTRADICTS -> 0;
            case RelationTypes.REFINES -> 1;
            case RelationTypes.SUPPORTS -> 2;
            default -> 9;
        });

        PathExplanation debate = store.query().explainPathBetweenExcludingTypes(
                ElementRef.interpretation(ctr.id()),
                ElementRef.interpretation(ref.id()),
                6,
                Set.of(RelationTypes.ABOUT, RelationTypes.SELECTS, RelationTypes.SUPERSEDES),
                debatePref
        );
        printExplanation(debate);

        System.out.println();
        System.out.println("Selection (SELECTS) for these two artifacts:");

        // All SELECTS relations that include BOTH artifacts
        var selectsForPair = q.relationsBetween(left, right).stream()
                .filter(r -> RelationTypes.SELECTS.equals(r.relationType()))
                .toList();

        if (selectsForPair.isEmpty()) {
            System.out.println("  (no selection found)");
        } else {
            // "Current" = highest confidence, then newest-ish via trace tie-break (we don't have timestamps here)
            var current = selectsForPair.stream()
                    .max((r1, r2) -> {
                        double c1 = getDoubleMeta(r1, "confidence").orElse(0.0);
                        double c2 = getDoubleMeta(r2, "confidence").orElse(0.0);
                        int cmp = Double.compare(c1, c2);
                        if (cmp != 0) return cmp;
                        return r1.trace().compareTo(r2.trace());
                    })
                    .orElse(selectsForPair.get(0));

            ElementRef chosen = current.links().stream()
                    .filter(er -> er.kind().name().equals("INTERPRETATION"))
                    .findFirst()
                    .orElse(null);

            System.out.println("  Current:");
            System.out.println("    relation=" + current.id() + " trace=" + current.trace() + " meta=" + current.meta());
            System.out.println("    chosen=" + (chosen == null ? "(none)" : (chosen.kind() + ":" + chosen.id())));
            if (chosen != null) {
                store.getInterpretation(chosen.id()).ifPresent(i ->
                        System.out.println("    chosenSummary=" + i.type() + " | " + i.summary() + " (conf=" + i.confidence() + ")")
                );
            }

            System.out.println("  History:");
            selectsForPair.stream()
                    .sorted((a, b) -> a.trace().compareTo(b.trace()))
                    .forEach(r -> {
                        ElementRef pick = r.links().stream()
                                .filter(er -> er.kind().name().equals("INTERPRETATION"))
                                .findFirst()
                                .orElse(null);
                        System.out.println("    - " + r.id()
                                + " trace=" + r.trace()
                                + " pick=" + (pick == null ? "(none)" : pick.id())
                                + " meta=" + r.meta());
                    });
        }

        System.out.println();
        System.out.println("Selection (SELECTS) for these two artifacts (current by SUPERSEDES):");

        selectsForPair = q.relationsBetween(left, right).stream()
                .filter(r -> RelationTypes.SELECTS.equals(r.relationType()))
                .filter(r -> "identity".equals(String.valueOf(r.meta().getOrDefault("topic", ""))))
                .toList();

        var supersedesRels = store.query().byTrace(traceSel2).stream()
                .filter(ref2 -> ref2.kind() == ElementKind.RELATION)
                .map(ref2 -> store.getRelation(ref2.id()).orElse(null))
                .filter(r -> r != null && RelationTypes.SUPERSEDES.equals(r.relationType()))
                .toList();

        // Collect superseded SELECTS relation ids (the "old" side)
        Set<String> supersededIds = supersedesRels.stream()
                .flatMap(r -> r.links().stream().skip(1).limit(1)) // links[1] = old
                .filter(er -> er.kind() == ElementKind.RELATION)
                .map(ElementRef::id)
                .collect(Collectors.toSet());

        // Current candidates = SELECTS not superseded
        var currentCandidates = selectsForPair.stream()
                .filter(s -> !supersededIds.contains(s.id()))
                .toList();

        if (currentCandidates.isEmpty()) {
            System.out.println("  (no current selection found)");
        } else if (currentCandidates.size() > 1) {
            System.out.println("  (multiple current selections found; expected 1)");
            currentCandidates.forEach(s -> System.out.println("    - " + s.id() + " meta=" + s.meta()));
        } else {
            Relation current = currentCandidates.get(0);

            ElementRef chosen = current.links().stream()
                    .filter(er -> er.kind() == ElementKind.INTERPRETATION)
                    .findFirst()
                    .orElse(null);

            System.out.println("  Current:");
            System.out.println("    relation=" + current.id() + " trace=" + current.trace() + " meta=" + current.meta());
            System.out.println("    chosen=" + (chosen == null ? "(none)" : (chosen.kind() + ":" + chosen.id())));

            System.out.println("  Chain (new -> old):");
            printSelectionChain(store, current);
        }

        w.validateAllOrThrow();
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

            // If we just printed a hop whose id equals this node id (relation-node “enter/exit”),
            // skip printing the relation node itself to avoid the duplicated-looking output.
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

    /**
     * Preference comparator that pulls listed relation types to the front, in the given order.
     * Everything else is sorted naturally after that.
     */
    private static Comparator<String> preferTypes(String... preferredInOrder) {
        List<String> pref = List.of(preferredInOrder);
        return Comparator.<String>comparingInt(t -> {
                    int idx = pref.indexOf(t);
                    return (idx >= 0) ? idx : 999;
                })
                .thenComparing(Comparator.naturalOrder());
    }

    private static Optional<Double> getDoubleMeta(Relation r, String key) {
        Object v = r.meta().get(key);
        if (v instanceof Number n) return Optional.of(n.doubleValue());
        if (v instanceof String s) {
            try { return Optional.of(Double.parseDouble(s)); } catch (Exception ignored) {}
        }
        return Optional.empty();
    }

    public static void printSelectionChain(GraphStore store, Relation current) {
        Relation cur = current;
        while (true) {
            System.out.println("    - SELECTS " + cur.id() + " meta=" + cur.meta());

            // Find SUPERSEDES where links[0] == cur
            Relation finalCur = cur;
            var supers = store.query().relationsOf(ElementRef.relation(cur.id())).stream()
                    .filter(r -> RelationTypes.SUPERSEDES.equals(r.relationType()))
                    .filter(r -> r.links().size() == 2)
                    .filter(r -> r.links().get(0).kind() == ElementKind.RELATION)
                    .filter(r -> r.links().get(0).id().equals(finalCur.id()))
                    .findFirst()
                    .orElse(null);

            if (supers == null) return;

            ElementRef oldRef = supers.links().get(1);
            var old = store.getRelation(oldRef.id()).orElse(null);
            if (old == null) return;

            cur = old;
        }
    }
}
