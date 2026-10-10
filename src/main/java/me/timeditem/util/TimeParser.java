package me.timeditem.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeParser {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(\\d+)\\s*([a-zA-Z]+)");

    private TimeParser() {
    }

    /**
     * Parses duration strings like 1d2h, 3d5h, 30m, 10s into milliseconds.
     *
     * @param input duration string
     * @return duration in milliseconds
     * @throws IllegalArgumentException if the format is invalid
     */
    public static long parseDuration(String input) throws IllegalArgumentException {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Time input cannot be empty");
        }

        String cleaned = input.trim().replaceAll("\\s+", "");
        Matcher matcher = TOKEN_PATTERN.matcher(cleaned);

        long totalMillis = 0;
        int lastEnd = 0;
        boolean matched = false;

        while (matcher.find()) {
            if (matcher.start() != lastEnd) {
                throw new IllegalArgumentException("Invalid format near: " + cleaned.substring(lastEnd, matcher.start()));
            }

            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Number too large: " + matcher.group(1));
            }

            String unit = matcher.group(2).toLowerCase();
            long multiplier = getUnitMultiplier(unit);

            if (amount <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero");
            }

            try {
                totalMillis = Math.addExact(totalMillis, Math.multiplyExact(amount, multiplier));
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Duration overflow");
            }

            lastEnd = matcher.end();
            matched = true;
        }

        if (!matched || lastEnd != cleaned.length()) {
            throw new IllegalArgumentException("Invalid time format: " + input);
        }

        return totalMillis;
    }

    private static long getUnitMultiplier(String unit) {
        return switch (unit) {
            case "s", "sec", "second", "seconds" -> 1000L;
            case "m", "min", "minute", "minutes" -> 60_000L;
            case "h", "hr", "hour", "hours" -> 3_600_000L;
            case "d", "day", "days" -> 86_400_000L;
            case "w", "week", "weeks" -> 7L * 86_400_000L;
            case "mo", "month", "months" -> 30L * 86_400_000L;
            case "y", "yr", "year", "years" -> 365L * 86_400_000L;
            default -> throw new IllegalArgumentException("Unknown time unit: " + unit);
        };
    }

    /**
     * Formats duration in milliseconds to human-readable string like "1d 2h 30m"
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "0s";
        }

        long seconds = millis / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("s");
        }

        return sb.toString().trim();
    }
}
