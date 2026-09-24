package com.walpro.memory.graph;

import com.walpro.memory.model.ElementKind;
import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Interpretation;
import com.walpro.memory.model.Relation;
import com.walpro.memory.model.RelationTypes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class GraphValidation {

    private GraphValidation() {}

    /** Returns problems (empty list means OK). */
    public static List<String> validateRelationTargets(GraphStore store, Relation relation) {
        List<String> problems = new ArrayList<>();
        for (ElementRef ref : relation.links()) {
            boolean exists = switch (ref.kind()) {
                case EPISODE -> store.getEpisode(ref.id()).isPresent();
                case ARTIFACT -> store.getArtifact(ref.id()).isPresent();
                case INTERPRETATION -> store.getInterpretation(ref.id()).isPresent();
                case RELATION -> store.getRelation(ref.id()).isPresent();
                case ACTION -> store.getAction(ref.id()).isPresent();
            };
            if (!exists) {
                problems.add("Relation " + relation.id() + " links missing target: " + ref.kind() + ":" + ref.id());
            }
        }
        return problems;
    }

    /** Phase 3 semantics: ABOUT / SELECTS / SUPERSEDES (plus baseline arity). */
    public static List<String> validateRelationSemantics(GraphStore store, Relation relation) {
        List<String> problems = new ArrayList<>();

        if (relation.links() == null || relation.links().size() < 2) {
            problems.add("Relation " + relation.id() + " must link at least two elements.");
            return problems;
        }

        Map<ElementKind, Integer> counts = countKinds(relation);

        String type = relation.relationType();
        if (type == null || type.isBlank()) {
            problems.add("Relation " + relation.id() + " relationType must be non-blank.");
            return problems;
        }

        switch (type) {
            case RelationTypes.ABOUT -> {
                // ABOUT must connect at least one interpretation to at least one concrete target.
                if (counts.getOrDefault(ElementKind.INTERPRETATION, 0) < 1) {
                    problems.add("ABOUT relation " + relation.id() + " must include at least 1 INTERPRETATION.");
                }
                int concreteTargets = counts.getOrDefault(ElementKind.ARTIFACT, 0)
                        + counts.getOrDefault(ElementKind.EPISODE, 0);
                if (concreteTargets < 1) {
                    problems.add("ABOUT relation " + relation.id() + " must include at least 1 ARTIFACT or EPISODE.");
                }
            }
            case RelationTypes.SELECTS -> {
                // SELECTS points to a single chosen interpretation from a selection context.
                int iCount = counts.getOrDefault(ElementKind.INTERPRETATION, 0);
                if (iCount != 1) {
                    problems.add("SELECTS relation " + relation.id() + " must include exactly 1 INTERPRETATION (found " + iCount + ").");
                }
                if (counts.getOrDefault(ElementKind.EPISODE, 0) < 1) {
                    problems.add("SELECTS relation " + relation.id() + " must include at least 1 EPISODE (selection context).");
                }
                // Require at least one non-interpretation target besides the episode context.
                int targets = counts.getOrDefault(ElementKind.ARTIFACT, 0) + counts.getOrDefault(ElementKind.EPISODE, 0);
                if (targets < 2) {
                    problems.add("SELECTS relation " + relation.id() + " should include context EPISODE + at least one target (ARTIFACT/EPISODE).");
                }

                // NEW: SELECTS must be backed by an ABOUT relation on the chosen interpretation
                // that mentions all non-episode targets (artifacts/episodes) of the SELECTS.
                ElementRef chosen = relation.links().stream()
                        .filter(r -> r.kind() == ElementKind.INTERPRETATION)
                        .findFirst()
                        .orElse(null);

                if (chosen != null) {

                    // Targets to be covered by ABOUT = all non-INTERPRETATION, excluding EPISODE context
                    // (i.e., typically the selected artifacts)
                    var requiredTargets = relation.links().stream()
                            .filter(r -> r.kind() != ElementKind.INTERPRETATION)
                            .filter(r -> r.kind() != ElementKind.EPISODE)
                            .distinct()
                            .toList();

                    // If there are no concrete targets, this rule is not meaningful (but earlier checks should prevent that).
                    if (!requiredTargets.isEmpty()) {
                        var abouts = store.query().relationsOf(chosen).stream()
                                .filter(r -> RelationTypes.ABOUT.equals(r.relationType()))
                                .toList();

                        boolean hasBackingAbout = abouts.stream().anyMatch(about -> about.links().containsAll(requiredTargets));

                        if (!hasBackingAbout) {
                            problems.add(
                                    "SELECTS relation " + relation.id()
                                            + " chooses " + chosen.id()
                                            + " but no ABOUT relation of that interpretation mentions all targets: "
                                            + requiredTargets
                            );
                        }
                    }
                }
            }
//            case "SUPERSEDES" -> {
//                // SUPERSEDES links two relation-nodes (new supersedes old).
//                int rCount = counts.getOrDefault(ElementKind.RELATION, 0);
//                if (relation.links().size() != 2 || rCount != 2) {
//                    problems.add("SUPERSEDES relation " + relation.id() + " must link exactly 2 RELATION refs.");
//                } else {
//                    ElementRef a = relation.links().get(0);
//                    ElementRef b = relation.links().get(1);
//                    if (a.id().equals(b.id())) {
//                        problems.add("SUPERSEDES relation " + relation.id() + " must not link a relation to itself.");
//                    }
//                    // Optional extra check: both relation refs exist (targets validation will already do this if enabled).
//                    if (store.getRelation(a.id()).isEmpty() || store.getRelation(b.id()).isEmpty()) {
//                        problems.add("SUPERSEDES relation " + relation.id() + " references unknown RELATION(s).");
//                    }
//                }
//            }
            case RelationTypes.SUPERSEDES -> {
                int rCount = counts.getOrDefault(ElementKind.RELATION, 0);
                if (relation.links().size() != 2 || rCount != 2) {
                    problems.add("SUPERSEDES relation " + relation.id() + " must link exactly 2 RELATION refs.");
                    break;
                }

                ElementRef newerRef = relation.links().get(0);
                ElementRef olderRef = relation.links().get(1);

                if (newerRef.id().equals(olderRef.id())) {
                    problems.add("SUPERSEDES relation " + relation.id() + " must not link a relation to itself.");
                    break;
                }

                Relation newer = store.getRelation(newerRef.id()).orElse(null);
                Relation older = store.getRelation(olderRef.id()).orElse(null);

                if (newer == null || older == null) {
                    problems.add("SUPERSEDES relation " + relation.id() + " references unknown RELATION(s).");
                    break;
                }

                if (!RelationTypes.SELECTS.equals(newer.relationType()) || !RelationTypes.SELECTS.equals(older.relationType())) {
                    problems.add("SUPERSEDES relation " + relation.id() + " must link SELECTS -> SELECTS.");
                    break;
                }

                // topic must match (if present on both)
                String tNew = String.valueOf(newer.meta().getOrDefault("topic", ""));
                String tOld = String.valueOf(older.meta().getOrDefault("topic", ""));
                if (!tNew.isBlank() && !tOld.isBlank() && !tNew.equals(tOld)) {
                    problems.add("SUPERSEDES relation " + relation.id() + " links SELECTS with different topics: '" + tNew + "' vs '" + tOld + "'.");
                }

                // targets must match: same artifact set
                Set<String> newTargets = newer.links().stream()
                        .filter(er -> er.kind() == ElementKind.ARTIFACT)
                        .map(ElementRef::id)
                        .collect(Collectors.toSet());

                Set<String> oldTargets = older.links().stream()
                        .filter(er -> er.kind() == ElementKind.ARTIFACT)
                        .map(ElementRef::id)
                        .collect(Collectors.toSet());

                if (!newTargets.equals(oldTargets)) {
                    problems.add("SUPERSEDES relation " + relation.id() + " links SELECTS with different artifact targets: " + newTargets + " vs " + oldTargets + ".");
                }
            }
            default -> {
                // No semantic constraints for other relation types yet.
            }
        }

        return problems;
    }

    /** Convenience: run both target + semantic validation. */
    public static List<String> validateRelation(GraphStore store, Relation relation) {
        List<String> problems = new ArrayList<>();
        problems.addAll(validateRelationTargets(store, relation));
        problems.addAll(validateRelationSemantics(store, relation));
        return problems;
    }

    private static Map<ElementKind, Integer> countKinds(Relation relation) {
        Map<ElementKind, Integer> counts = new EnumMap<>(ElementKind.class);
        for (ElementRef ref : relation.links()) {
            counts.put(ref.kind(), counts.getOrDefault(ref.kind(), 0) + 1);
        }
        return counts;
    }

    /**
     * The interpretation-reachability check over the given interpretations only. A batch can leave
     * unreferenced only the interpretations it wrote itself, so checking those is the whole check
     * for that batch -- and it costs one lookup per interpretation written rather than one per
     * interpretation in the graph, which grew without bound once the host kept history.
     */
    public static List<String> validateReferenced(GraphStore store, java.util.Collection<String> interpretationIds) {
        List<String> problems = new ArrayList<>();
        if (interpretationIds == null || interpretationIds.isEmpty()) return problems;
        GraphQuery q = store.query();
        for (String id : interpretationIds) {
            var rels = q.relationsOf(ElementRef.interpretation(id));
            if (rels == null || rels.isEmpty()) {
                problems.add("Interpretation " + id + " is unreferenced (no relations).");
            }
        }
        return problems;
    }

    public static List<String> validateAll(GraphStore store) {
        List<String> problems = new ArrayList<>();

        GraphQuery q = store.query();

        // Interpretation reachability: must be referenced by at least one relation.
        for (Interpretation i : store.getAllInterpretations()) {
            var rels = q.relationsOf(ElementRef.interpretation(i.id()));
            if (rels == null || rels.isEmpty()) {
                problems.add("Interpretation " + i.id() + " is unreferenced (no relations).");
            }
        }

        return problems;
    }
}
