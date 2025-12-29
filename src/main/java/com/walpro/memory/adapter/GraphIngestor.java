package com.walpro.memory.adapter;

import com.walpro.memory.graph.GraphStore;

/**
 * External systems can implement ingestion using their own domain models,
 * while this project only provides the store.
 */
public interface GraphIngestor<T> {
    void ingest(T input, GraphStore store, String trace);
}
