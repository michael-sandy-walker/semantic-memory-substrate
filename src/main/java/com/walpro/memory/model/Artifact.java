package com.walpro.memory.model;

public record Artifact(
        String id,
        String kind,
        String reference,
        String trace
) {
    public Artifact {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must be non-blank");
        if (kind == null || kind.isBlank()) throw new IllegalArgumentException("kind must be non-blank");
        if (reference == null || reference.isBlank()) throw new IllegalArgumentException("reference must be non-blank");
        if (trace == null || trace.isBlank()) throw new IllegalArgumentException("trace must be non-blank");
    }
}
