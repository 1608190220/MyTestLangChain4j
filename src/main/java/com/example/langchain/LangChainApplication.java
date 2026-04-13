package com.example.langchain;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LangChainApplication {

    private static final EmbeddingModel EMBEDDING_MODEL_SINGLETON = new AllMiniLmL6V2EmbeddingModel();

    public static void main(String[] args) {
        SpringApplication.run(LangChainApplication.class, args);
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return EMBEDDING_MODEL_SINGLETON;
    }
}
