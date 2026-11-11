package com.keriskit.chatbot;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private static final int MAX_QUESTION_LENGTH = 500;
    private static final int MAX_HISTORY_MESSAGES = 12;
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_HISTORY_LENGTH = 12000;

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/chat")
    public ChatbotService.ChatResponse chat(
            @RequestBody ChatRequest request) {

        if (request == null || request.question() == null) {
            throw badRequest(
                    "Question must contain between 1 and 500 characters."
            );
        }

        String question = request.question().strip();

        if (question.isBlank()
                || question.length() > MAX_QUESTION_LENGTH) {

            throw badRequest(
                    "Question must contain between 1 and 500 characters."
            );
        }

        validateHistory(request.history());

        // The next step will connect history to ChatbotService.
        // Existing chat requests continue to work for now.
        return chatbotService.chat(question, request.history());
    }

    private void validateHistory(List<ChatMessage> history) {
        // Older frontend requests do not include history.
        if (history == null) {
            return;
        }

        if (history.size() > MAX_HISTORY_MESSAGES) {
            throw badRequest(
                    "Conversation history cannot exceed 12 messages."
            );
        }

        int totalLength = 0;

        for (ChatMessage message : history) {
            if (message == null) {
                throw badRequest(
                        "Conversation history contains an invalid message."
                );
            }

            boolean validRole =
                    "user".equals(message.role())
                            || "assistant".equals(message.role());

            if (!validRole) {
                throw badRequest(
                        "Message role must be user or assistant."
                );
            }

            if (message.content() == null
                    || message.content().isBlank()
                    || message.content().length() > MAX_MESSAGE_LENGTH) {

                throw badRequest(
                        "Each history message must contain between "
                                + "1 and 2000 characters."
                );
            }

            totalLength += message.content().length();

            if (totalLength > MAX_HISTORY_LENGTH) {
                throw badRequest(
                        "Conversation history cannot exceed 12000 characters."
                );
            }
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    public record ChatRequest(
            String question,
            List<ChatMessage> history) {
    }

    public record ChatMessage(
            String role,
            String content) {
    }
}