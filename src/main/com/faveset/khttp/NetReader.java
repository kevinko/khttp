// Copyright 2014, Kevin Ko <kevin@faveset.com>

package com.faveset.khttp;

import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

interface NetReader {
    /**
     * @return true if the NetReader does not contain any data when reading.
     */
    boolean isEmpty();

    SSLEngineResult unwrap(SSLEngine engine, NetBuffer dest) throws SSLException;

    void updateRead();

    SSLEngineResult wrap(SSLEngine engine, NetBuffer dest) throws SSLException;
}
