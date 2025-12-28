package com.walpro.memory.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Relation(
        String id,
        String relationType,
        String trace,
        List<ElementRef> links,
        Map<String, Object> meta
) {
    public Relation {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must be non-blank");
        if (relationType == null || relationType.isBlank()) throw new IllegalArgumentException("relationType must be non-blank");
        if (trace == null || trace.isBlank()) throw new IllegalArgumentException("trace must be non-blank");

        if (links == null || links.size() < 2) {
            throw new IllegalArgumentException("links must contain at least two element refs");
        }
        if (links.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("links must not contain nulls");
        }

        // meta is optional, but normalize it so callers never deal with null.
        meta = (meta == null) ? Map.of() : Map.copyOf(meta);

        // lightweight sanity: no null keys/values; keys non-blank
        for (var e : meta.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                throw new IllegalArgumentException("meta keys must be non-blank");
            }
            if (e.getValue() == null) {
                throw new IllegalArgumentException("meta values must not be null (omit the key instead)");
            }
        }
    }

    public static Relation of(String id, String relationType, String trace, List<ElementRef> links) {
        return new Relation(id, relationType, trace, links, Map.of());
    }

    public static Relation of(String id, String relationType, String trace, List<ElementRef> links, Map<String, Object> meta) {
        return new Relation(id, relationType, trace, links, meta);
    }
}
