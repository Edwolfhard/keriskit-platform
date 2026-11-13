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

    private static final Pattern LIST_ITEM_PATTERN =
            Pattern.compile("^\\s*(?:\\d+[.)]|[-*•])\\s+.*");

    private static final Pattern SOURCE_NOTE_PATTERN =
            Pattern.compile(
                    "^\\h*(?:note\\h*:\\h*)?"
                            + "(?:the\\h+)?source\\h+labels?\\b.*$",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
            );

    private static final Pattern FOLLOW_UP_PATTERN =
            Pattern.compile(
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

    public ChatResponse chat(String question) {
        return chat(question, List.of());
    }

    public ChatResponse chat(
            String question,
            List<ChatbotController.ChatMessage> history) {

        if (question == null || question.isBlank()) {
            return insufficientInformation();
        }

        question = question.strip();

        List<ChatbotController.ChatMessage> recentHistory =
                prepareHistory(history);

        String searchQuestion =
                buildSearchQuestion(question, recentHistory);

        List<KnowledgeChunk> chunks =
                searchService.search(searchQuestion);

        if (chunks.isEmpty()) {
            return rejectAnswer("No matching knowledge chunks.");
        }

        System.out.println(
                "KERISBOT CHUNKS: "
                        + chunks.stream()
                        .map(KnowledgeChunk::id)
                        .toList()
        );

        StringBuilder context = new StringBuilder();
        List<SourceReference> availableSources = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            String label = "S" + (i + 1);

            // Page numbers and titles remain in source metadata.
            // The model receives only the label and source text.
            context.append("[")
                    .append(label)
                    .append("]\n")
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

        System.out.println("KERISBOT RAW ANSWER: " + answer);

        if (answer == null || answer.isBlank()) {
            return rejectAnswer("The generated answer was empty.");
        }

        answer = answer.strip();

        // Remove this generic closing sentence.
        // Component descriptions still require source citations.
        answer = answer.replaceAll(
                "(?im)^\\h*These parts work together to form "
                        + "the external structure of the keris\\.\\h*$",
                ""
        ).strip();

        if (answer.equalsIgnoreCase(OUT_OF_SCOPE_MESSAGE)) {
            return new ChatResponse(
                    OUT_OF_SCOPE_MESSAGE,
                    List.of()
            );
        }

        if (answer.equalsIgnoreCase(
                INSUFFICIENT_INFORMATION_MESSAGE)) {

            return insufficientInformation();
        }

        Set<String> availableLabels = new HashSet<>();

        for (SourceReference source : availableSources) {
            availableLabels.add(source.label());
        }

        Set<String> citedLabels = extractSourceLabels(answer);

        if (citedLabels.isEmpty()) {
            return rejectAnswer("No source citations were supplied.");
        }

        if (!availableLabels.containsAll(citedLabels)) {
            return rejectAnswer(
                    "The answer used an unavailable source label."
            );
        }

        if (SOURCE_NOTE_PATTERN.matcher(answer).find()) {
            return rejectAnswer(
                    "The answer included a source-label note."
            );
        }

        if (!hasCitationsForContentBlocks(answer)) {
            return rejectAnswer(
                    "A paragraph or list item had no source citation."
            );
        }

        List<SourceReference> citedSources =
                availableSources.stream()
                        .filter(source ->
                                citedLabels.contains(source.label()))
                        .toList();

        return new ChatResponse(answer, citedSources);
    }

    private boolean hasCitationsForContentBlocks(String answer) {
        List<String> blocks = splitContentBlocks(answer);
        int citedBlocks = 0;

        for (String block : blocks) {
            String content = block.strip();

            if (content.isBlank()) {
                continue;
            }

            // Allow short introductory headings without citations.
            // This is a formatting heuristic, not a factual check.
            if (isHeading(content)) {
                continue;
            }

            Set<String> labels = extractSourceLabels(content);

            if (labels.isEmpty()) {
                return false;
            }

            // A block containing only citation labels is not an answer.
            String textWithoutLabels = SOURCE_LABEL_PATTERN
                    .matcher(content)
                    .replaceAll("");

            boolean hasText = textWithoutLabels.codePoints()
                    .anyMatch(Character::isLetter);

            if (!hasText) {
                return false;
            }

            citedBlocks++;
        }

        return citedBlocks > 0;
    }

    private List<String> splitContentBlocks(String answer) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : answer.lines().toList()) {
            String trimmed = line.strip();

            if (trimmed.isBlank()) {
                flushBlock(current, blocks);
                continue;
            }

            if (LIST_ITEM_PATTERN.matcher(trimmed).matches()) {
                flushBlock(current, blocks);
            }

            if (!current.isEmpty()) {
                current.append("\n");
            }

            current.append(trimmed);
        }

        flushBlock(current, blocks);

        return blocks;
    }

    private void flushBlock(
            StringBuilder current,
            List<String> blocks) {

        if (!current.isEmpty()) {
            blocks.add(current.toString().strip());
            current.setLength(0);
        }
    }

    private boolean isHeading(String text) {
        if (text.contains("\n")
                || text.length() > 100
                || !text.endsWith(":")
                || LIST_ITEM_PATTERN.matcher(text).matches()
                || SOURCE_LABEL_PATTERN.matcher(text).find()) {

            return false;
        }

        return text.split("\\s+").length <= 18;
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
            ChatbotController.ChatMessage message = history.get(i);

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

            String content = SOURCE_LABEL_PATTERN
                    .matcher(message.content())
                    .replaceAll("")
                    .strip();

            if (!content.isBlank()) {
                recentHistory.add(
                        new ChatbotController.ChatMessage(
                                message.role(),
                                content
                        )
                );
            }
        }

        return List.copyOf(recentHistory);
    }

    private String buildSearchQuestion(
            String question,
            List<ChatbotController.ChatMessage> history) {

        if (history.isEmpty() || !isFollowUp(question)) {
            return question;
        }

        String previousQuestion = null;

        for (int i = history.size() - 1; i >= 0; i--) {
            ChatbotController.ChatMessage message = history.get(i);

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

        return previousQuestion == null
                ? question
                : question + "\n" + previousQuestion;
    }

    private boolean isFollowUp(String question) {
        return FOLLOW_UP_PATTERN
                .matcher(question.strip())
                .find();
    }

    private String buildConversationQuestion(
            String question,
            List<ChatbotController.ChatMessage> history) {

        if (history.isEmpty() || !isFollowUp(question)) {
            return question;
        }

        StringBuilder conversation = new StringBuilder();

        conversation.append("""
                The following conversation is background data.
                Use it only to understand references in the current question.
                Previous replies are not verified source evidence.
                Support factual claims only with current source excerpts.
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

    private Set<String> extractSourceLabels(String answer) {
        Set<String> labels = new HashSet<>();
        Matcher matcher = SOURCE_LABEL_PATTERN.matcher(answer);

        while (matcher.find()) {
            labels.add(matcher.group(1));
        }

        return labels;
    }

    private ChatResponse rejectAnswer(String reason) {
        System.out.println("KERISBOT VALIDATION: REJECTED - " + reason);
        return insufficientInformation();
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