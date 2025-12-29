package com.walpro.memory.graph.neo4j;

import java.util.Map;

/**
 * Phase 4: keep it simple.
 * If you want richer meta querying later, switch to typed properties or a separate Meta node model.
 */
public final class Neo4jMetaCodec {

    public String encode(Map<String, Object> meta) {
        if (meta == null || meta.isEmpty()) return "{}";
        // Minimal JSON encoding without extra deps:
        // - strings, numbers, booleans
        // - fallback to String.valueOf for unknowns
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : meta.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":");
            sb.append(valueToJson(e.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    public Map<String, Object> decode(String metaJson) {
        // Keep it opaque; Relation normalizes meta via Map.copyOf(...)
        String v = (metaJson == null || metaJson.isBlank()) ? "{}" : metaJson;
        return Map.of("metaJson", v);
    }

    private String valueToJson(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return "\"" + escape(String.valueOf(v)) + "\"";
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
