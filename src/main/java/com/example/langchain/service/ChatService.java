package com.example.langchain.service;

import reactor.core.publisher.Flux;

public interface ChatService {
    String chat(String message);

    /**
     * 流式聊天接口
     * @param message 用户消息
     * @return 流式响应
     */
    Flux<String> chatStream(String message);
}
