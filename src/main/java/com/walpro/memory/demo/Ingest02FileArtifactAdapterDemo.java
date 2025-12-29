package com.walpro.memory.demo;

import com.walpro.memory.adapter.FileArtifactAdapter;
import com.walpro.memory.graph.InMemoryGraphStore;
import com.walpro.memory.ingest.GraphWriter;
import com.walpro.memory.model.ElementKind;

import java.time.Instant;
import java.util.Map;

public final class Ingest02FileArtifactAdapterDemo {
    public static void main(String[] args) {
        var store = new InMemoryGraphStore();
        var writer = new GraphWriter(store);
        var adapter = new FileArtifactAdapter(writer);

        String trace1 = "ingest-file-1-" + Instant.now();
        String trace2 = "ingest-file-2-" + Instant.now();

        var res = adapter.ingest(new FileArtifactAdapter.FileArtifactIngestRequest(
                trace1,
                "RUN_LOG",
                "runs/latest/run.log",
                null,
                null,
                Map.of("source", "demo"),
                "note",
                "Observed run.log path from local filesystem.",
                0.75
        ));

        System.out.println("Ingested episode=" + res.episode().id() + " artifact=" + res.artifact().id());
        System.out.println("byTrace:");
        store.query().byTrace(trace1).forEach(ref -> {
            if (ref.kind() == ElementKind.RELATION) {
                store.getRelation(ref.id()).ifPresent(r ->
                        System.out.println("  - RELATION:" + r.id() + " type=" + r.relationType())
                );
            } else {
                System.out.println("  - " + ref.kind() + ":" + ref.id());
            }
        });

        res = adapter.ingest(new FileArtifactAdapter.FileArtifactIngestRequest(
                trace2,
                "RUN_LOG",
                "runs/latest/run.log",
                null,
                null,
                Map.of("source", "demo"),
                "note",
                "Observed run.log path from local filesystem.",
                0.75
        ));

        System.out.println("Ingested episode=" + res.episode().id() + " artifact=" + res.artifact().id());
        System.out.println("byTrace:");
        store.query().byTrace(trace2).forEach(ref -> {
            if (ref.kind() == ElementKind.RELATION) {
                store.getRelation(ref.id()).ifPresent(r ->
                        System.out.println("  - RELATION:" + r.id() + " type=" + r.relationType())
                );
            } else {
                System.out.println("  - " + ref.kind() + ":" + ref.id());
            }
        });

        var hits = store.query().findArtifactsByReference("RUN_LOG", "runs/latest/run.log");
        System.out.println("hits=" + hits.size());
        hits.forEach(a -> System.out.println("  - " + a.id() + " trace=" + a.trace()));

    }
}
