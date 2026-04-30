package com.vrchat.osctracker;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class OSCMessage {

    private static final String ADDRESS = "/chatbox/input";

    public static byte[] build(String text, boolean immediate) {
        byte[] addrBytes = padToFour(ADDRESS.getBytes(StandardCharsets.US_ASCII));
        byte[] tagBytes  = padToFour(",sTF".getBytes(StandardCharsets.US_ASCII));
        byte[] arg1Bytes = padToFour(text.getBytes(StandardCharsets.UTF_8));

        ByteBuffer buf = ByteBuffer.allocate(
            addrBytes.length + tagBytes.length + arg1Bytes.length);
        buf.put(addrBytes);
        buf.put(tagBytes);
        buf.put(arg1Bytes);
        return buf.array();
    }

    private static byte[] padToFour(byte[] raw) {
        int paddedLen = ((raw.length + 1) + 3) & ~3;
        byte[] padded = new byte[paddedLen];
        System.arraycopy(raw, 0, padded, 0, raw.length);
        return padded;
    }
}
