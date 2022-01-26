package com.guillaumecl.wordlesolver;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.lambda.function.Function2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class Main {
    private List<String> readLines(String fileName) throws IOException {
        return Files.readAllLines(Paths.get(fileName))
                .stream()
                .map(StringUtils::trim)
                .filter(s -> !StringUtils.isBlank(s))
                .collect(Collectors.toList());
    }

    private List<String> readLines() throws IOException {
        return readLines("words.txt");
    }

    /*
    for every guess word W
  for every answer word A
    if I guess W and the answer is A, how many words are remaining possibilities? X = that.
    sum += X
score[W] = sum / word_count.
     */

    private boolean wordContainsChar(char[] word, char chr) {
        for (int idx = 0; idx < 5; idx++) {
            if (word[idx] == chr) {
                return true;
            }
        }
        return false;
    }

    private boolean doesWordFollowRules(char[] guess, boolean[] mustContain, boolean[] correctPos, char[] word) {
        for (int idx = 0; idx < 5; idx++) {
            if (correctPos[idx]) {
                if (guess[idx] != word[idx]) {
                    return false;
                }
            } else if (mustContain[idx]) {
                if (!wordContainsChar(word, guess[idx]) || guess[idx] == word[idx]) {
                    return false;
                }
            } else {
                if (wordContainsChar(word, guess[idx])) {
                    return false;
                }
            }
        }
        return true;
    }

    private int calcScore(char[] guess, char[] answer, char[][] words) {
        boolean[] mustContain = new boolean[5];
        boolean[] correctPos = new boolean[5];
        for (int idx = 0; idx < 5; idx++) {
            char gc = guess[idx];
            char ac = answer[idx];
            if (gc == ac) {
                mustContain[idx] = true;
                correctPos[idx] = true;
            } else if (wordContainsChar(answer, gc)) {
                mustContain[idx] = true;
                correctPos[idx] = false;
            } else {
                mustContain[idx] = false;
                correctPos[idx] = false;
            }
        }

        int count = 0;
        for (int idx = 0; idx < words.length; idx++) {
            if (doesWordFollowRules(guess, mustContain, correctPos, words[idx])) {
                count++;
            }
        }
        return count;
    }

    private double calcGuessScore(char[] guess, char[][] words) {
        long total = 0L;
        for (int idx = 0; idx < words.length; idx++) {
            total += calcScore(guess, words[idx], words);
        }
        return (double)total / (double)words.length;
    }

    private List<Pair<String, Double>> calcScores(char[][] answers, char[][] guesses) {
        return Arrays.stream(guesses)
                .parallel()
                .map(guess -> Pair.of(String.copyValueOf(guess), calcGuessScore(guess, answers)))
                .sorted(Comparator.comparingDouble(Pair::getRight))
                .collect(Collectors.toList());
    }

    private void dumpResults(List<Pair<String, Double>> list) {
        System.out.println("Results: ");
        list.stream().limit(100).forEach(pair -> System.out.println(pair.getLeft() + ": " + pair.getRight()));
    }

    private String formatPairToJson(Pair<String, Double> pair) {
        return "{ \"word\": \"" + pair.getLeft() + "\", \"score\": " + pair.getRight() + " }";
    }
    private void dumpToDisk(List<Pair<String, Double>> list) throws IOException {
        String result = list.stream()
                .map(this::formatPairToJson)
                .collect(Collectors.joining(", "));
        Files.writeString(Paths.get("scores.json"), "[" + result + "]");
    }

    private List<String> filterList(String guessStr, String resultStr, List<String> words) {
        char[] guess = guessStr.toCharArray();
        char[] result = resultStr.toLowerCase().toCharArray();

        boolean[] mustContain = new boolean[5];
        boolean[] correctPos = new boolean[5];
        for (int idx = 0; idx < 5; idx++) {
            if (result[idx] == 'g') {
                mustContain[idx] = true;
                correctPos[idx] = true;
            } else if (result[idx] == 'y') {
                mustContain[idx] = true;
                correctPos[idx] = false;
            } else {
                mustContain[idx] = false;
                correctPos[idx] = false;
            }
        }

        return words.stream()
                .filter(word -> doesWordFollowRules(guess, mustContain, correctPos, word.toCharArray()))
                .collect(Collectors.toList());
    }

    private List<String> filterCompleteList(List<String> guesses, List<String> results, List<String> words) {
        for (int idx = 0; idx < guesses.size(); idx++) {
            words = filterList(guesses.get(idx), results.get(idx), words);
        }
        return words;
    }

    public void run(List<String> guesses, List<String> results, boolean isHardMode) throws IOException {
        List<String> lines = readLines();
        List<String> filteredWords = filterCompleteList(guesses, results, lines);
        char[][] answers = filteredWords.stream()
                .map(String::toCharArray)
                .toArray(char[][]::new);
        List<Pair<String, Double>> scores;
        if (isHardMode) {
            scores = calcScores(answers, answers);
        } else {
            char[][] guessWords = lines.stream()
                    .map(String::toCharArray)
                    .toArray(char[][]::new);
            scores = calcScores(answers, guessWords);
        }
        dumpResults(scores);
        if (guesses.isEmpty()) {
            dumpToDisk(scores);
        }
    }

    public static void main(String[] args) {
        boolean isHardMode = StringUtils.equalsIgnoreCase(args[0], "h");
        List<String> guesses = new ArrayList<>(args.length / 2 + 1);
        List<String> results = new ArrayList<>(args.length / 2 + 1);
        for (int idx = 1; idx < args.length; idx++) {
            if (idx % 2 == 0) {
                results.add(args[idx]);
            } else {
                guesses.add(args[idx]);
            }
        }
        var main = new Main();
        try {
            main.run(guesses, results, isHardMode);
        } catch (IOException e) {
            System.err.println(e);
        }
    }
}
