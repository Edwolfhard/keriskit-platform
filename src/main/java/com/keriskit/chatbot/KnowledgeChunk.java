package com.keriskit.chatbot;

public record KnowledgeChunk(
        String id,
        int pdfPage,
        String text,
        String reviewStatus
) {
}