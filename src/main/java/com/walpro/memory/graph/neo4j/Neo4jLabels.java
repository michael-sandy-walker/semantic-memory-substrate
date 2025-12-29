package com.walpro.memory.graph.neo4j;

import com.walpro.memory.model.ElementKind;

final class Neo4jLabels {
    private Neo4jLabels() {}

    static String labelFor(ElementKind kind) {
        return switch (kind) {
            case EPISODE -> "Episode";
            case ARTIFACT -> "Artifact";
            case INTERPRETATION -> "Interpretation";
            case RELATION -> "Relation";
            case ACTION -> "Action";
        };
    }
}
