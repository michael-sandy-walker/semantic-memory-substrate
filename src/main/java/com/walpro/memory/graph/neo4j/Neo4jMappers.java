package com.walpro.memory.graph.neo4j;

import com.walpro.memory.model.ElementKind;
import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Relation;
import org.neo4j.driver.Record;

import java.util.List;

final class Neo4jMappers {
    private Neo4jMappers() {}

    static Relation toRelation(Record rec, Neo4jMetaCodec metaCodec) {
        var r = rec.get("r").asNode();
        var targets = rec.get("targets").asList(v -> v.asNode());

        List<ElementRef> links = targets.stream()
                .map(n -> new ElementRef(kindFromLabels(n.labels()), n.get("id").asString()))
                .toList();

        return new Relation(
                r.get("id").asString(),
                r.get("relationType").asString(),
                r.get("trace").asString(),
                links,
                metaCodec.decode(r.get("metaJson").isNull() ? "{}" : r.get("metaJson").asString())
        );
    }

    static ElementKind kindFromLabels(Iterable<String> labels) {
        for (String l : labels) {
            switch (l) {
                case "Episode": return ElementKind.EPISODE;
                case "Artifact": return ElementKind.ARTIFACT;
                case "Interpretation": return ElementKind.INTERPRETATION;
                case "Relation": return ElementKind.RELATION;
                case "Action": return ElementKind.ACTION;
                default: // ignore
            }
        }
        throw new IllegalArgumentException("Unknown labels: " + labels);
    }
}
