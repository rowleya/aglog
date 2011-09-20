package com.googlecode.aglog;

import java.awt.event.ActionEvent;
import java.util.HashSet;

public class MeetingSource extends CNameSource {

    private int noSites = 0;

    private HashSet<String> cnames = new HashSet<String>();

    public MeetingSource(VenueListener listener) {
        super("MEETING", listener);
    }

    public void sourceJoined(Source source) {
        synchronized (cnames) {
            super.sourceJoined(source);
            String cname = source.getCname();
            if (!cnames.contains(cname)) {
                cnames.add(cname);
                noSites += 1;
            }
        }
    }

    public int getNoSites() {
        return noSites;
    }

    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        noSites = 0;
        cnames.clear();
    }
}
