package com.walpro.memory.example;

import com.walpro.memory.graph.GraphStore;
import com.walpro.memory.graph.InMemoryGraphStore;
import com.walpro.memory.model.Artifact;
import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Episode;
import com.walpro.memory.model.Interpretation;
import com.walpro.memory.model.Relation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleGraphTest {

    @Test
    void exampleGraph_isNavigable_andNotATree() {
        GraphStore store = new InMemoryGraphStore();

        // Shared trace group (one “run” / one “context envelope”)
        String trace = "demo-2025-12-28T16:00Z";

        // Two episodes
        Episode ep1 = new Episode(
                "ep-1",
                Instant.parse("2025-12-28T16:00:00Z"),
                Instant.parse("2025-12-28T16:02:00Z"),
                trace
        );
        Episode ep2 = new Episode(
                "ep-2",
                Instant.parse("2025-12-28T16:03:00Z"),
                Instant.parse("2025-12-28T16:05:00Z"),
                trace
        );

        // One shared artifact referenced by both episodes
        Artifact diff = new Artifact(
                "ar-1",
                "diff",
                "mutations/42/ProjectDiff.json",
                trace
        );

        // Two interpretations that conflict, both referencing the same artifact
        Interpretation i1 = new Interpretation(
                "in-1",
                "goal_progress",
                "Progress likely: changes align with goal intent.",
                0.72,
                trace
        );
        Interpretation i2 = new Interpretation(
                "in-2",
                "goal_progress",
                "No progress: changes are cosmetic or unrelated.",
                0.68,
                trace
        );

        store.putEpisode(ep1);
        store.putEpisode(ep2);
        store.putArtifact(diff);
        store.putInterpretation(i1);
        store.putInterpretation(i2);

        // Relations are n-ary nodes. We link by meaning, not by fixed edge schema.

        // ep1 -> diff
        store.putRelation(new Relation(
                "rel-1",
                "produced",
                trace,
                List.of(
                        ElementRef.episode(ep1.id()),
                        ElementRef.artifact(diff.id())
                ),
                Map.of()
        ));

        // ep2 -> diff (shared artifact makes it NOT a tree)
        store.putRelation(new Relation(
                "rel-2",
                "reused",
                trace,
                List.of(
                        ElementRef.episode(ep2.id()),
                        ElementRef.artifact(diff.id())
                ),
                Map.of()
        ));

        // diff -> interpretations (conflicting)
        store.putRelation(new Relation(
                "rel-3",
                "supports",
                trace,
                List.of(
                        ElementRef.artifact(diff.id()),
                        ElementRef.interpretation(i1.id()),
                        ElementRef.interpretation(i2.id())
                ),
                Map.of()
        ));

        // --- Proofs (navigation) ---

        // 1) Shared artifact neighbors both episodes (non-tree).
        var ep1Neighbors = store.query().neighborsOf(ElementRef.episode(ep1.id()));
        var ep2Neighbors = store.query().neighborsOf(ElementRef.episode(ep2.id()));

        assertThat(ep1Neighbors).contains(ElementRef.artifact(diff.id()));
        assertThat(ep2Neighbors).contains(ElementRef.artifact(diff.id()));

        // 2) Artifact neighbors include both episodes and both interpretations
        var diffNeighbors = store.query().neighborsOf(ElementRef.artifact(diff.id()));
        assertThat(diffNeighbors).contains(
                ElementRef.episode(ep1.id()),
                ElementRef.episode(ep2.id()),
                ElementRef.interpretation(i1.id()),
                ElementRef.interpretation(i2.id())
        );

        // 3) Trace returns a mixed bag of element refs (episodes + artifact + interpretations + relations)
        var byTrace = store.query().byTrace(trace);
        assertThat(byTrace).contains(
                ElementRef.episode(ep1.id()),
                ElementRef.episode(ep2.id()),
                ElementRef.artifact(diff.id()),
                ElementRef.interpretation(i1.id()),
                ElementRef.interpretation(i2.id()),
                ElementRef.relation("rel-1"),
                ElementRef.relation("rel-2"),
                ElementRef.relation("rel-3")
        );
    }
}
