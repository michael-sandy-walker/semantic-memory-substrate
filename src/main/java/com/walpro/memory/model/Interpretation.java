package com.walpro.memory.model;

public record Interpretation(
        String id,
        String type,
        String summary,
        double confidence,
        String trace
) {
    public Interpretation {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must be non-blank");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type must be non-blank");
        if (summary == null || summary.isBlank()) throw new IllegalArgumentException("summary must be non-blank");
        if (confidence < 0.0 || confidence > 1.0) throw new IllegalArgumentException("confidence must be in [0,1]");
        if (trace == null || trace.isBlank()) throw new IllegalArgumentException("trace must be non-blank");
    }
}
