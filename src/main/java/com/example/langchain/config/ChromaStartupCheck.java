package com.example.langchain.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChromaStartupCheck implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ChromaStartupCheck.class);
    private final ChromaV2Client chromaV2Client;

    @Value("${app.vector-store.provider:chromadb}")
    private String provider;

    @Value("${app.vector-store.chroma.base-url:http://localhost:8000}")
    private String chromaBaseUrl;

    public ChromaStartupCheck(ChromaV2Client chromaV2Client) {
        this.chromaV2Client = chromaV2Client;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"chromadb".equalsIgnoreCase(provider)) {
            return;
        }
        try {
            String version = chromaV2Client.version();
            Map<String, Object> heartbeat = chromaV2Client.heartbeat();
            logger.info("[ChromaStartupCheck] baseUrl={}, version={}, heartbeat={}", chromaBaseUrl, version, heartbeat);
        } catch (Exception e) {
            logger.error("[ChromaStartupCheck] baseUrl={} connectivity check failed: {}", chromaBaseUrl, e.getMessage(), e);
        }
    }
}
