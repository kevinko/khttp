// Copyright 2014, Kevin Ko <kevin@faveset.com>. All rights reserved.
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package com.faveset.mahttpd;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * An AsyncConnection provides an asynchronous send/receive interface.
 */
public interface AsyncConnection {
    /**
     * Called when the connection is closed by the peer.  The callback should
     * call close() to clean up.
     *
     * Socket closes are only detected on read, because send errors can
     * manifest in different ways (various IOExceptions).
     */
    public interface OnCloseCallback {
        void onClose(AsyncConnection conn);
    }

    /**
     * Called whenever an unrecoverable error (e.g., IOException) occurs
     * while reading/writing the underlying socket.
     */
    public interface OnErrorCallback {
        void onError(AsyncConnection conn, String reason);
    }

    /**
     * Called when new data is received.  buf is the in buffer and will
     * be positioned at the start of data to be read.  The callback must
     * consume all content within the buffer (use it or lose it).
     */
    public interface OnRecvCallback {
        void onRecv(AsyncConnection conn, ByteBuffer buf);
    }

    public interface OnSendCallback {
        void onSend(AsyncConnection conn);
    }

    /**
     * Cancels the receive handler and read interest on the selection key.
     *
     * @return the cancelled callback, which will be null if not assigned.
     */
    OnRecvCallback cancelRecv();

    /**
     * Closes the underlying channel.  This should be called to clean up
     * resources.
     *
     * @throws IOException if the underlying socket experienced an I/O error.
     */
    void close() throws IOException;

    /**
     * @return the internal send buffer.  One should take care to clear it before use to put it in a
     * known state.
     */
    ByteBuffer getOutBuffer();

    /**
     * Configures the connection for receiving data.  The callback will be
     * called when new data is received.
     *
     * This is not persistent.
     *
     * NOTE: the buffer that is passed to the callback (the internal in buffer)
     * is only guaranteed for the life of the callback.
     *
     * The in buffer will also be cleared when the recv is performed.
     *
     * @throws IllegalArgumentException if callback is null.
     */
    void recv(OnRecvCallback callback) throws IllegalArgumentException;

    /**
     * A persistent version of recv.  The callback will remain scheduled
     * until the recv is cancelled with cancelRecv.
     *
     * @throws IllegalArgumentException if callback is null.
     */
    void recvPersistent(OnRecvCallback callback) throws IllegalArgumentException;

    /**
     * Schedules the contents of the out buffer for sending.  Callback will
     * be called when the buffer is completely drained.  The buffer will not
     * be compacted, cleared, or otherwise modified.  The callback is not
     * persistent.
     *
     * @throws IllegalArgumentException if callback is null.
     */
    void send(OnSendCallback callback) throws IllegalArgumentException;

    /**
     * A variant of send that uses the contents of buf instead of the built-in
     * buffer.  (This is useful for sending MappedByteBuffers.)  The callback
     * is not persistent.
     *
     * IMPORTANT: One must not manipulate the buffer while a send is in
     * progress, since the contents are not copied.
     *
     * @param callback will be called on completion.
     *
     * @throws IllegalArgumentException if callback is null.
     */
    void send(OnSendCallback callback, ByteBuffer buf) throws IllegalArgumentException;

    /**
     * A variant of send() that sends an array of ByteBuffers.  The callback
     * is not persistent.
     *
     * @param bufsRemaining is the total number of bytes remaining for bufs.
     * Set to 0 to calculate automatically.
     *
     * @throws IllegalArgumentException if callback is null.
     */
    void send(OnSendCallback callback, ByteBuffer[] bufs, long bufsRemaining) throws IllegalArgumentException;

    /**
     * Schedules the contents of the out buffer for sending.  Callback will
     * be called as soon data has been sent from the buffer.  This is not
     * persistent.  As such, the sendPartial will typically be
     * rescheduled in the callback.
     *
     * If a send is called during the callback, it will take priority over
     * whatever remains in the internal buffer.
     *
     * @throws IllegalArgumentException if callback is null.
     */
    void sendPartial(OnSendCallback callback) throws IllegalArgumentException;

    /**
     * Configures the callback that will be called when the connection is
     * closed.
     *
     * @return this for chaining
     */
    AsyncConnection setOnCloseCallback(OnCloseCallback callback);

    /**
     * Configures the callback that will be called when an unrecoverable
     * network error is encountered.
     *
     * @return this for chaining
     */
    AsyncConnection setOnErrorCallback(OnErrorCallback callback);

    /**
     * @return the underlying SocketChannel
     */
    SocketChannel socketChannel();
}
