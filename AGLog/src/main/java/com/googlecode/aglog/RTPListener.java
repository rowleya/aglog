package com.googlecode.aglog;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.util.HashMap;
import java.util.LinkedList;

import com.googlecode.vicovre.media.rtp.RTCPHeader;
import com.googlecode.vicovre.media.rtp.RTPHeader;
import com.googlecode.vicovre.media.rtp.UnsupportedEncryptionException;
import com.googlecode.vicovre.media.rtp.encryption.AESCrypt;
import com.googlecode.vicovre.media.rtp.encryption.Crypt;
import com.googlecode.vicovre.media.rtp.encryption.DESCrypt;
import com.googlecode.vicovre.media.rtp.encryption.RTPCrypt;

public class RTPListener extends Thread {

    private static final int MAX_PACKETS = 100;

    private static final long MIN_PACKETS = 3;

    private MulticastSocket rtpSocket = null;

    private MulticastSocket rtcpSocket = null;

    private SocketListener rtpListener = null;

    private SocketListener rtcpListener = null;

    private RTPCrypt encryption = null;

    private byte[] decryptedData = null;

    private int maxDataSize = 0;

    private LinkedList<DatagramPacket> freePackets =
        new LinkedList<DatagramPacket>();

    private int createdPackets = 0;

    private LinkedList<DatagramPacket> packetQueue =
        new LinkedList<DatagramPacket>();

    private HashMap<Long, Source> sources = new HashMap<Long, Source>();

    private KnownAddresses knownAddresses = null;

    private boolean done = false;

    private VenueListener listener = null;

    public RTPListener(InetSocketAddress rtpAddress, VenueListener listener,
            boolean rtcpOnly, KnownAddresses knownAddresses)
            throws IOException {
        this(rtpAddress, new InetSocketAddress(
                rtpAddress.getAddress(), rtpAddress.getPort() + 1), listener,
                rtcpOnly, knownAddresses);
    }

    public RTPListener(InetSocketAddress rtpAddress,
            InetSocketAddress rtcpAddress, VenueListener listener,
            boolean rtcpOnly, KnownAddresses knownAddresses)
            throws IOException {
        if (!rtcpOnly) {
            rtpSocket = createSocket(rtpAddress);
        }
        rtcpSocket = createSocket(rtcpAddress);
        if (!rtcpOnly) {
            maxDataSize = Math.max(rtpSocket.getReceiveBufferSize(),
                rtcpSocket.getReceiveBufferSize());
        } else {
            maxDataSize = rtcpSocket.getReceiveBufferSize();
        }

        if (!rtcpOnly) {
            rtpListener = new SocketListener(this, rtpSocket);
        }
        rtcpListener = new SocketListener(this, rtcpSocket);
        this.listener = listener;
        this.knownAddresses = knownAddresses;
    }

    public void setEncryption(String encryption)
            throws UnsupportedEncryptionException {
        String type = DESCrypt.TYPE;
        String key = encryption;
        int index = encryption.indexOf('/');
        if (index != -1) {
            type = encryption.substring(0, index);
            key = encryption.substring(index + 1);
        }
        Crypt crypter = null;
        if (type.equalsIgnoreCase(DESCrypt.TYPE)) {
            crypter = new DESCrypt(key);
        } else if (type.equalsIgnoreCase(AESCrypt.TYPE)) {
            crypter = new AESCrypt(key);
        } else {
            throw new UnsupportedEncryptionException("Unknown encryption type"
                    + type);
        }
        this.encryption = new RTPCrypt(crypter);
    }

    private MulticastSocket createSocket(InetSocketAddress address)
            throws IOException {
        MulticastSocket socket = null;
        try {
            socket = new MulticastSocket(address);
        } catch (BindException e) {
            socket = new MulticastSocket(address.getPort());
        }
        if (address.getAddress().isMulticastAddress()) {
            socket.joinGroup(address.getAddress());
        }
        return socket;
    }

    protected DatagramPacket getFreePacket() {
        synchronized (freePackets) {
            if (freePackets.isEmpty()) {
                if (createdPackets >= MAX_PACKETS) {
                    while (freePackets.isEmpty() && !done) {
                        try {
                            freePackets.wait();
                        } catch (InterruptedException e) {
                            // Does Nothing
                        }
                    }
                } else {
                    byte[] data = new byte[maxDataSize];
                    createdPackets += 1;
                    return new DatagramPacket(data, data.length);
                }
            }
            if (!freePackets.isEmpty()) {
                return freePackets.removeFirst();
            }
            return null;
        }
    }

    private void freePacket(DatagramPacket packet) {
        synchronized (freePackets) {
            freePackets.addLast(packet);
            freePackets.notifyAll();
        }
    }

    protected void handlePacket(DatagramPacket packet) {
        synchronized (packetQueue) {
            packetQueue.addLast(packet);
            packetQueue.notifyAll();
        }
    }

    private DatagramPacket getNextPacket() {
        synchronized (packetQueue) {
            while (!done && packetQueue.isEmpty()) {
                try {
                    packetQueue.wait();
                } catch (InterruptedException e) {
                    // Do Nothing
                }
            }
            if (!done) {
                return packetQueue.removeFirst();
            }
            return null;
        }
    }

    public void run() {
        done = false;
        if (rtpListener != null) {
            rtpListener.start();
        }
        rtcpListener.start();
        while (!done) {
            DatagramPacket packet = getNextPacket();
            if ((packet != null) && !done && (rtcpSocket != null)) {
                if ((rtpSocket != null)
                        && (packet.getPort() == rtpSocket.getLocalPort())) {
                    processRTPPacket(packet);
                } else if (packet.getPort() == rtcpSocket.getLocalPort()) {
                    processRTCPPacket(packet);
                }
            }
        }
    }

    private Source getSource(long ssrc, InetAddress address) {

        synchronized (sources) {
            Source source = sources.get(ssrc);
            boolean sourceCreated = false;
            if (source == null) {
                source = new Source(ssrc, this, address);
                sourceCreated = true;
                sources.put(ssrc, source);
            } else if (!source.getAddress().equals(address)) {
                removeSource(source);
                source = new Source(ssrc, this, address);
                sourceCreated = true;
                sources.put(ssrc, source);
            }
            if (sourceCreated) {
                String addressName = null;
                if (knownAddresses != null) {
                    addressName = knownAddresses.getAddressName(address);
                    if (addressName != null) {
                        source.setAddressName(addressName);
                    }
                }
            }
            return source;
        }
    }

    private void processRTPPacket(DatagramPacket packet) {
        try {
            int length = packet.getLength();
            int offset = packet.getOffset();
            byte[] data = packet.getData();
            if (encryption != null) {
                int size = encryption.getDecryptOutputSize(packet.getLength());
                if ((decryptedData == null) || (decryptedData.length < size)) {
                    decryptedData = new byte[size];
                }
                length = encryption.decryptData(data, offset, length,
                        decryptedData, 0);
            }
            DatagramPacket newPacket = new DatagramPacket(data, offset, length,
                    packet.getSocketAddress());
            RTPHeader header = new RTPHeader(newPacket);
            if ((header.getVersion() == 2) && ((header.getPacketType() < 72)
                    || (header.getPacketType() > 76))) {
                long ssrc = header.getSsrc();
                Source source = getSource(ssrc, newPacket.getAddress());
                synchronized (source) {
                    long numPackets = source.getNumPackets();
                    if ((numPackets + 1) == MIN_PACKETS) {
                        listener.sourceJoining(source);
                    }
                    source.receiveRTPPacket(newPacket);
                }
            }
        } catch (Exception e) {
            // Do Nothing
        }
        freePacket(packet);
    }

    private void processRTCPPacket(DatagramPacket packet) {
        try {
            int length = packet.getLength();
            int offset = packet.getOffset();
            byte[] data = packet.getData();
            if (encryption != null) {
                int size = encryption.getDecryptOutputSize(packet.getLength());
                if ((decryptedData == null) || (decryptedData.length < size)) {
                    decryptedData = new byte[size];
                }
                length = encryption.decryptCtrl(data, offset, length,
                        decryptedData, 0);
            }
            DatagramPacket newPacket = new DatagramPacket(data, offset, length,
                    packet.getSocketAddress());
            RTCPHeader header = new RTCPHeader(newPacket);
            if (header.getVersion() == 2) {
                long ssrc = header.getSsrc();
                Source source = getSource(ssrc, newPacket.getAddress());
                synchronized (source) {
                    String cname = source.getCname();
                    long numPackets = source.getNumPackets();
                    if ((numPackets + 1) == MIN_PACKETS) {
                        listener.sourceJoining(source);
                        if (!cname.equals("")) {
                            listener.sourceCnameUpdated(source);
                        }
                    }
                    source.receiveRTCPPacket(newPacket);
                    if (cname.equals("")) {
                        String newCname = source.getCname();
                        if (source.getNumPackets() >= MIN_PACKETS) {
                            if (!newCname.equals("")) {
                                listener.sourceCnameUpdated(source);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Do Nothing
        }
        freePacket(packet);
    }

    protected void removeSource(Source source) {
        synchronized (sources) {
            sources.remove(source.getSsrc());
            if (source.getNumPackets() >= MIN_PACKETS) {
                listener.sourceLeaving(source);
            }
        }
    }

    public void close() {
        done = true;
        if (rtpListener != null) {
            rtpListener.close();
        }
        rtcpListener.close();
        if (rtpSocket != null) {
            rtpSocket.close();
        }
        rtcpSocket.close();
        synchronized (packetQueue) {
            packetQueue.notifyAll();
        }
    }

}
