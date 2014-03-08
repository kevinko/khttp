// Copyright 2014, Kevin Ko <kevin@faveset.com>

package com.faveset.khttp;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Represents the key-value pairs in an HTTP headers.
 */
public class Headers {
    // Holds the characters representing the delimiter between header key and
    // value.
    private static final String sHeaderDelim = ": ";
    private static final byte[] sHeaderDelimBytes = { (byte) ':', (byte) ' ' };

    // Used to delimit multiple header values.
    private static final String sHeaderValueDelim = ",";
    private static final byte sHeaderValueDelimByte = (byte) ',';

    protected HashMap<String, List<String>> mHeaders;

    public Headers() {
        mHeaders = new HashMap<String, List<String>>();
    }

    /**
     * Key will be canonicalized so that the first letter and any letter
     * following a hypen is upper case; all other letters are lowercase.
     */
    protected String canonicalizeKey(String key) {
        StringBuilder builder = new StringBuilder(key.length());
        boolean seenHyphen = false;
        for (int ii = 0; ii < key.length(); ii++) {
            char ch = key.charAt(ii);
            if (ch == '-') {
                seenHyphen = true;
            } else if (ii == 0 || seenHyphen) {
                ch = Character.toUpperCase(ch);
                seenHyphen = false;
            }
            builder.append(ch);
        }
        return builder.toString();
    }

    /**
     * @return The list of values for the header named key or null if it
     * doesn't exist.  The returned list will always have non-zero length.
     */
    public List<String> get(String key) {
        key = canonicalizeKey(key);
        return mHeaders.get(key);
    }

    /**
     * Convenience method for returning the first header value or null if no
     * mapping exists.
     */
    public String getFirst(String key) {
        key = canonicalizeKey(key);

        List<String> l = mHeaders.get(key);
        if (l == null) {
            return null;
        }
        return l.get(0);
    }

    /**
     * @return the header map as a string in wire format.
     */
    public String toString() {
        StringBuilder builder = new StringBuilder();
        write(builder);
        return builder.toString();
    }

    /**
     * Writes the headers in (HTTP) wire format to buf.
     *
     * @return the number of bytes written.
     */
    public int write(ByteBuffer buf) throws BufferOverflowException {
        int start = buf.position();
        for (Map.Entry<String, List<String>> entry : mHeaders.entrySet()) {
            Strings.write(entry.getKey(), buf);
            buf.put(sHeaderDelimBytes);
            writeValue(buf, entry.getValue());

            buf.put(Strings.CRLF_BYTES);
        }
        int count = buf.position() - start;
        return count;
    }

    /**
     * Writes the headers in (HTTP) wire format to bufPool.
     *
     * @return the number of bytes written.
     */
    public int write(ByteBufferPool bufPool) {
        long start = bufPool.remaining();
        for (Map.Entry<String, List<String>> entry : mHeaders.entrySet()) {
            bufPool.writeString(entry.getKey());
            bufPool.writeString(sHeaderDelim);
            writeValuePool(bufPool, entry.getValue());
            bufPool.writeString(Strings.CRLF);
        }
        long count = bufPool.remaining() - start;
        return (int) count;
    }

    /**
     * Writes headers in (HTTP) wire format to the inserter.  The inserter
     * will not be closed on completion.
     */
    public void write(ByteBufferPool.Inserter inserter) {
        for (Map.Entry<String, List<String>> entry : mHeaders.entrySet()) {
            inserter.writeString(entry.getKey());
            inserter.writeString(sHeaderDelim);
            writeValueInserter(inserter, entry.getValue());
            inserter.writeString(Strings.CRLF);
        }
    }

    /**
     * Write the headers in (HTTP) wire format to the StringBuilder.
     *
     * @return the number of bytes written.
     */
    public int write(StringBuilder builder) {
        int start = builder.length();
        for (Map.Entry<String, List<String>> entry : mHeaders.entrySet()) {
            builder.append(entry.getKey());
            builder.append(": ");
            String v = Strings.join(entry.getValue(), ",");
            builder.append(v);

            builder.append("\r\n");
        }
        int count = builder.length() - start;
        return count;
    }

    /**
     * Writes the values as a comma-separated list to buf.
     */
    private static void writeValue(ByteBuffer buf, List<String> values) throws BufferOverflowException {
        if (values.size() == 0) {
            return;
        }

        Iterator<String> iter = values.iterator();
        Strings.write(iter.next(), buf);

        while (iter.hasNext()) {
            buf.put(sHeaderValueDelimByte);
            Strings.write(iter.next(), buf);
        }
    }

    /**
     * inserter will not be closed.
     */
    private static void writeValueInserter(ByteBufferPool.Inserter inserter, List<String> values) {
        if (values.size() == 0) {
            return;
        }

        Iterator<String> iter = values.iterator();
        inserter.writeString(iter.next());

        while (iter.hasNext()) {
            inserter.writeString(sHeaderValueDelim);
            inserter.writeString(iter.next());
        }
    }

    private static void writeValuePool(ByteBufferPool pool, List<String> values) {
        if (values.size() == 0) {
            return;
        }

        Iterator<String> iter = values.iterator();
        pool.writeString(iter.next());

        while (iter.hasNext()) {
            pool.writeString(sHeaderValueDelim);
            pool.writeString(iter.next());
        }
    }
}
