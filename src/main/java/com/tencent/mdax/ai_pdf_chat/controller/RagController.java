package com.tencent.mdax.ai_pdf_chat.controller;

import com.tencent.mdax.ai_pdf_chat.service.RagService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Flux;

import java.util.Map;

// controller/RagController.java
@Controller
public class RagController {

    @Autowired
    RagService ragService;

    @Value("${pdf.path:classpath:pdf/sample.pdf}")
    private String pdfPath;

    // 初始化PDF文档
    @PostConstruct
    public void init() {
        try {
            ragService.initializeWithPdf(pdfPath);
        } catch (Exception e) {
            System.err.println("PDF初始化失败: " + e.getMessage());
        }
    }

    // 提供对话页面
    @GetMapping("/")
    public String chatPage() {
        return "redirect:/chat.html";
    }

    @PostMapping(value = "/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public Flux<String> askStream(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        String sessionId = request.get("sessionId");
        
        // 如果未提供sessionId，生成一个默认的
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "default-session";
        }
        
        try {
            return ragService.askStream(question, sessionId);
        } catch (Exception e) {
            return Flux.just("Error: " + e.getMessage());
        }
    }
    
    @PostMapping("/clear-history")
    @ResponseBody
    public ResponseEntity<Map<String, String>> clearHistory(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "default-session";
        }
        
        ragService.clearHistory(sessionId);
        return ResponseEntity.ok(Map.of("message", "对话历史已清空"));
    }
}