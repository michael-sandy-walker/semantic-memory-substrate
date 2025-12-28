package com.walpro.memory.model;

import java.time.Instant;
import java.util.Objects;

public record Episode(
        String id,
        Instant startedAt,
        Instant endedAt,
        String trace
) {
    public Episode {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must be non-blank");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(endedAt, "endedAt");
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt must be >= startedAt");
        }
        if (trace == null || trace.isBlank()) throw new IllegalArgumentException("trace must be non-blank");
    }
}
