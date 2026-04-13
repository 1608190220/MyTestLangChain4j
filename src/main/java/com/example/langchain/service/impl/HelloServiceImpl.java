package com.example.langchain.service.impl;

import com.example.langchain.model.HelloResponse;
import com.example.langchain.service.HelloService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class HelloServiceImpl implements HelloService {
    @Override
    public HelloResponse getHelloMessage() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return new HelloResponse("Hello from LangChain Spring Boot Service!", timestamp);
    }
}