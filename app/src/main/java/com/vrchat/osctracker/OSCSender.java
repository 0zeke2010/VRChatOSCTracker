package com.vrchat.osctracker;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Sends pre-built OSC packets over UDP to a target host:port.
 * All network I/O happens on the calling thread — always invoke from
 * a background thread or the TrackerService's worker.
 */
public class OSCSender {

    private static final String TAG = "OSCSender";

    private final String host;
    private final int    port;

    public OSCSender(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Send the given OSC payload.
     * @return true on success, false on any network error.
     */
    public boolean send(byte[] packet) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(host);
            DatagramPacket dp   = new DatagramPacket(packet, packet.length, address, port);
            socket.send(dp);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send OSC packet: " + e.getMessage());
            return false;
        }
    }

    /**
     * Convenience: build and send a chatbox text message in one call.
     */
    public boolean sendChatbox(String text, boolean immediate) {
        byte[] packet = OSCMessage.build(text, immediate);
        return send(packet);
    }
}
