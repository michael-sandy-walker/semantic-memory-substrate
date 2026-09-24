package com.walpro.memory.ingest;

import com.walpro.memory.graph.InMemoryGraphStore;
import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Episode;
import com.walpro.memory.model.Interpretation;
import com.walpro.memory.model.RelationTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A batch's commit checks what the batch wrote, not the whole graph. */
class BatchCommitScopeTest {

    @Test
    void aBatchWhoseInterpretationIsReferencedCommits() {
        GraphWriter writer = new GraphWriter(new InMemoryGraphStore());
        GraphWriter.Batch b = writer.batch("t");
        Episode ep = b.episodeNow();
        Interpretation i = b.interpretation("OBSERVATION", "seen", 0.7);
        b.relation(RelationTypes.ABOUT, List.of(ElementRef.interpretation(i.id()), ElementRef.episode(ep.id())));

        b.commit();
    }

    @Test
    void aBatchThatLeavesItsOwnInterpretationUnreferencedFails() {
        GraphWriter.Batch b = new GraphWriter(new InMemoryGraphStore()).batch("t");
        Interpretation i = b.interpretation("OBSERVATION", "orphan", 0.7);

        assertThatThrownBy(b::commit).isInstanceOf(IllegalStateException.class).hasMessageContaining(i.id());
    }

    /** Another writer's orphan is the whole-graph check's business, and it still finds it. */
    @Test
    void anOrphanWrittenOutsideTheBatchDoesNotFailIt() {
        GraphWriter writer = new GraphWriter(new InMemoryGraphStore());
        writer.interpretation("t0", "OBSERVATION", "older orphan", 0.5);

        writer.batch("t").commit();

        assertThatThrownBy(writer::validateAllOrThrow).isInstanceOf(IllegalStateException.class);
    }
}
