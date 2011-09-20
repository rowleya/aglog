package com.googlecode.aglog;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;

public class SocketListener extends Thread {

    private RTPListener listener = null;

    private MulticastSocket socket = null;

    private boolean done = false;

    public SocketListener(RTPListener listener, MulticastSocket socket) {
        this.listener = listener;
        this.socket = socket;
    }

    public void run() {
        while (!done && !socket.isClosed()) {
            DatagramPacket packet = listener.getFreePacket();
            if (packet != null) {
                try {
                    socket.receive(packet);
                    packet.setPort(socket.getLocalPort());
                    listener.handlePacket(packet);
                } catch (IOException e) {
                    if (!socket.isClosed()) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void close() {
        done = true;
    }
}
