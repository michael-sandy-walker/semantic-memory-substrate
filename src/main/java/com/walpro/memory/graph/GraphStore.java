package com.walpro.memory.graph;

import com.walpro.memory.model.Artifact;
import com.walpro.memory.model.Episode;
import com.walpro.memory.model.Interpretation;
import com.walpro.memory.model.Relation;

import java.util.List;
import java.util.Optional;

public interface GraphStore {

    void putEpisode(Episode episode);
    void putArtifact(Artifact artifact);
    void putInterpretation(Interpretation interpretation);
    void putRelation(Relation relation);

    Optional<Episode> getEpisode(String id);
    Optional<Artifact> getArtifact(String id);
    Optional<Interpretation> getInterpretation(String id);
    Optional<Relation> getRelation(String id);

    GraphQuery query();

    List<Interpretation> getAllInterpretations();
}
