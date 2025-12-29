package com.walpro.memory.model;

import java.util.Objects;

public record ElementRef(ElementKind kind, String id) {

    public ElementRef {
        Objects.requireNonNull(kind, "kind");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must be non-blank");
        }
    }

    public static ElementRef episode(String id) { return new ElementRef(ElementKind.EPISODE, id); }
    public static ElementRef artifact(String id) { return new ElementRef(ElementKind.ARTIFACT, id); }
    public static ElementRef interpretation(String id) { return new ElementRef(ElementKind.INTERPRETATION, id); }
    public static ElementRef relation(String id) { return new ElementRef(ElementKind.RELATION, id); }
    public static ElementRef action(String id) { return new ElementRef(ElementKind.ACTION, id); }
}
