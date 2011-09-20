package com.googlecode.aglog;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;

import javax.swing.Timer;

public class CNameSource implements ActionListener {

    private static final int LEAVE_TIMEOUT = 10000;

    private String cname = null;

    private int noSources = 0;

    private long firstSourceJoinedTime = 0;

    private long lastSourceLeftTime = 0;

    private HashSet<Long> sources = new HashSet<Long>();

    private Timer timer = null;

    private VenueListener listener = null;

    public CNameSource(String cname, VenueListener listener) {
        this.cname = cname;
        this.listener = listener;
    }

    protected VenueListener getListener() {
        return listener;
    }

    public void sourceJoined(Source source) {
        synchronized (sources) {
            if (timer != null) {
                timer.stop();
                timer = null;
            }
            long ssrc = source.getSsrc();
            if (!sources.contains(ssrc)) {
                noSources += 1;
                long startTime = source.getStartTime();
                if (sources.isEmpty() || (startTime < firstSourceJoinedTime)) {
                    firstSourceJoinedTime = startTime;
                }
                sources.add(ssrc);
            }
        }
    }

    public void sourceLeft(Source source) {
        synchronized (sources) {
            long ssrc = source.getSsrc();
            if (sources.contains(ssrc)) {
                sources.remove(ssrc);
                long endTime = source.getLastPacketTime();
                if (endTime > lastSourceLeftTime) {
                    lastSourceLeftTime = endTime;
                }
                if (sources.isEmpty()) {
                    timer = new Timer(LEAVE_TIMEOUT, this);
                    timer.start();
                }
            }
        }
    }

    public void actionPerformed(ActionEvent e) {
        synchronized (sources) {
            timer.stop();
            listener.cnameSourceLeaving(this);
            noSources = 0;
        }
    }

    public String getCName() {
        return cname;
    }

    public long getFirstSourceJoinedTime() {
        return firstSourceJoinedTime;
    }

    public long getLastSourceLeftTime() {
        return lastSourceLeftTime;
    }

    public int getNoSources() {
        return noSources;
    }
}
