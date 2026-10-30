package com.keriskit.chatbot;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class KnowledgeSearchController {

    private final KnowledgeSearchService searchService;

    public KnowledgeSearchController(
            KnowledgeSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/chatbot/knowledge/search")
    public List<KnowledgeChunk> search(
            @RequestParam("question") String question) {

        if (question.isBlank() || question.length() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Question must contain between 1 and 500 characters"
            );
        }

        return searchService.search(question);
    }
}