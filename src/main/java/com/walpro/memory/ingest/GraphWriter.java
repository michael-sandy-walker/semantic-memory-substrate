package com.walpro.memory.ingest;

import com.walpro.memory.graph.GraphStore;
import com.walpro.memory.graph.GraphValidation;
import com.walpro.memory.model.Artifact;
import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Episode;
import com.walpro.memory.model.Interpretation;
import com.walpro.memory.model.Relation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Small write facade for creating graph elements.
 *
 * Design intent:
 * - Adapters (domain translators) call this class.
 * - This class only creates/stores Episode/Artifact/Interpretation/Relation.
 * - Semantic enforcement stays in GraphStore.putRelation(...) (InMemoryGraphStore validates relations).
 */
public final class GraphWriter {

    private final GraphStore store;

    public GraphWriter(GraphStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Starts a batch bound to a trace. */
    public Batch batch(String trace) {
        return new Batch(this, trace);
    }

    public Episode episode(String trace, Instant startedAt, Instant endedAt) {
        Episode ep = new Episode(id("ep"), startedAt, endedAt, trace);
        store.putEpisode(ep);
        return ep;
    }

    public Episode episodeNow(String trace) {
        Instant now = Instant.now();
        return episode(trace, now, now);
    }

    public Artifact artifact(String trace, String kind, String reference) {
        Artifact a = new Artifact(id("art"), kind, reference, trace);
        store.putArtifact(a);
        return a;
    }

    public Interpretation interpretation(String trace, String type, String summary, double confidence) {
        Interpretation i = new Interpretation(id("int"), type, summary, confidence, trace);
        store.putInterpretation(i);
        return i;
    }

    public Relation relation(String trace, String relationType, List<ElementRef> links) {
        return relation(trace, relationType, links, Map.of());
    }

    public Relation relation(String trace, String relationType, List<ElementRef> links, Map<String, Object> meta) {
        Relation r = Relation.of(id("rel"), relationType, trace, links, meta);
        store.putRelation(r); // enforces target + semantic validation via GraphValidation
        return r;
    }

    /**
     * Optional whole-graph checks (currently: interpretations must be referenced by at least one relation).
     * Useful at the end of demos/tests or after batch ingestion.
     */
    public void validateAllOrThrow() {
        List<String> problems = GraphValidation.validateAll(store);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join("\n", problems));
        }
    }

    public final class Batch implements AutoCloseable {
        private final String batchId = "batch-" + UUID.randomUUID();

        private final GraphWriter writer;
        private final String trace;
        private boolean committed = false;

        private Batch(GraphWriter writer, String trace) {
            this.writer = Objects.requireNonNull(writer, "writer");
            this.trace = Objects.requireNonNull(trace, "trace");
        }

        public String trace() {
            return trace;
        }

        public Episode episode(Instant startedAt, Instant endedAt) {
            ensureOpen();
            return writer.episode(trace, startedAt, endedAt);
        }

        public Episode episodeNow() {
            ensureOpen();
            return writer.episodeNow(trace);
        }

        public Artifact artifact(String kind, String reference) {
            ensureOpen();
            return writer.artifact(trace, kind, reference);
        }

        public Interpretation interpretation(String type, String summary, double confidence) {
            ensureOpen();
            return writer.interpretation(trace, type, summary, confidence);
        }

        public Relation relation(String relationType, List<ElementRef> links) {
            ensureOpen();
            return writer.relation(trace, relationType, links);
        }

        public Relation relation(String relationType, List<ElementRef> links, Map<String, Object> meta) {
            ensureOpen();
            return writer.relation(trace, relationType, links, meta);
        }

        /** Runs whole-graph checks once (and marks the batch as complete). */
        public void commit() {
            ensureOpen();
            writer.validateAllOrThrow();
            committed = true;
        }

        @Override
        public void close() {
            if (!committed) {
                commit();
            }
        }

        private void ensureOpen() {
            if (committed) {
                throw new IllegalStateException("Batch already committed");
            }
        }

        public String batchId() { return batchId; }
    }

    private static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
