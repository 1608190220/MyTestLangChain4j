package com.example.langchain.controller;

import com.example.langchain.service.EmbeddingService;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/embedding")
public class EmbeddingController {

    private final EmbeddingService embeddingService;

    public EmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody Map<String, Object> payload) {
        String text = payload == null ? null : asString(payload.get("text"));
        return embeddingService.testEmbedding(text);
    }

    @PostMapping("/ingest")
    @SuppressWarnings("unchecked")
    public Map<String, Object> ingest(@RequestBody Map<String, Object> payload) {
        String text = payload == null ? null : asString(payload.get("text"));
        String id = payload == null ? null : asString(payload.get("id"));
        Map<String, String> metadata = payload != null && payload.get("metadata") instanceof Map
                ? (Map<String, String>) payload.get("metadata")
                : Collections.emptyMap();
        return embeddingService.ingestText(text, id, metadata);
    }

    /**
     * 查询 ChromaDB 当前租户/数据库下的集合概览信息。
     * 返回内容包含集合名称、集合ID、所属租户、所属数据库、记录总量等，便于前端做集合列表展示。
     */
    @GetMapping("/chroma/collections")
    public Map<String, Object> chromaCollections() {
        return embeddingService.listChromaCollections();
    }

    /**
     * 分页查询指定集合中的记录数据。
     * 参数说明：
     * 1) collectionName：目标集合名，必填。
     * 2) limit：单页数量，选填，默认 20，服务端会做边界保护。
     * 3) offset：分页偏移量，选填，默认 0。
     * 返回内容包含集合基础信息、集合总数、当前页记录列表（id/document/metadata）。
     */
    @GetMapping("/chroma/records")
    public Map<String, Object> chromaRecords(@RequestParam("collectionName") String collectionName,
                                             @RequestParam(value = "limit", required = false) Integer limit,
                                             @RequestParam(value = "offset", required = false) Integer offset) {
        return embeddingService.listChromaRecords(collectionName, limit, offset);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
