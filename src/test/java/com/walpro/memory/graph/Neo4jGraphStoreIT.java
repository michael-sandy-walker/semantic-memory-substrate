package com.walpro.memory.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.walpro.memory.graph.neo4j.Neo4jGraphStore;
import com.walpro.memory.model.ElementRef;
import com.walpro.memory.model.Episode;
import com.walpro.memory.model.Relation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class Neo4jGraphStoreIT {

    @Container
    static final Neo4jContainer<?> neo4j =
            new Neo4jContainer<>("neo4j:5")
                    .withoutAuthentication(); // simplest for tests

    @Test
    void roundtrip_relation_and_path_explanation_steps_through_relation_node() {
        try (Driver driver = GraphDatabase.driver(neo4j.getBoltUrl(), AuthTokens.none())) {
            Neo4jGraphStore store = new Neo4jGraphStore(driver);
            var query = store.query();

            String trace = "it-" + UUID.randomUUID();

            Episode a = new Episode("ep-a", Instant.now(), Instant.now(), trace);
            Episode b = new Episode("ep-b", Instant.now(), Instant.now(), trace);

            store.putEpisode(a);
            store.putEpisode(b);

            Relation r = new Relation(
                    "rel-stitch",
                    "SAME_AS",
                    trace,
                    List.of(ElementRef.episode(a.id()), ElementRef.episode(b.id())),
                    Map.of("reason", "test")
            );

            store.putRelation(r);

            // Basic readback
            var relBack = store.getRelation(r.id()).orElseThrow();
            assertThat(relBack.relationType()).isEqualTo("SAME_AS");
            assertThat(relBack.links()).containsExactlyInAnyOrderElementsOf(r.links());

            // Path should exist and should include stepping into the relation-node.
            var expl = query.explainPathBetween(ElementRef.episode(a.id()), ElementRef.episode(b.id()), 4);
            assertThat(expl.nodes()).isNotEmpty();

            // Because BFS uses relation-node stepping, we expect the relation node to appear in the node list.
            assertThat(expl.nodes())
                    .contains(ElementRef.relation(r.id()));

            // Hops should reference the relation at least once (often twice with relation-node stepping).
            assertThat(expl.hops()).isNotEmpty();
            assertThat(expl.hops().stream().allMatch(h -> h.id().equals(r.id()))).isTrue();
        }
    }
}
