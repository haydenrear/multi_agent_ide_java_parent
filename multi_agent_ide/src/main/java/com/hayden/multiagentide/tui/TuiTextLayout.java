package com.hayden.multiagentide.tui;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
final class TuiTextLayout {

    private TuiTextLayout() {
    }

    static String pad(String line, int width) {
        String trimmed = trim(line, width);
        if (trimmed.length() >= width) {
            return trimmed;
        }
        return trimmed + " ".repeat(width - trimmed.length());
    }

    static String trim(String line, int width) {
        if (line == null) {
            return "";
        }
        if (line.length() <= width) {
            return line;
        }
        return line.substring(0, Math.max(0, width));
    }

    static String truncateWithEllipsis(String line, int width) {
        if (line == null) {
            return "";
        }
        // Replace newlines/control chars with spaces — a newline in the cell
        // text causes a terminal line break that corrupts the TUI layout.
        String sanitized = line.replaceAll("[\\r\\n\\t]", " ");
        // Reserve 1 extra char for trailing space to prevent terminal word-wrap
        // from pushing the border onto the next line.
        int max = Math.max(1, width - 1);
        if (sanitized.length() <= max) {
            return sanitized;
        }
        if (max <= 3) {
            return sanitized.substring(0, max);
        }
        return sanitized.substring(0, max - 3) + "...";
    }

    static List<String> wrapFixed(String text, int width) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }
        int maxWidth = Math.max(1, width);
        List<String> lines = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + maxWidth);
            lines.add(text.substring(i, end));
            i = end;
        }
        return lines;
    }

    static List<String> wrapWord(String text, int width) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }
        int maxWidth = Math.max(1, width);
        List<String> lines = new ArrayList<>();
        String remaining = text;
        while (!remaining.isEmpty()) {
            if (remaining.length() <= maxWidth) {
                lines.add(remaining);
                break;
            }
            int breakAt = remaining.lastIndexOf(' ', maxWidth);
            if (breakAt <= 0) {
                breakAt = maxWidth;
            }
            String head = remaining.substring(0, breakAt).trim();
            if (head.isEmpty()) {
                head = remaining.substring(0, Math.min(maxWidth, remaining.length()));
                breakAt = head.length();
            }
            lines.add(head);
            remaining = remaining.substring(Math.min(remaining.length(), breakAt)).trim();
        }


        var l = lines.isEmpty() ? List.of("") : lines;

        return l;
    }
}
