package com.walpro.memory.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * An Action is a trace-step inside an Episode.
 * Think: "Planner executed because PLANNING signal was strong".
 */
public final class Action {

    private final String id;
    private final String role;
    private final String kind;
    private final String reason;
    private final Instant at;
    private final Map<String, Object> meta;
    private final String trace;


    public Action(String id, String role, String kind, String reason, Instant at, Map<String, Object> meta, String trace) {
        this.id = Objects.requireNonNull(id, "id");
        this.role = Objects.requireNonNull(role, "role");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.reason = reason == null ? "" : reason;
        this.at = at == null ? Instant.now() : at;
        this.meta = meta == null ? Map.of() : Map.copyOf(meta);
        this.trace = trace == null ? "" : trace;
    }

    public String getId() { return id; }
    public String getRole() { return role; }
    public String getKind() { return kind; }
    public String getReason() { return reason; }
    public Instant getAt() { return at; }
    public Map<String, Object> getMeta() { return meta; }
    public String getTrace() { return trace; }
}
