// Copyright 2014, Kevin Ko <kevin@faveset.com>

package com.faveset.khttp;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO:
// add file sending and chunked transfers.
class ResponseWriter implements HttpResponseWriter {
    public interface OnSendCallback {
        void onSend();
    }

    // 1.1 is the default version.
    private static final int sHttpMinorVersionDefault = 1;

    private static final Map<Integer, String> sReasonMap;
    // This is the default reason that will be reported if one is not found
    // in sReasonMap.
    private static final String sUnknownReason = "Unknown";

    // Most web servers set the max total header length to 4-8KB, so
    // we'll just choose a standard page size.  The pool will grow as
    // needed; though, this size should handle most cases without additional
    // allocations.
    private static final int sBufferSize = 4096;

    private HeadersBuilder mHeadersBuilder;

    private ByteBufferPool mBufPool;

    private OnSendCallback mSendCallback;

    private NonBlockingConnection.OnSendCallback mNbcSendCallback;

    // Tracks whether writeHeader() has been called explicitly.
    private boolean mWroteHeaders;

    private int mHttpMinorVersion;

    public ResponseWriter() {
        mHeadersBuilder = new HeadersBuilder();
        // Use direct allocations.
        mBufPool = new ByteBufferPool(sBufferSize, true);
        mNbcSendCallback = new NonBlockingConnection.OnSendCallback() {
            public void onSend(NonBlockingConnection conn) {
                mSendCallback.onSend();
            }
        };

        mHttpMinorVersion = sHttpMinorVersionDefault;
    }

    /**
     * Resets the ResponseWriter state so that it can be reused.
     */
    public void clear() {
        mHeadersBuilder.clear();
        mBufPool.clear();
        mWroteHeaders = false;
    }

    public HeadersBuilder getHeadersBuilder() {
        return mHeadersBuilder;
    }

    /**
     * Finalizes the response and sends it over the connection.  This manages
     * NonBlockingConnection callbacks until completion and then calls the
     * OnSendCallback specified in setOnSendCallback when sending is done.
     *
     * The callback should clear the ResponseWriter's state before reusing.
     */
    public void send(NonBlockingConnection conn) {
        long remCount = mBufPool.remaining();
        ByteBuffer[] bufs = mBufPool.build();
        conn.send(mNbcSendCallback, bufs, remCount);
    }

    /**
     * Configures the ResponseWriter to use an HTTP minor version of
     * minorVersion.  Major version will always be 1.
     *
     * @return this for chaining.
    public ResponseWriter setHttpMinorVersion(int minorVersion) {
        mHttpMinorVersion = minorVersion;
        return this;
    }

    /**
     * Assigns the send callback and returns this (for chaining).
     */
    public ResponseWriter setOnSendCallback(OnSendCallback cb) {
        mSendCallback = cb;
        return this;
    }

    /**
     * Uses buf as the data for the HTTP reply.
     *
     * This will implicitly call writeHeader with status OK if not already
     * performed by the caller.
     */
    public void write(ByteBuffer buf) {
        writeHeader(HttpStatus.OK);

        mBufPool.writeBuffer(buf);
    }

    public void write(String s) {
        writeHeader(HttpStatus.OK);

        mBufPool.writeString(s);
    }

    /**
     * Prepares and writes an HTTP response header with given status code.
     * If not called, the other write methods will call this implicitly with
     * status OK.
     */
    public void writeHeader(int statusCode) throws BufferOverflowException {
        if (mWroteHeaders) {
            return;
        }

        String reason = sReasonMap.get(statusCode);
        if (reason == null) {
            reason = sUnknownReason;
        }

        String statusLine = String.format("HTTP/1.%d %d, %s\r\n",
                mHttpMinorVersion, statusCode, reason);
        mBufPool.writeString(statusLine);

        mHeadersBuilder.write(mBufPool);

        // Terminal CRLF.
        mBufPool.writeString(Strings.CRLF);

        // We're now ready for the message-body.

        mWroteHeaders = true;
    }

    static {
        // These are taken from RFC2616 recommendations.
        sReasonMap = new HashMap<Integer, String>();
        sReasonMap.put(100, "Continue");
        sReasonMap.put(101, "Switching Protocols");
        sReasonMap.put(200, "OK");
        sReasonMap.put(201, "Created");
        sReasonMap.put(202, "Accepted");
        sReasonMap.put(203, "Non-Authoritative Information");
        sReasonMap.put(204, "No Content");
        sReasonMap.put(205, "Reset Content");
        sReasonMap.put(206, "Partial Content");
        sReasonMap.put(300, "Multiple Choices");
        sReasonMap.put(301, "Moved Permanently");
        sReasonMap.put(302, "Found");
        sReasonMap.put(303, "See Other");
        sReasonMap.put(304, "Not Modified");
        sReasonMap.put(305, "Use Proxy");
        sReasonMap.put(307, "Temporary Redirect");
        sReasonMap.put(400, "Bad Request");
        sReasonMap.put(401, "Unauthorized");
        sReasonMap.put(402, "Payment Required ");
        sReasonMap.put(403, "Forbidden");
        sReasonMap.put(404, "Not Found");
        sReasonMap.put(405, "Method Not Allowed");
        sReasonMap.put(406, "Not Acceptable");
        sReasonMap.put(407, "Proxy Authentication Required");
        sReasonMap.put(408, "Request Time-out");
        sReasonMap.put(409, "Conflict");
        sReasonMap.put(410, "Gone");
        sReasonMap.put(411, "Length Required");
        sReasonMap.put(412, "Precondition Failed");
        sReasonMap.put(413, "Request Entity Too Large");
        sReasonMap.put(414, "Request-URI Too Large");
        sReasonMap.put(415, "Unsupported Media Type");
        sReasonMap.put(416, "Requested range not satisfiable");
        sReasonMap.put(417, "Expectation Failed");
        sReasonMap.put(500, "Internal Server Error");
        sReasonMap.put(501, "Not Implemented");
        sReasonMap.put(502, "Bad Gateway");
        sReasonMap.put(503, "Service Unavailable");
        sReasonMap.put(504, "Gateway Time-out");
        sReasonMap.put(505, "HTTP Version not supported");
    }
}