package com.googlecode.aglog;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;

import javax.swing.Timer;

import com.googlecode.vicovre.media.rtp.RTCPHeader;
import com.googlecode.vicovre.media.rtp.RTCPSDES;

public class Source implements ActionListener {

    private static final int TIMEOUT = 120000;

    private static final int INITIAL_TIMEOUT = 30000;

    private static final int INITIAL_PACKETS = 3;

    private final Integer timeSync = new Integer(0);

    private boolean byeReceived = false;

    private long numPackets = 0;

    private long ssrc = 0;

    private InetAddress address = null;

    private String addressName = null;

    private long startTime = 0;

    private long lastPacketTime = 0;

    private String cname = "";

    private String name = "";

    private String email = "";

    private String phone = "";

    private String location = "";

    private String tool = "";

    private String note = "";

    private RTPListener listener = null;

    private Timer timer = null;

    public Source(long ssrc, RTPListener listener, InetAddress address) {
        this.ssrc = ssrc;
        this.listener = listener;
        this.address = address;
        this.addressName = address.getHostAddress();
        this.startTime = System.currentTimeMillis();
        timer = new Timer(INITIAL_TIMEOUT, this);
        timer.start();
    }

    public Source(long ssrc, RTPListener listener, InetAddress address,
            Source source) {
        this(ssrc, listener, address);
        this.cname = source.cname;
        this.name = source.name;
        this.email = source.email;
        this.phone = source.phone;
        this.location = source.location;
        this.tool = source.tool;
        this.note = source.note;
    }

    private void handleTimer() {
        synchronized (timeSync) {
            ++numPackets;
            if (numPackets != INITIAL_PACKETS) {
                timer.restart();
            } else {
                timer.stop();
                timer.setDelay(TIMEOUT);
                timer.start();
            }
            lastPacketTime = System.currentTimeMillis();
        }
    }

    public void receiveRTPPacket(DatagramPacket packet) throws IOException {
        handleTimer();
    }

    public void receiveRTCPPacket(DatagramPacket packet) throws IOException {
        handleTimer();
        int offset = packet.getOffset();
        int read = 0;

        // Go through all the attached RTCP packets and handle them too
        try {
            while (read < packet.getLength()) {
                RTCPHeader header = new RTCPHeader(packet.getData(),
                        offset, packet.getLength() - read);
                int length = (header.getLength() + 1) * 4;
                read += RTCPHeader.SIZE;
                offset += RTCPHeader.SIZE;
                processSubpacket(header, packet.getData(), offset);
                offset += length - RTCPHeader.SIZE;
                read += length - RTCPHeader.SIZE;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processSubpacket(RTCPHeader packetHeader, byte[] packet,
            int offset) {

        switch (packetHeader.getPacketType()) {

        // Ignore RR packets
        case RTCPHeader.PT_RR:
            break;

        case RTCPHeader.PT_BYE:
            byeReceived = true;
            timer.stop();
            listener.removeSource(this);
            break;

        case RTCPHeader.PT_SDES:
            try {
                RTCPSDES sdes = new RTCPSDES(packet, offset,
                        (packetHeader.getLength() + 1) * 4);
                if (sdes.getCname() != null) {
                    cname = sdes.getCname();
                }
                if (sdes.getName() != null) {
                    name = sdes.getName();
                }
                if (sdes.getEmail() != null) {
                    email = sdes.getEmail();
                }
                if (sdes.getPhone() != null) {
                    phone = sdes.getPhone();
                }
                if (sdes.getLocation() != null) {
                    location = sdes.getLocation();
                }
                if (sdes.getTool() != null) {
                    tool = sdes.getTool();
                }
                if (sdes.getNote() != null) {
                    note = sdes.getNote();
                }
            } catch (StringIndexOutOfBoundsException e) {
                // Do Nothing
            }
        break;

        // Ignore SR Packets
        case RTCPHeader.PT_SR:
            break;

        // Ignore APP Packets
        case RTCPHeader.PT_APP:
            break;

        // Ignore anything you don't know about
        default:
            break;
        }
    }

    public void actionPerformed(ActionEvent e) {
        timer.stop();
        listener.removeSource(this);
    }

    public boolean isByeReceived() {
        return byeReceived;
    }

    public long getNumPackets() {
        return numPackets;
    }

    public long getSsrc() {
        return ssrc;
    }

    public String getCname() {
        return cname;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getLocation() {
        return location;
    }

    public String getTool() {
        return tool;
    }

    public String getNote() {
        return note;
    }

    public InetAddress getAddress() {
        return address;
    }

    public String getAddressName() {
        return addressName;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getLastPacketTime() {
        return lastPacketTime;
    }

    public void setAddressName(String addressName) {
        this.addressName = addressName;
    }
}
