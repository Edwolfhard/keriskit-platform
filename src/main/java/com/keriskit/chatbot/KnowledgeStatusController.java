package com.keriskit.chatbot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class KnowledgeStatusController {

    private final KnowledgeService knowledgeService;

    public KnowledgeStatusController(
            KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/api/chatbot/knowledge/status")
    public Map<String, Object> getKnowledgeStatus() {
        return Map.of(
                "status", "loaded",
                "chunkCount", knowledgeService.getChunkCount()
        );
    }
}