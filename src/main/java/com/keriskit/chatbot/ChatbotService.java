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

    private static final int MAX_HISTORY_MESSAGES = 12;

    private static final Pattern SOURCE_LABEL_PATTERN =
            Pattern.compile("\\[(S\\d+)\\]");

    private static final Pattern FOLLOW_UP_PATTERN = Pattern.compile(
            "\\b(it|its|they|them|their|this|that|these|those)\\b"
                    + "|^(and\\b|also\\b|what about\\b|how about\\b"
                    + "|tell me more\\b|explain more\\b"
                    + "|why\\b|how so\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private final KnowledgeSearchService searchService;
    private final OllamaService ollamaService;

    public ChatbotService(
            KnowledgeSearchService searchService,
            OllamaService ollamaService) {

        this.searchService = searchService;
        this.ollamaService = ollamaService;
    }

    // Keeps existing controller calls working.
    public ChatResponse chat(String question) {
        return chat(question, List.of());
    }

    public ChatResponse chat(
            String question,
            List<ChatbotController.ChatMessage> history) {

        List<ChatbotController.ChatMessage> recentHistory =
                prepareHistory(history);

        String searchQuestion =
                buildSearchQuestion(question, recentHistory);

        List<KnowledgeChunk> chunks =
                searchService.search(searchQuestion);

        if (chunks.isEmpty()) {
            return insufficientInformation();
        }

        StringBuilder context = new StringBuilder();

        List<SourceReference> availableSources =
                new ArrayList<>();

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

        String conversationQuestion =
                buildConversationQuestion(question, recentHistory);

        String answer = ollamaService.generateAnswer(
                conversationQuestion,
                context.toString()
        );

// Temporary diagnostics in the IntelliJ console.
        System.out.println(
                "KERISBOT CHUNKS: "
                        + chunks.stream()
                        .map(KnowledgeChunk::id)
                        .toList()
        );

        System.out.println(
                "KERISBOT RAW ANSWER: " + answer
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

        if (containsMessage(
                answer,
                INSUFFICIENT_INFORMATION_MESSAGE)) {

            return insufficientInformation();
        }

        Set<String> citedLabels =
                extractSourceLabels(answer);

        Set<String> availableLabels = new HashSet<>();

        for (SourceReference source : availableSources) {
            availableLabels.add(source.label());
        }

        // Only accept citation labels from the current search.
        if (citedLabels.isEmpty()
                || !availableLabels.containsAll(citedLabels)) {

            return insufficientInformation();
        }

        List<SourceReference> citedSources =
                availableSources.stream()
                        .filter(source ->
                                citedLabels.contains(source.label()))
                        .toList();

        return new ChatResponse(answer, citedSources);
    }

    private List<ChatbotController.ChatMessage> prepareHistory(
            List<ChatbotController.ChatMessage> history) {

        if (history == null || history.isEmpty()) {
            return List.of();
        }

        int start = Math.max(
                0,
                history.size() - MAX_HISTORY_MESSAGES
        );

        List<ChatbotController.ChatMessage> recentHistory =
                new ArrayList<>();

        for (int i = start; i < history.size(); i++) {
            ChatbotController.ChatMessage message =
                    history.get(i);

            if (message == null
                    || message.content() == null
                    || message.content().isBlank()) {

                continue;
            }

            boolean validRole =
                    "user".equals(message.role())
                            || "assistant".equals(message.role());

            if (!validRole) {
                continue;
            }

            // Old citation labels must not be reused as current sources.
            String content = SOURCE_LABEL_PATTERN
                    .matcher(message.content())
                    .replaceAll("")
                    .strip();

            if (content.isBlank()) {
                continue;
            }

            recentHistory.add(
                    new ChatbotController.ChatMessage(
                            message.role(),
                            content
                    )
            );
        }

        return List.copyOf(recentHistory);
    }

    private String buildSearchQuestion(
            String question,
            List<ChatbotController.ChatMessage> history) {

        if (history.isEmpty()
                || !isFollowUp(question)) {

            return question;
        }

        String previousQuestion = null;

        // Prefer the most recent self-contained user question.
        // Assistant replies are never used as source evidence.
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatbotController.ChatMessage message =
                    history.get(i);

            if (!"user".equals(message.role())) {
                continue;
            }

            if (previousQuestion == null) {
                previousQuestion = message.content();
            }

            if (!isFollowUp(message.content())) {
                previousQuestion = message.content();
                break;
            }
        }

        if (previousQuestion == null) {
            return question;
        }

        return question + "\n" + previousQuestion;
    }

    private boolean isFollowUp(String question) {
        return FOLLOW_UP_PATTERN
                .matcher(question.strip())
                .find();
    }

    private String buildConversationQuestion(
            String question,
            List<ChatbotController.ChatMessage> history) {

        if (history.isEmpty()) {
            return question;
        }

        StringBuilder conversation = new StringBuilder();

        conversation.append("""
                The following conversation is background data.
                Use it to understand references in the current question,
                such as "it", "that part", or "tell me more".
                Previous replies are not verified source evidence.
                Support factual claims only with the current source excerpts.
                Answer only the current question in natural English.
                If the reference is ambiguous, do not guess.

                PREVIOUS CONVERSATION:
                """);

        for (ChatbotController.ChatMessage message : history) {
            String speaker = "user".equals(message.role())
                    ? "User"
                    : "KerisBot";

            conversation.append(speaker)
                    .append(": ")
                    .append(message.content())
                    .append("\n\n");
        }

        conversation.append("CURRENT QUESTION:\n")
                .append(question);

        return conversation.toString();
    }

    private boolean containsMessage(
            String answer,
            String message) {

        return Pattern.compile(
                        Pattern.quote(message),
                        Pattern.CASE_INSENSITIVE
                )
                .matcher(answer)
                .find();
    }

    private Set<String> extractSourceLabels(String answer) {
        Set<String> labels = new HashSet<>();

        Matcher matcher =
                SOURCE_LABEL_PATTERN.matcher(answer);

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