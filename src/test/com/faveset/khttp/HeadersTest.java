// Copyright 2014, Kevin Ko <kevin@faveset.com>

package com.faveset.khttp;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HeadersTest extends Headers {
    @Test
    public void testCanonicalize() {
        assertEquals("Hello", canonicalizeKey("hello"));
        assertEquals("Hello-World", canonicalizeKey("hello-world"));
        assertEquals("-Ello-World", canonicalizeKey("-ello-world"));
    }

    @Test
    public void testWriteString() {
        HeadersBuilder builder = new HeadersBuilder();
        builder.add("hello", "world");
        builder.add("hello", "how");
        builder.add("hello", "are");
        builder.add("hello", "you");
        assertEquals("Hello: world,how,are,you\r\n", builder.toString());
    }

    @Test
    public void testWrite() throws BufferOverflowException {
        ByteBuffer buf = ByteBuffer.allocate(1024);

        HeadersBuilder builder = new HeadersBuilder();
        builder.add("hello", "world");
        builder.add("hello", "how");
        builder.add("hello", "are");
        builder.add("hello", "you");
        builder.write(buf);

        buf.flip();

        Helper.compare(buf, "Hello: world,how,are,you\r\n");

        buf.clear();
        builder.clear();

        builder.add("hello", "world");
        builder.write(buf);

        buf.flip();
        Helper.compare(buf, "Hello: world\r\n");
    }
}