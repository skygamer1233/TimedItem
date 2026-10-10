package me.timeditem.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public final class DateParser {

    private DateParser() {
    }

    /**
     * Parses a date string based on the configured format and common date-only variations.
     *
     * @param input            the user input string (e.g. "12/07/2025" or "27/05/2023 13:17:29")
     * @param configuredFormat the pattern configured in config.yml (e.g. "dd/MM/yyyy HH:mm:ss")
     * @return epoch timestamp in milliseconds
     * @throws ParseException if input cannot be parsed
     */
    public static long parseDate(String input, String configuredFormat) throws ParseException {
        if (input == null || input.trim().isEmpty()) {
            throw new ParseException("Date string is empty", 0);
        }

        String trimmed = input.trim();

        // 1. Try exact configured format
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(configuredFormat);
            sdf.setLenient(false);
            return sdf.parse(trimmed).getTime();
        } catch (ParseException ignored) {
        }

        // 2. Try configured format without seconds if it has HH:mm:ss
        if (configuredFormat.contains("HH:mm:ss")) {
            try {
                String noSecFormat = configuredFormat.replace(":ss", "");
                SimpleDateFormat sdf = new SimpleDateFormat(noSecFormat);
                sdf.setLenient(false);
                return sdf.parse(trimmed).getTime();
            } catch (ParseException ignored) {
            }
        }

        // 3. Try date-only formats (sets time to 23:59:59 end-of-day)
        List<String> dateOnlyFormats = new ArrayList<>();

        // Extract date part from configured format (e.g. "dd/MM/yyyy")
        if (configuredFormat.contains(" ")) {
            String datePart = configuredFormat.split(" ")[0];
            dateOnlyFormats.add(datePart);
        }

        // Common date formats
        dateOnlyFormats.add("dd/MM/yyyy");
        dateOnlyFormats.add("yyyy-MM-dd");
        dateOnlyFormats.add("MM/dd/yyyy");
        dateOnlyFormats.add("dd-MM-yyyy");

        for (String pattern : dateOnlyFormats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                sdf.setLenient(false);
                Date parsedDate = sdf.parse(trimmed);

                // Set to 23:59:59 end of that date
                Calendar cal = Calendar.getInstance();
                cal.setTime(parsedDate);
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 999);
                return cal.getTimeInMillis();
            } catch (ParseException ignored) {
            }
        }

        throw new ParseException("Unable to parse date: " + trimmed, 0);
    }

    /**
     * Formats a timestamp in milliseconds using the configured format.
     */
    public static String formatDate(long millis, String configuredFormat) {
        SimpleDateFormat sdf = new SimpleDateFormat(configuredFormat);
        return sdf.format(new Date(millis));
    }
}
