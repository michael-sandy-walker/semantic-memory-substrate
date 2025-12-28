package com.walpro.memory.graph;

import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Relation;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public interface GraphQuery {

    /** All relations that mention the given element. */
    List<Relation> relationsOf(ElementRef ref);

    /**
     * Returns adjacent nodes (ElementRefs) reachable via any relation.
     *
     * Note: this is a projection that intentionally discards WHICH relation produced the neighbor.
     * Use relationsOf(...) / relationsBetween(...) / explainPathBetween(...) if you need an explanation chain.
     */
    List<ElementRef> neighborsOf(ElementRef ref);

    /** All element refs that share a trace. */
    List<ElementRef> byTrace(String trace);

    /** Relations that mention both a and b (direct connective tissue). */
    List<Relation> relationsBetween(ElementRef a, ElementRef b);

    /** Element refs reachable within <= hops steps (includes start ref). */
    List<ElementRef> neighborhood(ElementRef start, int hops);

    /** Relations that mention the given element of a specific relation type */
    List<Relation> relationsOfType(ElementRef ref, String relationType);

    /**
     * Bounded BFS path query.
     *
     * Returns a list of element refs from {@code a} to {@code b} (inclusive), or empty if no path was found
     * within the given hop limit.
     */
    List<ElementRef> pathBetween(ElementRef a, ElementRef b, int maxHops);

    /**
     * Like pathBetween, but returns the concrete relation instances used for each hop.
     */
    PathExplanation explainPathBetween(ElementRef a, ElementRef b, int maxHops);

    /**
     * Same as explainPathBetween, but allows ordering which relations are explored first.
     * Still bounded BFS, still first-found semantics.
     */
    PathExplanation explainPathBetween(
            ElementRef a,
            ElementRef b,
            int maxHops,
            Comparator<String> relationTypePreference
    );

    default PathExplanation explainPathBetweenExcludingTypes(
            ElementRef a,
            ElementRef b,
            int maxHops,
            Set<String> excludedRelationTypes
    ) {
        return explainPathBetweenExcludingTypes(a, b, maxHops, excludedRelationTypes, Comparator.naturalOrder());
    }

    /** Like explainPathBetween, but ignores any relation whose type is in excludedRelationTypes.
     * This is useful to ask for an indirect explanation even if a direct stitch exists.
     */
    PathExplanation explainPathBetweenExcludingTypes(
            ElementRef a,
            ElementRef b,
            int maxHops,
            Set<String> excludedRelationTypes,
            Comparator<String> relationTypePreference
    );
}
