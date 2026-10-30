package com.keriskit.chatbot;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatbotService {

    private static final String OUT_OF_SCOPE_MESSAGE =
            "I can only help with questions about keris.";

    private static final String INSUFFICIENT_INFORMATION_MESSAGE =
            "I could not find enough information in the available sources.";

    private static final Pattern SOURCE_LABEL_PATTERN =
            Pattern.compile("\\[(S\\d+)\\]");

    private final KnowledgeSearchService searchService;
    private final OllamaService ollamaService;

    public ChatbotService(
            KnowledgeSearchService searchService,
            OllamaService ollamaService) {

        this.searchService = searchService;
        this.ollamaService = ollamaService;
    }

    public ChatResponse chat(String question) {
        List<KnowledgeChunk> chunks = searchService.search(question);

        if (chunks.isEmpty()) {
            return insufficientInformation();
        }

        StringBuilder context = new StringBuilder();
        List<SourceReference> availableSources = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            String label = "S" + (i + 1);

            context.append("[")
                    .append(label)
                    .append("]\n")
                    .append("Source: Keris Melayu Semenanjung\n")
                    .append("PDF page: ")
                    .append(chunk.pdfPage())
                    .append("\n")
                    .append(chunk.text())
                    .append("\n\n");

            availableSources.add(new SourceReference(
                    label,
                    chunk.id(),
                    "Keris Melayu Semenanjung",
                    chunk.pdfPage(),
                    chunk.reviewStatus()
            ));
        }

        String answer = ollamaService.generateAnswer(
                question,
                context.toString()
        );

        if (answer == null || answer.isBlank()) {
            return insufficientInformation();
        }

        answer = answer.strip();

        if (containsMessage(answer, OUT_OF_SCOPE_MESSAGE)) {
            return new ChatResponse(
                    OUT_OF_SCOPE_MESSAGE,
                    List.of()
            );
        }

        if (containsMessage(answer, INSUFFICIENT_INFORMATION_MESSAGE)) {
            return insufficientInformation();
        }

        Set<String> citedLabels = extractSourceLabels(answer);

        Set<String> availableLabels = new HashSet<>();

        for (SourceReference source : availableSources) {
            availableLabels.add(source.label());
        }

        // Reject answers with missing or invented source labels.
        if (citedLabels.isEmpty()
                || !availableLabels.containsAll(citedLabels)) {
            return insufficientInformation();
        }

        List<SourceReference> citedSources = availableSources.stream()
                .filter(source -> citedLabels.contains(source.label()))
                .toList();

        return new ChatResponse(answer, citedSources);
    }

    private boolean containsMessage(String answer, String message) {
        return Pattern.compile(
                        Pattern.quote(message),
                        Pattern.CASE_INSENSITIVE
                )
                .matcher(answer)
                .find();
    }

    private Set<String> extractSourceLabels(String answer) {
        Set<String> labels = new HashSet<>();
        Matcher matcher = SOURCE_LABEL_PATTERN.matcher(answer);

        while (matcher.find()) {
            labels.add(matcher.group(1));
        }

        return labels;
    }

    private ChatResponse insufficientInformation() {
        return new ChatResponse(
                INSUFFICIENT_INFORMATION_MESSAGE,
                List.of()
        );
    }

    public record ChatResponse(
            String answer,
            List<SourceReference> sources) {
    }

    public record SourceReference(
            String label,
            String chunkId,
            String title,
            int pdfPage,
            String reviewStatus) {
    }
}