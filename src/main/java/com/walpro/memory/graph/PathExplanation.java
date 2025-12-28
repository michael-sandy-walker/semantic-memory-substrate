package com.walpro.memory.graph;

import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Relation;

import java.util.List;

/**
 * An explanation chain:
 * node0 --rel0--> node1 --rel1--> node2 ...
 */
public record PathExplanation(
        List<ElementRef> nodes,
        List<Relation> hops
) {
    public boolean isEmpty() {
        return nodes == null || nodes.isEmpty();
    }
}
