package com.quietgrid.sudoku;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;

/** Bounded JSON reader for the dependency-free offline interchange contract. */
final class PuzzleJson {
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private final String text;
    private int position;

    private PuzzleJson(String text) {
        if (text.length() > MAX_BYTES) throw new IllegalArgumentException("Puzzle file exceeds 2 MiB.");
        this.text = text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    static Object parse(String text) {
        PuzzleJson parser = new PuzzleJson(text);
        Object value = parser.value(0);
        parser.space();
        if (parser.position != parser.text.length()) throw parser.error();
        return value;
    }

    static String readDocument(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            if (bytes.size() + read > MAX_BYTES) throw new IOException("Puzzle file exceeds 2 MiB.");
            bytes.write(buffer, 0, read);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException ex) {
            throw new IOException("Puzzle files must use valid UTF-8.", ex);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        if (!(value instanceof Map)) throw new IllegalArgumentException("Expected a JSON object.");
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object value) {
        if (!(value instanceof List)) throw new IllegalArgumentException("Expected a JSON array.");
        return (List<Object>) value;
    }

    static String string(Object value) {
        if (!(value instanceof String)) throw new IllegalArgumentException("Expected a JSON string.");
        return (String) value;
    }

    static void keys(Map<String, Object> object, String... keys) {
        if (!object.keySet().equals(Set.of(keys))) {
            throw new IllegalArgumentException("Missing or unknown fields. Expected: " + String.join(", ", keys) + ".");
        }
    }

    private Object value(int depth) {
        if (depth > 8) throw error();
        space();
        if (position == text.length()) throw error();
        char c = text.charAt(position);
        if (c == '"') return quoted();
        if (c == '{') {
            position++;
            Map<String, Object> result = new LinkedHashMap<>();
            if (take('}')) return result;
            do {
                space();
                String key = quoted();
                if (!take(':') || result.containsKey(key)) throw error();
                result.put(key, value(depth + 1));
            } while (take(','));
            if (!take('}')) throw error();
            return result;
        }
        if (c == '[') {
            position++;
            List<Object> result = new ArrayList<>();
            if (take(']')) return result;
            do {
                if (result.size() >= PuzzleLibrary.MAX_ENTRIES) {
                    throw new IllegalArgumentException("Too many entries in puzzle file.");
                }
                result.add(value(depth + 1));
            } while (take(','));
            if (!take(']')) throw error();
            return result;
        }
        if (text.startsWith("null", position)) { position += 4; return null; }
        int start = position;
        if (c == '-') position++;
        if (position >= text.length() || text.charAt(position) < '0' || text.charAt(position) > '9') throw error();
        if (text.charAt(position) == '0') position++;
        else while (position < text.length() && text.charAt(position) >= '0' && text.charAt(position) <= '9') position++;
        try { return Long.valueOf(text.substring(start, position)); }
        catch (NumberFormatException ex) { throw error(); }
    }

    private String quoted() {
        if (position >= text.length() || text.charAt(position++) != '"') throw error();
        StringBuilder out = new StringBuilder();
        while (position < text.length()) {
            char c = text.charAt(position++);
            if (c == '"') return out.toString();
            if (c < 32) throw error();
            if (c == '\\') {
                if (position == text.length()) throw error();
                c = text.charAt(position++);
                switch (c) {
                    case '"': case '\\': case '/': break;
                    case 'b': c = '\b'; break;
                    case 'f': c = '\f'; break;
                    case 'n': c = '\n'; break;
                    case 'r': c = '\r'; break;
                    case 't': c = '\t'; break;
                    case 'u':
                        if (position + 4 > text.length()) throw error();
                        if (!text.substring(position, position + 4).matches("[0-9a-fA-F]{4}")) throw error();
                        try { c = (char) Integer.parseInt(text.substring(position, position + 4), 16); }
                        catch (NumberFormatException ex) { throw error(); }
                        position += 4;
                        break;
                    default: throw error();
                }
            }
            out.append(c);
        }
        throw error();
    }

    private boolean take(char c) {
        space();
        if (position < text.length() && text.charAt(position) == c) { position++; return true; }
        return false;
    }

    private void space() {
        while (position < text.length() && " \t\r\n".indexOf(text.charAt(position)) >= 0) position++;
    }

    private IllegalArgumentException error() {
        return new IllegalArgumentException("Malformed puzzle JSON at character " + position + ".");
    }
}
