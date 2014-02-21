// Copyright 2014, Kevin Ko <kevin@faveset.com>

package com.faveset.khttp;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import java.util.EnumMap;
import java.util.Map;

// Handles an HTTP request in non-blocking fashion.
class HttpConnection {
    private enum State {
        // Request start line.
        REQUEST_START,
        REQUEST_HEADERS,
        MESSAGE_BODY,
        // Sending response.  Receives will be blocked until
        // this state is complete.
        RESPONSE_SEND,
        SERVER_ERROR,

        // The state will be manually assigned by a callback.  If the callback
        // does not assign a state, the state will remain unchanged.
        // The receive handler will exit once this is encountered during
        // a transition.  (A selector can then call the receive handler again
        // if so configured.)
        MANUAL,
    }

    private static class StateEntry {
        private State mNextState;
        private StateHandler mHandler;

        /**
         * @param nextState the state to transition to on success.
         */
        public StateEntry(State nextState, StateHandler handler) {
            mNextState = nextState;
            mHandler = handler;
        }

        public StateHandler getHandler() {
            return mHandler;
        }

        public State getNextState() {
            return mNextState;
        }
    }

    /* Size for the internal buffers. */
    public static final int BUFFER_SIZE = 4096;

    private static final EnumMap<State, StateEntry> mStateHandlerMap;

    private Map<String, HttpHandler> mHttpHandlerMap;

    private NonBlockingConnection mConn;

    private HandlerState mHandlerState;

    private State mState;

    private NonBlockingConnection.OnCloseCallback mCloseCallback =
        new NonBlockingConnection.OnCloseCallback() {
            public void onClose(NonBlockingConnection conn) {
                handleClose(conn);
            }
        };

    private NonBlockingConnection.OnRecvCallback mRecvCallback =
        new NonBlockingConnection.OnRecvCallback() {
            public void onRecv(NonBlockingConnection conn, ByteBuffer buf) {
                handleRecv(conn, buf);
            }
        };

    private HandlerState.OnRequestCallback mRequestCallback =
        new HandlerState.OnRequestCallback() {
            public boolean onRequest(HttpRequest req, ByteBuffer data, ResponseWriter w) {
                return handleRequest(req, data, w);
            }
        };

    private ResponseWriter.OnSendCallback mSendResponseCallback =
        new ResponseWriter.OnSendCallback() {
            public void onSend() {
                handleSendResponse();
            }
        };

    public HttpConnection(Selector selector, SocketChannel chan) throws IOException {
        mConn = new NonBlockingConnection(selector, chan, BUFFER_SIZE);
        mHandlerState = new HandlerState().setOnRequestCallback(mRequestCallback);
        mState = State.REQUEST_START;
    }

    /**
     * Closes the connection and releases all resources.
     */
    public void close() {
        mConn.close();
    }

    /**
     * @return the underlying NonBlockingConnection.
     */
    public NonBlockingConnection getNonBlockingConnection() {
        return mConn;
    }

    /**
     * Handle read closes.
     */
    private void handleClose(NonBlockingConnection conn) {
        close();
    }

    private void handleRecv(NonBlockingConnection conn, ByteBuffer buf) {
        boolean done = false;
        do {
            done = handleStateStep(conn, buf);
        } while (!done);
    }

    private boolean handleRequest(HttpRequest req, ByteBuffer data, ResponseWriter w) {
        String uri = req.getUri();
        HttpHandler handler = mHttpHandlerMap.get(uri);
        if (handler == null) {
            sendErrorResponse(HttpStatus.NOT_FOUND);
            // Transition to a new state.
            return true;
        }

        // Prepare the writer.
        w.clear();

        handler.onRequest(req, w);

        switch (req.getBodyType()) {
            case READ:
                // We need to read the body in its entirety.
                // TODO

            case IGNORE:
            default:
                // It's safe to send the response right now.
                sendResponse(mConn, w);
                return true;
        }
    }

    /**
     * Called after the HTTP response has been sent to the client.  This
     * reconfigures the HttpConnection to listen for another request.
     */
    private void handleSendResponse() {
        mState = State.REQUEST_START;

        // Restart the receive, now that we're at the start state.
        mConn.recvPersistent(mRecvCallback);
    }

    /**
     * Performs one step for the state machine.
     *
     * @return true if more data needs to be read into buf.  The receive
     * handler will finish as a result.
     */
    private boolean handleStateStep(NonBlockingConnection conn, ByteBuffer buf) {
        StateEntry entry = mStateHandlerMap.get(mState);
        if (entry == null) {
            sendErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR);
            // Unknown state.  Stop state steps until we send the response.
            return true;
        }

        try {
            if (!entry.getHandler().handleState(conn, buf, mHandlerState)) {
                // Continue reading new data from the connection.  The recv()
                // is already persistent.
                return true;
            }

            State nextState = entry.getNextState();
            if (nextState == State.MANUAL) {
                // Hold back on advancing steps automatically so that the
                // receive handler can be called again (if configured).
                //
                // This gives an opportunity to pause receive handling when,
                // for example, sending a response.
                return true;
            }

            mState = nextState;
        } catch (InvalidRequestException e) {
            sendErrorResponse(e.getErrorCode());
            // Stop stepping states until we send the response.
            return true;
        }

        return false;
    }

    /**
     * Resets the connection state for a new request.
     */
    private void reset() {
        mState = State.REQUEST_START;
        mHandlerState.clear();
    }

    /**
     * Convenience method for sending error responses.  The state will
     * change to RESPONSE_SEND.  Thus, this should be called in a transition
     * to a MANUAL state or outside a state machine callback.
     */
    private void sendErrorResponse(int errorCode) {
        ResponseWriter writer = mHandlerState.getResponseWriter();
        writer.clear();
        writer.writeHeader(errorCode);

        sendResponse(mConn, writer);
    }

    /**
     * Sends w over conn and configures handleSendResponse to handle the
     * send completion callback.
     *
     * The state will change to RESPONSE_SEND.  Thus, this should be called
     * in a transition to a MANUAL state or outside a state machine callback.
     *
     * Receive callbacks will be blocked until send completion.
     */
    private void sendResponse(NonBlockingConnection conn, ResponseWriter w) {
        mState = State.RESPONSE_SEND;

        // Turn off receive callbacks, since the state machine is in a send
        // state.
        conn.cancelRecv();

        w.send(conn, mSendResponseCallback);
    }

    /**
     * Start HttpConnection processing.
     *
     * @param handlers maps uris to HttpHandlers for handling requests.
     */
    public void start(Map<String, HttpHandler> handlers) {
        mHttpHandlerMap = handlers;

        mConn.setOnCloseCallback(mCloseCallback);
        mConn.recvPersistent(mRecvCallback);
    }

    static {
        mStateHandlerMap = new EnumMap<State, StateEntry>(State.class);

        mStateHandlerMap.put(State.REQUEST_START,
                new StateEntry(State.REQUEST_HEADERS, new RequestStartHandler()));
        mStateHandlerMap.put(State.REQUEST_HEADERS,
                new StateEntry(State.MESSAGE_BODY, new RequestHeaderHandler()));

        // The handleRequest handler will configure the state.
        mStateHandlerMap.put(State.MESSAGE_BODY,
                new StateEntry(State.MANUAL, new MessageBodyHandler()));
    }
};
