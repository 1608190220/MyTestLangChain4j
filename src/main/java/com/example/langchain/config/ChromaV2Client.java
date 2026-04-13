package com.example.langchain.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class ChromaV2Client {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.vector-store.chroma.base-url:http://localhost:8000}")
    private String chromaBaseUrl;

    @Value("${app.vector-store.chroma.auth-type:none}")
    private String authType;

    @Value("${app.vector-store.chroma.token:}")
    private String token;

    @Value("${app.vector-store.chroma.username:}")
    private String username;

    @Value("${app.vector-store.chroma.password:}")
    private String password;

    @Value("${app.vector-store.chroma.tenant:default_tenant}")
    private String tenant;

    @Value("${app.vector-store.chroma.database:default_database}")
    private String database;

    @Value("${app.vector-store.chroma.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${app.vector-store.chroma.read-timeout-seconds:30}")
    private int readTimeoutSeconds;

    @Value("${app.vector-store.chroma.write-timeout-seconds:30}")
    private int writeTimeoutSeconds;

    public String version() {
        return executeText("GET", buildUrl(Arrays.asList("api", "v2", "version"), null), null).trim();
    }

    public Map<String, Object> heartbeat() {
        String body = executeText("GET", buildUrl(Arrays.asList("api", "v2", "heartbeat"), null), null);
        return readMap(body);
    }

    public String getOrCreateCollectionId(String collectionName) {
        List<Map<String, Object>> collections = listCollections();
        for (Map<String, Object> collection : collections) {
            Object name = collection.get("name");
            if (name instanceof String && collectionName.equals(name)) {
                return requireCollectionId(collection, collectionName);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", collectionName);
        payload.put("get_or_create", true);
        String body = executeText("POST", buildUrl(
                Arrays.asList("api", "v2", "tenants", tenant, "databases", database, "collections"),
                null
        ), payload);
        Map<String, Object> created = readMap(body);
        return requireCollectionId(created, collectionName);
    }

    public void upsert(String collectionId, String id, float[] embedding, String document, Map<String, String> metadata) {
        List<Float> embeddingList = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            embeddingList.add(value);
        }

        List<Map<String, Object>> metadatas = new ArrayList<>();
        Map<String, Object> metadataObject = new LinkedHashMap<>();
        if (metadata != null) {
            metadataObject.putAll(metadata);
        }
        metadatas.add(metadataObject);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ids", Collections.singletonList(id));
        payload.put("embeddings", Collections.singletonList(embeddingList));
        payload.put("documents", Collections.singletonList(document));
        payload.put("metadatas", metadatas);

        executeText("POST", buildUrl(
                Arrays.asList("api", "v2", "tenants", tenant, "databases", database, "collections", collectionId, "upsert"),
                null
        ), payload);
    }

    public int count(String collectionId) {
        String body = executeText("GET", buildUrl(
                Arrays.asList("api", "v2", "tenants", tenant, "databases", database, "collections", collectionId, "count"),
                null
        ), null);
        try {
            return Integer.parseInt(body.trim());
        } catch (NumberFormatException e) {
            throw new RuntimeException("解析 Chroma count 响应失败: " + body, e);
        }
    }

    public List<Map<String, Object>> collectionsOverview() {
        List<Map<String, Object>> collections = listCollections();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> collection : collections) {
            Map<String, Object> item = new LinkedHashMap<>();
            String id = requireCollectionId(collection, String.valueOf(collection.get("name")));
            item.put("id", id);
            item.put("name", collection.get("name"));
            item.put("tenant", collection.get("tenant"));
            item.put("database", collection.get("database"));
            item.put("count", count(id));
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> collectionRecords(String collectionName, int limit, int offset) {
        if (collectionName == null || collectionName.trim().isEmpty()) {
            throw new IllegalArgumentException("collectionName 不能为空");
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safeOffset = Math.max(offset, 0);

        String collectionId = findCollectionId(collectionName.trim());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("limit", safeLimit);
        payload.put("offset", safeOffset);
        payload.put("include", Arrays.asList("documents", "metadatas"));

        String body = executeText("POST", buildUrl(
                Arrays.asList("api", "v2", "tenants", tenant, "databases", database, "collections", collectionId, "get"),
                null
        ), payload);
        Map<String, Object> response = readMap(body);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("collectionName", collectionName);
        result.put("collectionId", collectionId);
        result.put("limit", safeLimit);
        result.put("offset", safeOffset);
        result.put("count", count(collectionId));
        result.put("records", buildRecords(response));
        return result;
    }

    private List<Map<String, Object>> listCollections() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("limit", "1000");
        query.put("offset", "0");
        String body = executeText("GET", buildUrl(
                Arrays.asList("api", "v2", "tenants", tenant, "databases", database, "collections"),
                query
        ), null);
        try {
            return objectMapper.readValue(body, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (IOException e) {
            throw new RuntimeException("解析 Chroma collections 响应失败: " + body, e);
        }
    }

    private String findCollectionId(String collectionName) {
        List<Map<String, Object>> collections = listCollections();
        for (Map<String, Object> collection : collections) {
            Object name = collection.get("name");
            if (name instanceof String && collectionName.equals(name)) {
                return requireCollectionId(collection, collectionName);
            }
        }
        throw new RuntimeException("集合不存在: " + collectionName);
    }

    private List<Map<String, Object>> buildRecords(Map<String, Object> rawResponse) {
        List<Map<String, Object>> records = new ArrayList<>();
        List<?> ids = asList(rawResponse.get("ids"));
        List<?> documents = asList(rawResponse.get("documents"));
        List<?> metadatas = asList(rawResponse.get("metadatas"));

        for (int i = 0; i < ids.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ids.get(i));
            item.put("document", i < documents.size() ? documents.get(i) : null);
            item.put("metadata", i < metadatas.size() ? metadatas.get(i) : null);
            records.add(item);
        }
        return records;
    }

    private List<?> asList(Object value) {
        if (value instanceof List) {
            return (List<?>) value;
        }
        return Collections.emptyList();
    }

    private String requireCollectionId(Map<String, Object> collection, String collectionName) {
        Object id = collection.get("id");
        if (!(id instanceof String) || ((String) id).trim().isEmpty()) {
            throw new RuntimeException("未获取到集合ID: " + collectionName);
        }
        return ((String) id).trim();
    }

    private Map<String, Object> readMap(String body) {
        try {
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            throw new RuntimeException("解析 Chroma 响应失败: " + body, e);
        }
    }

    private String executeText(String method, HttpUrl url, Map<String, Object> jsonBody) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
                .build();

        RequestBody requestBody = null;
        if (jsonBody != null) {
            try {
                requestBody = RequestBody.create(JSON, objectMapper.writeValueAsString(jsonBody));
            } catch (IOException e) {
                throw new RuntimeException("序列化 Chroma 请求体失败", e);
            }
        }

        Request.Builder requestBuilder = new Request.Builder().url(url);
        if ("POST".equalsIgnoreCase(method)) {
            requestBuilder.post(requestBody == null ? RequestBody.create(JSON, "{}") : requestBody);
        } else {
            requestBuilder.get();
        }
        requestBuilder.addHeader("Accept", "application/json, text/plain, */*");

        Map<String, String> headers = buildHeaders();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            requestBuilder.addHeader(entry.getKey(), entry.getValue());
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Chroma v2 请求失败, code=" + response.code() + ", url=" + url + ", body=" + responseBody);
            }
            return responseBody;
        } catch (IOException e) {
            throw new RuntimeException("调用 Chroma v2 失败: " + url, e);
        }
    }

    private HttpUrl buildUrl(List<String> segments, Map<String, String> query) {
        String normalizedBaseUrl = chromaBaseUrl == null ? "" : chromaBaseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        HttpUrl base = HttpUrl.parse(normalizedBaseUrl);
        if (base == null) {
            throw new IllegalArgumentException("非法的 Chroma base-url: " + chromaBaseUrl);
        }
        HttpUrl.Builder builder = base.newBuilder();
        for (String segment : segments) {
            builder.addPathSegment(segment);
        }
        if (query != null) {
            for (Map.Entry<String, String> entry : query.entrySet()) {
                builder.addQueryParameter(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        String mode = authType == null ? "none" : authType.trim().toLowerCase(Locale.ROOT);
        if ("token".equals(mode) && token != null && !token.trim().isEmpty()) {
            headers.put("X-Chroma-Token", token.trim());
            headers.put("Authorization", "Bearer " + token.trim());
        } else if ("basic".equals(mode) && username != null && password != null) {
            String raw = username + ":" + password;
            String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            headers.put("Authorization", "Basic " + encoded);
        }
        return headers;
    }
}
