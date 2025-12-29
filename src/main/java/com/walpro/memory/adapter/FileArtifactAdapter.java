package com.walpro.memory.adapter;

import com.walpro.memory.ingest.GraphWriter;
import com.walpro.memory.model.Artifact;
import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Episode;
import com.walpro.memory.model.Interpretation;
import com.walpro.memory.model.Relation;
import com.walpro.memory.model.RelationTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal adapter: translates an external file-like reference into Phase-1 graph structure.
 *
 * Adapter responsibilities:
 * - Accept a domain-shaped input (file reference + optional note)
 * - Write Episode + Artifact + HAS_ARTIFACT, optionally Interpretation + ABOUT
 *
 * Non-responsibilities:
 * - No deduplication
 * - No causality
 * - No "same-as" detection
 */
public final class FileArtifactAdapter {

    private final GraphWriter writer;

    public FileArtifactAdapter(GraphWriter writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    public IngestResult ingest(FileArtifactIngestRequest req) {
        Objects.requireNonNull(req, "req");
        Objects.requireNonNull(req.trace(), "trace");
        Objects.requireNonNull(req.artifactKind(), "artifactKind");
        Objects.requireNonNull(req.reference(), "reference");

        try (GraphWriter.Batch b = writer.batch(req.trace())) {
            Episode ep = (req.startedAt() != null || req.endedAt() != null)
                    ? b.episode(
                    req.startedAt() != null ? req.startedAt() : Instant.now(),
                    req.endedAt() != null ? req.endedAt() : Instant.now()
            )
                    : b.episodeNow();

            Artifact art = b.artifact(req.artifactKind(), req.reference());

            Relation has = b.relation(
                    RelationTypes.HAS_ARTIFACT,
                    List.of(ElementRef.episode(ep.id()), ElementRef.artifact(art.id())),
                    req.relationMeta() == null ? Map.of() : req.relationMeta()
            );

            Interpretation note = null;
            Relation about = null;

            if (req.noteSummary() != null && !req.noteSummary().isBlank()) {
                note = b.interpretation(
                        req.noteType() == null ? "note" : req.noteType(),
                        req.noteSummary(),
                        req.noteConfidence() == null ? 0.70 : req.noteConfidence()
                );

                about = b.relation(
                        RelationTypes.ABOUT,
                        List.of(ElementRef.interpretation(note.id()), ElementRef.artifact(art.id()))
                );
            }

            return new IngestResult(ep, art, has, note, about);
        }
    }

    public record FileArtifactIngestRequest(
            String trace,
            String artifactKind,
            String reference,
            Instant startedAt,
            Instant endedAt,
            Map<String, Object> relationMeta,
            String noteType,
            String noteSummary,
            Double noteConfidence
    ) {
    }

    public record IngestResult(
            Episode episode,
            Artifact artifact,
            Relation hasArtifact,
            Interpretation interpretation,
            Relation about
    ) {
    }
}
