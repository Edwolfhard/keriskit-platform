package com.keriskit.chatbot;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeService {

    private final List<KnowledgeChunk> chunks;

    public KnowledgeService(JsonMapper jsonMapper) throws IOException {
        ClassPathResource resource = new ClassPathResource(
                "knowledge/kerisbot-knowledge-draft.json"
        );

        try (InputStream input = resource.getInputStream()) {
            JsonNode root = jsonMapper.readTree(input);
            JsonNode chunkNodes = root.path("chunks");

            if (!chunkNodes.isArray() || chunkNodes.isEmpty()) {
                throw new IllegalStateException(
                        "Knowledge file must contain a non-empty chunks array"
                );
            }

            List<KnowledgeChunk> loadedChunks = new ArrayList<>();

            for (JsonNode node : chunkNodes) {
                String id = node.path("id").asText("");
                int pdfPage = node.path("pdf_page").asInt(0);
                String text = node.path("text").asText("");
                String reviewStatus = node.path("review_status").asText("");

                if (id.isBlank() || pdfPage < 1 || text.isBlank()) {
                    throw new IllegalStateException(
                            "Knowledge file contains an invalid chunk"
                    );
                }

                loadedChunks.add(new KnowledgeChunk(
                        id,
                        pdfPage,
                        text,
                        reviewStatus
                ));
            }

            this.chunks = List.copyOf(loadedChunks);
        }
    }

    public int getChunkCount() {
        return chunks.size();
    }

    public List<KnowledgeChunk> getChunks() {
        return chunks;
    }
}