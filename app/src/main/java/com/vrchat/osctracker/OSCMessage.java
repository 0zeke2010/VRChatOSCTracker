package com.vrchat.osctracker;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Builds a minimal OSC 1.0 packet for VRChat's /chatbox/input address.
 *
 * Packet layout:
 *   address string  (null-padded to 4-byte boundary)
 *   type tag string ",ssT\0" or ",ssTF\0" depending on whether we want
 *                   the message to bypass the keyboard (3rd arg = bool)
 *   string arg 1    – the text to display
 *   string arg 2    – (unused by VRChat but required by OSC spec, send "")
 *   bool   arg 3    – true  = send immediately (bypass keyboard)
 *
 * VRChat OSC docs: https://docs.vrchat.com/docs/osc-as-input-controller
 */
public class OSCMessage {

    private static final String ADDRESS = "/chatbox/input";

    /**
     * Build the full UDP payload for a chatbox message.
     *
     * @param text     The text to show in the chatbox.
     * @param immediate If true the message appears immediately (no keyboard animation).
     */
    public static byte[] build(String text, boolean immediate) {
        // Encode address
        byte[] addrBytes  = padToFour(ADDRESS.getBytes(StandardCharsets.US_ASCII));

        // Type tag: ",ssT" (two strings + true bool) or ",ssF"
        // VRChat actually ignores the 2nd string but we must include it per spec
        String typeTag    = immediate ? ",ssT" : ",ssF";
        byte[] tagBytes   = padToFour(typeTag.getBytes(StandardCharsets.US_ASCII));

        // String argument 1 – the actual text
        byte[] arg1Bytes  = padToFour(text.getBytes(StandardCharsets.UTF_8));

        // String argument 2 – empty, required by spec
        byte[] arg2Bytes  = padToFour("".getBytes(StandardCharsets.UTF_8));

        // Bool args are encoded in the type tag, no additional bytes needed

        int totalLen = addrBytes.length + tagBytes.length + arg1Bytes.length + arg2Bytes.length;
        ByteBuffer buf = ByteBuffer.allocate(totalLen);
        buf.put(addrBytes);
        buf.put(tagBytes);
        buf.put(arg1Bytes);
        buf.put(arg2Bytes);
        return buf.array();
    }

    /** Null-terminate then pad the byte array to the next 4-byte boundary. */
    private static byte[] padToFour(byte[] raw) {
        // +1 for null terminator, then round up
        int paddedLen = ((raw.length + 1) + 3) & ~3;
        byte[] padded = new byte[paddedLen];
        System.arraycopy(raw, 0, padded, 0, raw.length);
        // remaining bytes are already 0 (null)
        return padded;
    }
}
