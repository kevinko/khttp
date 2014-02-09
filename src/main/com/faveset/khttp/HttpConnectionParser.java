// Copyright 2014, Kevin Ko <kevin@faveset.com>

package com.faveset.khttp;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

class HttpConnectionParser {
    private static final Charset US_ASCII_CHARSET = Charset.forName("US-ASCII");

    private static boolean isCtl(char ch) {
        if (ch <= 31 || ch == 127) {
            return true;
        }
        return false;
    }

    private static boolean isSeparator(char ch) {
        if (ch >= 0x30 && ch <= 0x39) {
            // [0-9]
            return false;
        }

        if (ch >= 0x41 && ch <= 0x5a) {
            // [A-Z]
            return false;
        }

        if (ch >= 0x61 && ch <= 0x7a) {
            // [a-z]
            return false;
        }

        switch (ch) {
            case '(':
            case ')':
            case '<':
            case '>':
            case '@':
            case ',':
            case ';':
            case ':':
            case '\\':
            case '"':
            case '/':
            case '[':
            case ']':
            case '?':
            case '=':
            case '{':
            case '}':
            case ' ':
            case '\t':
                return true;

            default:
                break;
        }

        return false;
    }

    /**
     * @return null if a line has yet to be parsed from buf, in which case
     * buf will be compacted with the remainder so that new data can be
     * loaded.  Otherwise, returns a buffer holding the parsed line.  The
     * array backing the buffer is shared, so one must be careful about
     * modifying the returned buffer.
     *
     * Carriage returns might be in the returned string but newlines will not.
     *
     * @throws BufferOverflowException if the line is longer than buf's
     * capacity.  A server will typically send a 414 Request-URI Too Long error
     * as a result.
     */
    public static ByteBuffer parseLine(ByteBuffer buf) throws BufferOverflowException {
        int startPos = buf.position();

        while (buf.hasRemaining()) {
            char ch = (char) buf.get();
            if (ch == '\r') {
                continue;
            }

            if (ch == '\n') {
                ByteBuffer result = buf.duplicate();
                // Exclude the delimiter.
                result.limit(buf.position() - 1);
                result.position(startPos);
                return result;
            }
        }

        // We haven't encountered a newline yet.  See if the buffer space has
        // been exceeded.
        if (startPos == 0 &&
            buf.position() == buf.capacity()) {
            throw new BufferOverflowException();
        }

        buf.position(startPos);
        buf.compact();
        // Signal that more data should be loaded.
        return null;
    }

    /**
     * @return the http version (0 for 1.0 or 1 for 1.1).
     * @throws IllegalArgumentException on error.
     */
    public static int parseHttpVersion(ByteBuffer buf) throws IllegalArgumentException {
        String word = parseWord(buf);
        if (word.length() == 0) {
            throw new IllegalArgumentException();
        }
        if (!word.startsWith("HTTP/1.")) {
            throw new IllegalArgumentException();
        }
        String minorVersionStr = word.substring(7);
        return Integer.parseInt(minorVersionStr);
    }

    /**
     * @throws IllegalArgumentException if the method name is unknown.
     */
    public static HttpRequest.Method parseMethod(ByteBuffer buf) throws IllegalArgumentException {
        String methodStr = parseWord(buf);
        if (methodStr.length() == 0) {
            throw new IllegalArgumentException();
        }
        return HttpRequest.Method.valueOf(methodStr);
    }

    /**
     * Parses the next TEXT from buf, where TEXT consists of any OCTET except
     * CTLs.
     *
     * buf's position will be incremented to the position of the separating
     * delimiter or the end of buf if none was found.
     */
    public static String parseText(ByteBuffer buf) {
        ByteBuffer result = buf.duplicate();

        while (buf.hasRemaining()) {
            char ch = (char) buf.get();
            if (isCtl(ch)) {
                buf.position(buf.position() - 1);
                break;
            }
        }

        result.limit(buf.position());
        return new String(result.array(), result.position(), result.remaining(), US_ASCII_CHARSET);
    }

    /**
     * Parses the next token from buf, where a token consists of any CHAR except
     * CTLs or separators.
     *
     * buf's position will be incremented to that of the separating delimiter.
     * This way, one can distinguish between an empty value and an invalid
     * header without a delimiter.
     */
    public static String parseToken(ByteBuffer buf) {
        ByteBuffer result = buf.duplicate();

        while (buf.hasRemaining()) {
            char ch = (char) buf.get();
            if (isCtl(ch) || isSeparator(ch)) {
                // Set the position to the delimiter.
                buf.position(buf.position() - 1);
                break;
            }
        }

        result.limit(buf.position());
        return new String(result.array(), result.position(), result.remaining(), US_ASCII_CHARSET);
    }

    /**
     * Parses the next word from buf, using whitespace ([\t\s\n\r]) as a
     * delimiter.  Repeated whitespace will be trimmed.
     *
     * buf's position will be incremented to one past the separating LWS
     * character.
     *
     * This will return the built-up string (possibly empty) when buf is
     * exhausted.  Thus, it is designed for when buf contains a self-contained
     * line.  Otherwise, the contents will be interpreted as an ASCII value.
     */
    public static String parseWord(ByteBuffer buf) {
        int startPos = buf.position();

        // Eat up all leading whitespace.
        while (buf.hasRemaining()) {
            char ch = (char) buf.get();
            if (ch != ' ' && ch != '\t' && ch != '\n' && ch != '\r') {
                break;
            }
            startPos++;
        }

        ByteBuffer result = buf.duplicate();
        result.position(startPos);

        while (true) {
            // At this point, buf.position() is not LWS due to the prior loop.
            if (!buf.hasRemaining()) {
                // Pass the entire string.
                result.limit(buf.position());
                break;
            }

            char ch = (char) buf.get();
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                // Exclude the whitespace delimiter.
                result.limit(buf.position() - 1);
                break;
            }
        }

        return new String(result.array(), result.position(), result.remaining(), US_ASCII_CHARSET);
    }

    /**
     * Skips all leading CRLFs in buf.
     */
    public static void skipCrlf(ByteBuffer buf) {
        while (buf.hasRemaining()) {
            char ch = (char) buf.get();
            if (ch != '\r' && ch != '\n') {
                // Put back the read character.
                buf.position(buf.position() - 1);
                break;
            }
        }
    }
}
