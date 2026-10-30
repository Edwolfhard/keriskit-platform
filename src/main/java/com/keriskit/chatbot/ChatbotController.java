package com.keriskit.chatbot;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/chat")
    public ChatbotService.ChatResponse chat(
            @RequestBody ChatRequest request) {

        if (request == null || request.question() == null) {
            throw invalidQuestion();
        }

        String question = request.question().strip();

        if (question.isBlank() || question.length() > 500) {
            throw invalidQuestion();
        }

        return chatbotService.chat(question);
    }

    private ResponseStatusException invalidQuestion() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Question must contain between 1 and 500 characters"
        );
    }

    public record ChatRequest(String question) {
    }
}