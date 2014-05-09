// Copyright 2014, Kevin Ko <kevin@faveset.com>

package com.faveset.khttp;

interface SSLState {
    enum OpResult {
        NONE,
        DRAIN_DEST_BUFFER,
        ENGINE_CLOSE,
        SCHEDULE_TASKS,
        SCHEDULE_UNWRAP,
        SCHEDULE_WRAP,
        STATE_CHANGE,
        // This should only be called from stepUnwrap.
        UNWRAP_LOAD_SRC_BUFFER,
    }

    /**
     * Unwraps from src to dest.
     */
    OpResult stepUnwrap(NetReader src, NetBuffer dest);

    /**
     * Wraps from src to dest.
     */
    OpResult stepWrap(NetReader src, NetBuffer dest);
}
