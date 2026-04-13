package com.example.langchain.service;

import java.util.Map;

public interface EmbeddingService {
    Map<String, Object> testEmbedding(String text);

    Map<String, Object> ingestText(String text, String id, Map<String, String> metadata);

    Map<String, Object> listChromaCollections();

    Map<String, Object> listChromaRecords(String collectionName, Integer limit, Integer offset);
}
