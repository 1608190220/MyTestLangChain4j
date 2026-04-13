package com.example.langchain.controller;

import com.example.langchain.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    @Autowired
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 普通聊天接口（同步返回）
     */
    @GetMapping
    public String chat(@RequestParam String message) {
        return chatService.chat(message);
    }

    /**
     * 流式聊天接口（逐字输出）
     * 使用 SSE (Server-Sent Events) 格式
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam String message) {
        return chatService.chatStream(message)
                .map(data -> "data: " + data + "\n\n");
    }

    /**
     * 流式聊天接口（纯文本流）
     * 适用于前端直接读取
     */
    @GetMapping(value = "/stream/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> chatStreamText(@RequestParam String message) {
        return chatService.chatStream(message);
    }
}
