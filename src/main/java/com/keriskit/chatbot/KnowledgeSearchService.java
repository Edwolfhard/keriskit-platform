package com.keriskit.chatbot;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class KnowledgeSearchService {

    private static final Set<String> STOP_WORDS = Set.of(
            "apa", "apakah", "itu", "ini", "yang", "dan",
            "di", "ke", "dari", "untuk", "tentang", "ialah",
            "adalah", "what", "is", "the", "a", "an", "of",
            "in", "and", "about", "tell", "me"
    );

    private final KnowledgeService knowledgeService;

    public KnowledgeSearchService(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    public List<KnowledgeChunk> search(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        Set<String> keywords = tokenize(question);
        keywords.removeAll(STOP_WORDS);

        if (keywords.isEmpty()) {
            return List.of();
        }

        return knowledgeService.getChunks()
                .stream()
                .map(chunk -> new ScoredChunk(
                        chunk,
                        calculateScore(chunk.text(), keywords)
                ))
                .filter(result -> result.score() > 0)
                .sorted(
                        Comparator.comparingInt(ScoredChunk::score)
                                .reversed()
                                .thenComparingInt(
                                        result -> result.chunk().pdfPage()
                                )
                                .thenComparing(
                                        result -> result.chunk().id()
                                )
                )
                .limit(3)
                .map(ScoredChunk::chunk)
                .toList();
    }

    private int calculateScore(String text, Set<String> keywords) {
        String normalizedText = text.toLowerCase(Locale.ROOT);
        Set<String> words = tokenize(text);

        int score = 0;

        for (String keyword : keywords) {
            if (!words.contains(keyword)) {
                continue;
            }

            // Reward matching keywords.
            score += 10;

            // Reward repeated mentions, with a limit.
            long occurrences = Arrays.stream(
                            normalizedText.split("[^\\p{L}\\p{N}]+")
                    )
                    .filter(keyword::equals)
                    .count();

            score += (int) Math.min(occurrences, 5) * 2;

            // Prioritize sentences that define the keyword.
            String definitionPattern =
                    "\\b" + Pattern.quote(keyword)
                            + "\\s+(?:ialah|adalah|merupakan|merujuk)\\b";

            if (Pattern.compile(definitionPattern)
                    .matcher(normalizedText)
                    .find()) {
                score += 30;
            }

            // Prioritize a standalone heading matching the keyword.
            boolean matchingHeading = text.lines()
                    .map(String::trim)
                    .anyMatch(line -> line.equalsIgnoreCase(keyword));

            if (matchingHeading) {
                score += 20;
            }
        }

        return score;
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(
                        text.toLowerCase(Locale.ROOT)
                                .split("[^\\p{L}\\p{N}]+")
                )
                .filter(word -> !word.isBlank())
                .collect(Collectors.toSet());
    }

    private record ScoredChunk(KnowledgeChunk chunk, int score) {
    }
}