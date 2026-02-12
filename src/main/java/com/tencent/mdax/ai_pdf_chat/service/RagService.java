package com.tencent.mdax.ai_pdf_chat.service;

import com.tencent.mdax.ai_pdf_chat.utils.PdfTextExtractor;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

// service/RagService.java
@Service
public class RagService {

    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatLanguageModel;
    
    @Autowired
    private ChatHistoryService chatHistoryService;

    public RagService(EmbeddingModel embeddingModel, ChatLanguageModel chatLanguageModel) {
        this.embeddingModel = embeddingModel;
        this.chatLanguageModel = chatLanguageModel;
    }

    // ✅ 0.27.0 中存在
    private final EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
    private boolean initialized = false;

    public void initializeWithPdf(String pdfPath) {
        if (initialized) return;

        String text = PdfTextExtractor.extractTextFromPdf(Paths.get(pdfPath));
        Document document = Document.from(text);

        // ✅ 使用langchain4j 0.27.0中正确的DocumentSplitter
        DocumentSplitter splitter = DocumentSplitters.recursive(256, 30);
        List<TextSegment> segments = splitter.split(document);

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        for (int i = 0; i < segments.size(); i++) {
            embeddingStore.add(embeddings.get(i), segments.get(i));
        }

        initialized = true;
        System.out.println("✅ 加载完成，共 " + segments.size() + " 段");
    }

    public Flux<String> askStream(String question, String sessionId) {
        // 添加用户消息到历史
        chatHistoryService.addMessage(sessionId, "user", question);
        
        // 构建包含上下文的提示词
        String contextPrompt = chatHistoryService.buildContextPrompt(sessionId, question);
        
        Embedding queryEmbedding = embeddingModel.embed(contextPrompt).content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(queryEmbedding, 3);

        String documentContext = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.joining("\n---\n"));

        String prompt = "基于以下文档内容回答问题：\n" + documentContext + "\n\n" + contextPrompt;
        
        // 使用Flux实现真正的流式输出
        String answer = chatLanguageModel.generate(prompt);
        
        // 添加助手回答到历史
        chatHistoryService.addMessage(sessionId, "assistant", answer);
        
        // 将回答拆分成字符流，模拟真正的流式输出
        return Flux.fromArray(answer.split(""))
                .delayElements(java.time.Duration.ofMillis(50));
    }
    
    /**
     * 清空对话历史
     */
    public void clearHistory(String sessionId) {
        chatHistoryService.clearHistory(sessionId);
    }
}