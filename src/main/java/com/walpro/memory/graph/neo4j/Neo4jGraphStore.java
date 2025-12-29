package com.walpro.memory.graph.neo4j;

import com.walpro.memory.graph.GraphQuery;
import com.walpro.memory.graph.GraphStore;
import com.walpro.memory.model.Action;
import com.walpro.memory.model.Artifact;
import com.walpro.memory.model.Episode;
import com.walpro.memory.model.Interpretation;
import com.walpro.memory.model.Relation;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;

import java.util.List;
import java.util.Optional;

import static org.neo4j.driver.Values.parameters;

public final class Neo4jGraphStore implements GraphStore {

    private final Driver driver;
    private final Neo4jMetaCodec metaCodec;

    public Neo4jGraphStore(Driver driver) {
        this(driver, new Neo4jMetaCodec());
    }

    public Neo4jGraphStore(Driver driver, Neo4jMetaCodec metaCodec) {
        this.driver = driver;
        this.metaCodec = metaCodec;
    }

    @Override
    public void putEpisode(Episode episode) {
        try (Session s = driver.session()) {
            s.executeWrite(tx -> {
                tx.run(
                        """
                        MERGE (e:Episode {id: $id})
                        SET e.trace = $trace,
                            e.startedAt = $startedAt,
                            e.endedAt = $endedAt
                        """,
                        parameters(
                                "id", episode.id(),
                                "trace", episode.trace(),
                                "startedAt", episode.startedAt().toString(),
                                "endedAt", episode.endedAt().toString()
                        )
                );
                return null;
            });
        }
    }

    @Override
    public void putArtifact(Artifact artifact) {
        try (Session s = driver.session()) {
            s.executeWrite(tx -> {
                tx.run(
                        """
                        MERGE (a:Artifact {id: $id})
                        SET a.trace = $trace,
                            a.kind = $kind,
                            a.reference = $reference
                        """,
                        parameters(
                                "id", artifact.id(),
                                "trace", artifact.trace(),
                                "kind", artifact.kind(),
                                "reference", artifact.reference()
                        )
                );
                return null;
            });
        }
    }

    @Override
    public void putInterpretation(Interpretation interpretation) {
        try (Session s = driver.session()) {
            s.executeWrite(tx -> {
                tx.run(
                        """
                        MERGE (i:Interpretation {id: $id})
                        SET i.trace = $trace,
                            i.type = $type,
                            i.summary = $summary,
                            i.confidence = $confidence
                        """,
                        parameters(
                                "id", interpretation.id(),
                                "trace", interpretation.trace(),
                                "type", interpretation.type(),
                                "summary", interpretation.summary(),
                                "confidence", interpretation.confidence()
                        )
                );
                return null;
            });
        }
    }

    @Override
    public void putRelation(Relation relation) {
        String metaJson = metaCodec.encode(relation.meta());

        try (Session s = driver.session()) {
            s.executeWrite(tx -> {
                tx.run(
                        """
                        MERGE (r:Relation {id: $id})
                        SET r.trace = $trace,
                            r.relationType = $relationType,
                            r.metaJson = $metaJson
                        """,
                        parameters(
                                "id", relation.id(),
                                "trace", relation.trace(),
                                "relationType", relation.relationType(),
                                "metaJson", metaJson
                        )
                );

                // Clear and re-create LINKS_TO deterministically (rare updates; keeps it simple).
                tx.run(
                        """
                        MATCH (r:Relation {id: $id})-[l:LINKS_TO]->()
                        DELETE l
                        """,
                        parameters("id", relation.id())
                );

                for (var link : relation.links()) {
                    String label = Neo4jLabels.labelFor(link.kind());
                    tx.run(
                            """
                            MATCH (r:Relation {id: $rid})
                            MATCH (t:%s {id: $tid})
                            MERGE (r)-[:LINKS_TO]->(t)
                            """.formatted(label),
                            parameters("rid", relation.id(), "tid", link.id())
                    );
                }

                return null;
            });
        }
    }

    @Override
    public void putAction(Action action) {
        String metaJson = metaCodec.encode(action.getMeta());

        try (Session s = driver.session()) {
            s.executeWrite(tx -> {
                tx.run(
                        """
                        MERGE (a:Action {id: $id})
                        SET a.trace = $trace,
                            a.role = $role,
                            a.kind = $kind,
                            a.reason = $reason,
                            a.at = $at,
                            a.metaJson = $metaJson
                        """,
                        parameters(
                                "id", action.getId(),
                                "trace", action.getTrace(),
                                "role", action.getRole(),
                                "kind", action.getKind(),
                                "reason", action.getReason(),
                                "at", action.getAt().toString(),
                                "metaJson", metaJson
                        )
                );
                return null;
            });
        }
    }

    @Override
    public Optional<Episode> getEpisode(String id) {
        try (Session s = driver.session()) {
            return s.executeRead(tx -> tx.run(
                            "MATCH (e:Episode {id: $id}) RETURN e",
                            parameters("id", id)
                    ).stream().findFirst()
            ).map(r -> {
                var n = r.get("e").asNode();
                return new Episode(
                        n.get("id").asString(),
                        java.time.Instant.parse(n.get("startedAt").asString()),
                        java.time.Instant.parse(n.get("endedAt").asString()),
                        n.get("trace").asString()
                );
            });
        }
    }

    @Override
    public Optional<Artifact> getArtifact(String id) {
        try (Session s = driver.session()) {
            return s.executeRead(tx -> tx.run(
                            "MATCH (a:Artifact {id: $id}) RETURN a",
                            parameters("id", id)
                    ).stream().findFirst()
            ).map(r -> {
                var n = r.get("a").asNode();
                return new Artifact(
                        n.get("id").asString(),
                        n.get("kind").asString(),
                        n.get("reference").asString(),
                        n.get("trace").asString()
                );
            });
        }
    }

    @Override
    public Optional<Interpretation> getInterpretation(String id) {
        try (Session s = driver.session()) {
            return s.executeRead(tx -> tx.run(
                            "MATCH (i:Interpretation {id: $id}) RETURN i",
                            parameters("id", id)
                    ).stream().findFirst()
            ).map(r -> {
                var n = r.get("i").asNode();
                return new Interpretation(
                        n.get("id").asString(),
                        n.get("type").asString(),
                        n.get("summary").asString(),
                        n.get("confidence").asDouble(),
                        n.get("trace").asString()
                );
            });
        }
    }

    @Override
    public Optional<Relation> getRelation(String id) {
        try (Session s = driver.session()) {
            return s.executeRead(tx -> tx.run(
                            """
                            MATCH (r:Relation {id: $id})
                            OPTIONAL MATCH (r)-[:LINKS_TO]->(t)
                            RETURN r, collect(t) as targets
                            """,
                            parameters("id", id)
                    ).stream().findFirst()
            ).map(rec -> Neo4jMappers.toRelation(rec, metaCodec));
        }
    }

    @Override
    public Optional<Action> getAction(String id) {
        try (Session s = driver.session()) {
            return s.executeRead(tx -> tx.run(
                            "MATCH (a:Action {id: $id}) RETURN a",
                            parameters("id", id)
                    ).stream().findFirst()
            ).map(r -> {
                var n = r.get("a").asNode();
                return new Action(
                        n.get("id").asString(),
                        n.get("role").asString(),
                        n.get("kind").asString(),
                        n.get("reason").asString(),
                        java.time.Instant.parse(n.get("at").asString()),
                        metaCodec.decode(n.get("metaJson").asString()),
                        n.get("trace").asString()
                );
            });
        }
    }

    @Override
    public GraphQuery query() {
        return new Neo4jGraphQuery(driver, metaCodec);
    }

    @Override
    public List<Interpretation> getAllInterpretations() {
        try (Session s = driver.session()) {
            return s.executeRead(tx -> tx.run(
                    "MATCH (i:Interpretation) RETURN i"
            ).list()).stream().map(rec -> {
                var n = rec.get("i").asNode();
                return new Interpretation(
                        n.get("id").asString(),
                        n.get("type").asString(),
                        n.get("summary").asString(),
                        n.get("confidence").asDouble(),
                        n.get("trace").asString()
                );
            }).toList();
        }
    }
}
