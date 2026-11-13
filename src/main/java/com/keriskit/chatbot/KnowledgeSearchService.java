package com.keriskit.chatbot;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
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
            "in", "and", "about", "tell", "me",
            "are", "was", "were", "do", "does", "can",
            "could", "please", "to", "it", "its",
            "they", "them", "their", "this", "that",
            "these", "those", "used", "use", "using"
    );

    private static final List<Set<String>> COMPONENT_TERMS =
            List.of(
                    Set.of(
                            "hulu", "hilt", "hilts",
                            "handle", "handles"
                    ),
                    Set.of(
                            "pendokok", "pendongkok",
                            "collar", "collars"
                    ),
                    Set.of("sampir"),
                    Set.of(
                            "sarung", "sheath", "sheaths",
                            "scabbard", "scabbards"
                    ),
                    Set.of(
                            "bilah", "blade", "blades"
                    )
            );

    private static final Set<String> OVERVIEW_TERMS = Set.of(
            "component", "components",
            "part", "parts",
            "anatomy", "structure",
            "komponen", "bahagian", "struktur"
    );

    private static final Set<String> MATERIAL_QUESTION_TERMS =
            Set.of(
                    "material", "materials", "bahan",
                    "made", "make", "making",
                    "diperbuat", "terbuat", "dibuat",
                    "pembuatan"
            );

    private static final Set<String> MATERIAL_SOURCE_TERMS =
            Set.of(
                    "material", "materials", "bahan",
                    "diperbuat", "terbuat", "dibuat",
                    "pembuatan", "menggunakan",
                    "kayu", "wood", "wooden",
                    "gading", "ivory",
                    "tanduk", "horn",
                    "logam", "metal",
                    "perak", "silver",
                    "emas", "gold",
                    "tembaga", "copper",
                    "timah", "tin",
                    "pewter"
            );

    private final KnowledgeService knowledgeService;

    public KnowledgeSearchService(
            KnowledgeService knowledgeService) {

        this.knowledgeService = knowledgeService;
    }

    public List<KnowledgeChunk> search(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }

        Set<String> questionWords = tokenize(question);

        Set<String> componentKeywords =
                findComponentKeywords(questionWords);

        boolean materialQuestion = hasAny(
                questionWords,
                MATERIAL_QUESTION_TERMS
        );

        boolean overviewQuestion =
                componentKeywords.isEmpty()
                        && hasAny(questionWords, OVERVIEW_TERMS)
                        && !materialQuestion;

        Set<String> keywords = new HashSet<>(questionWords);
        keywords.removeAll(STOP_WORDS);
        keywords.addAll(componentKeywords);

        if (keywords.isEmpty()) {
            return List.of();
        }

        return knowledgeService.getChunks()
                .stream()
                .map(chunk -> new ScoredChunk(
                        chunk,
                        calculateScore(
                                chunk.text(),
                                keywords,
                                componentKeywords,
                                materialQuestion,
                                overviewQuestion
                        )
                ))
                .filter(result -> result.score() > 0)
                .sorted(
                        Comparator.comparingInt(ScoredChunk::score)
                                .reversed()
                                .thenComparingInt(
                                        result ->
                                                result.chunk().pdfPage()
                                )
                                .thenComparing(
                                        result -> result.chunk().id()
                                )
                )
                .limit(3)
                .map(ScoredChunk::chunk)
                .toList();
    }

    private Set<String> findComponentKeywords(
            Set<String> questionWords) {

        Set<String> result = new HashSet<>();

        for (Set<String> group : COMPONENT_TERMS) {
            if (hasAny(questionWords, group)) {
                result.addAll(group);
            }
        }

        if (questionWords.contains("tapak")
                && questionWords.contains("kasut")) {

            result.add("tapak");
            result.add("kasut");
        }

        if (questionWords.contains("batang")
                && questionWords.contains("serunai")) {

            result.add("batang");
            result.add("serunai");
        }

        return result;
    }

    private int calculateScore(
            String text,
            Set<String> keywords,
            Set<String> componentKeywords,
            boolean materialQuestion,
            boolean overviewQuestion) {

        if (text == null || text.isBlank()) {
            return 0;
        }

        Set<String> words = tokenize(text);

        if (overviewQuestion) {
            return calculateOverviewScore(text, words);
        }

        boolean matchesComponent =
                hasAny(words, componentKeywords);

        if (!componentKeywords.isEmpty()
                && !matchesComponent) {

            return 0;
        }

        int score = 0;

        for (String keyword : keywords) {
            if (words.contains(keyword)) {
                score += 10;
            }
        }

        if (matchesComponent) {
            score += 40;
        }

        if (materialQuestion) {
            long materialMatches = MATERIAL_SOURCE_TERMS.stream()
                    .filter(words::contains)
                    .count();

            score += (int) Math.min(materialMatches, 6) * 12;
        } else {
            String normalizedText =
                    text.toLowerCase(Locale.ROOT);

            for (String keyword : keywords) {
                if (!words.contains(keyword)) {
                    continue;
                }

                String definitionPattern =
                        "\\b" + Pattern.quote(keyword)
                                + "\\s+(?:ialah|adalah|merupakan|merujuk)\\b";

                if (Pattern.compile(definitionPattern)
                        .matcher(normalizedText)
                        .find()) {

                    score += 30;
                }

                boolean matchingHeading = text.lines()
                        .map(String::trim)
                        .anyMatch(line ->
                                line.equalsIgnoreCase(keyword));

                if (matchingHeading) {
                    score += 20;
                }
            }
        }

        return score;
    }

    private int calculateOverviewScore(
            String text,
            Set<String> words) {

        int componentCoverage = 0;

        for (Set<String> group : COMPONENT_TERMS) {
            if (hasAny(words, group)) {
                componentCoverage++;
            }
        }

        if (words.contains("kasut")
                || words.contains("buntut")) {

            componentCoverage++;
        }

        if (words.contains("batang")
                && words.contains("serunai")) {

            componentCoverage++;
        }

        if (componentCoverage == 0) {
            return 0;
        }

        // Prefer passages covering several parts together.
        int score = componentCoverage * 100;

        String normalizedText = text
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        if (normalizedText.contains("rupa bentuk asas")) {
            score += 200;
        }

        if (normalizedText.contains("terdiri daripada")
                || normalizedText.contains("consists of")) {

            score += 100;
        }

        return score;
    }

    private boolean hasAny(
            Set<String> words,
            Set<String> candidates) {

        return candidates.stream().anyMatch(words::contains);
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(
                        text.toLowerCase(Locale.ROOT)
                                .split("[^\\p{L}\\p{N}]+")
                )
                .filter(word -> !word.isBlank())
                .collect(Collectors.toSet());
    }

    private record ScoredChunk(
            KnowledgeChunk chunk,
            int score) {
    }
}