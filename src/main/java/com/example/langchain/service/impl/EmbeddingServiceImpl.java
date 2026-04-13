package com.example.langchain.service.impl;

import com.example.langchain.config.ChromaV2Client;
import com.example.langchain.service.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final ChromaV2Client chromaV2Client;

    @Value("${app.vector-store.chroma.collection-name:langchain_knowledge}")
    private String collectionName;

    public EmbeddingServiceImpl(EmbeddingModel embeddingModel, ChromaV2Client chromaV2Client) {
        this.embeddingModel = embeddingModel;
        this.chromaV2Client = chromaV2Client;
    }

    @Override
    public Map<String, Object> testEmbedding(String text) {
        String normalized = normalizeText(text);
        Embedding embedding = embeddingModel.embed(normalized).content();
        float[] vector = embedding.vector();

        List<Float> preview = new ArrayList<>();
        int previewSize = Math.min(vector.length, 8);
        for (int i = 0; i < previewSize; i++) {
            preview.add(vector[i]);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("modelName", embeddingModel.modelName());
        result.put("dimension", embedding.dimension());
        result.put("vectorPreview", preview);
        result.put("textLength", normalized.length());
        return result;
    }

    @Override
    public Map<String, Object> ingestText(String text, String id, Map<String, String> metadata) {
        String normalized = normalizeText(text);
        String finalId = (id == null || id.trim().isEmpty()) ? UUID.randomUUID().toString() : id.trim();

        Embedding embedding = embeddingModel.embed(normalized).content();

        Map<String, String> metadataMap = new HashMap<>();
        if (metadata != null) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    metadataMap.put(entry.getKey(), entry.getValue());
                }
            }
        }
        metadataMap.put("source", "embedding-test-ui");

        try {
            String collectionId = chromaV2Client.getOrCreateCollectionId(collectionName);
            chromaV2Client.upsert(collectionId, finalId, embedding.vector(), normalized, metadataMap);
            int count = chromaV2Client.count(collectionId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("collectionName", collectionName);
            result.put("collectionId", collectionId);
            result.put("id", finalId);
            result.put("dimension", embedding.dimension());
            result.put("count", count);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("写入 ChromaDB 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> listChromaCollections() {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("collections", chromaV2Client.collectionsOverview());
            return result;
        } catch (Exception e) {
            throw new RuntimeException("查询 ChromaDB 集合失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> listChromaRecords(String collectionName, Integer limit, Integer offset) {
        try {
            Map<String, Object> response = chromaV2Client.collectionRecords(
                    collectionName,
                    limit == null ? 20 : limit,
                    offset == null ? 0 : offset
            );
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.putAll(response);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("查询 ChromaDB 记录失败: " + e.getMessage(), e);
        }
    }

    private String normalizeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text 不能为空");
        }
        return text.trim();
    }
}
