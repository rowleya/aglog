package com.googlecode.aglog;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

import org.xml.sax.SAXException;

import com.googlecode.onevre.ag.types.Capability;
import com.googlecode.onevre.ag.types.ClientProfile;
import com.googlecode.onevre.ag.types.ConnectionDescription;
import com.googlecode.onevre.ag.types.EventDescription;
import com.googlecode.onevre.ag.types.StreamDescription;
import com.googlecode.onevre.ag.types.VenueState;
import com.googlecode.onevre.ag.types.network.NetworkLocation;
import com.googlecode.onevre.ag.types.server.Venue;
import com.googlecode.onevre.types.soap.exceptions.SoapException;
import com.googlecode.vicovre.media.rtp.UnsupportedEncryptionException;

public class VenueListener implements EventListener {

    private final String connectionId = UUID.randomUUID().toString();

    private HashMap<String, NetworkLocation> streams =
        new HashMap<String, NetworkLocation>();

    private HashMap<String, CNameSource> cnameSources =
        new HashMap<String, CNameSource>();

    private HashMap<String, Long> agtkParticipantEntered =
        new HashMap<String, Long>();

    private MeetingSource meetingSource = new MeetingSource(this);

    private Venue venue = null;

    private String server = null;

    private String venueName = null;

    private EventClient eventClient = null;

    private VenueState venueState = null;

    private boolean started = false;

    private AGLog log = null;

    private String uri = null;

    private boolean rtcpOnly = true;

    private ClientProfile profile = new ClientProfile();

    private KnownAddresses knownAddresses = null;

    private RTPListenerManager rtpManager = null;

    public VenueListener(AGLog log, ConnectionDescription connectionDescription,
            boolean rtcpOnly, KnownAddresses knownAddresses,
            RTPListenerManager rtpManager)
            throws NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, IOException, SAXException,
            UnsupportedEncryptionException, SoapException {
        this.log = log;
        this.uri = connectionDescription.getUri();
        this.rtcpOnly = rtcpOnly;
        this.knownAddresses = knownAddresses;
        this.rtpManager = rtpManager;
        URL url = new URL(uri);
        this.server = url.getHost();
        connectToVenue();
    }

    private void connectToVenue() throws NoSuchMethodException,
            IllegalAccessException, InvocationTargetException, IOException,
            SAXException, UnsupportedEncryptionException, SoapException {
        profile.setDistinguishedName(AGLog.class.getName());
        venue = new Venue(uri, false);
        venueState = venue.getState();
        venueName = venueState.getName();
        System.err.println("Connected to venue " + venueName);
        StreamDescription[] streams = venue.getStreams();
        for (int i = 0; i < streams.length; i++) {
            addStream(streams[i]);
        }
        for (ClientProfile client : venueState.getClients()) {
            agtkParticipantEntered.put(client.getConnectionId(),
                    System.currentTimeMillis());
        }
        String[] location = venueState.getEventLocation().split(":");
        String hostname = location[0];
        int port = Integer.parseInt(location[1]);
        eventClient = new EventClient(this, hostname, port, connectionId,
                venueState.getUniqueId());
    }

    private void addStream(StreamDescription stream) throws IOException,
            UnsupportedEncryptionException {
        NetworkLocation location = stream.getLocation();
        String encryptionKey = null;
        if (stream.getEncryptionFlag() != 0) {
            encryptionKey = stream.getEncryptionKey();
        }
        rtpManager.addListener(location, this, rtcpOnly, knownAddresses,
                encryptionKey);
        synchronized (streams) {
            NetworkLocation oldLocation = streams.get(stream.getId());
            if (oldLocation != null) {
                rtpManager.stopListener(location, this);
            }
            streams.put(stream.getId(), location);
        }
    }

    public void cnameSourceLeaving(CNameSource source) {
        Date joined = new Date(source.getFirstSourceJoinedTime());
        Date left = new Date(source.getLastSourceLeftTime());
        if (source instanceof MeetingSource) {
            log.log("MEETING", joined.toString(), left.toString(),
                String.valueOf(source.getFirstSourceJoinedTime()),
                String.valueOf(source.getLastSourceLeftTime()),
                venueName, String.valueOf(source.getNoSources()),
                String.valueOf(((MeetingSource) source).getNoSites()));
        } else {
            log.log("SITE", joined.toString(), left.toString(),
                String.valueOf(source.getFirstSourceJoinedTime()),
                String.valueOf(source.getLastSourceLeftTime()),
                venueName, source.getCName(),
                String.valueOf(source.getNoSources()));
        }
    }

    public void sourceJoining(Source source) {
        // Does Nothing
    }

    public void sourceCnameUpdated(Source source) {
        String cname = source.getCname();
        if ((cname != null) && !cname.equals("")) {
            CNameSource cnameSource = cnameSources.get(cname);
            if (cnameSource == null) {
                cnameSource = new CNameSource(cname, this);
                cnameSources.put(cname, cnameSource);
            }
            cnameSource.sourceJoined(source);
            meetingSource.sourceJoined(source);
        }
    }

    public void sourceLeaving(Source source) {
        Date joined = new Date(source.getStartTime());
        Date left = new Date(source.getLastPacketTime());
        log.log("SOURCE", joined.toString(), left.toString(),
                String.valueOf(source.getStartTime()),
                String.valueOf(source.getLastPacketTime()),
                venueName, source.getAddressName(),
                String.valueOf(source.getSsrc()),
                source.getCname(), source.getName(), source.getNote(),
                source.getEmail(), source.getTool());
        String cname = source.getCname();
        if ((cname != null) && !cname.equals("")) {
            meetingSource.sourceLeft(source);
            CNameSource cnameSource = cnameSources.get(cname);
            if (cnameSource != null) {
                cnameSource.sourceLeft(source);
            }
        }
    }

    public void processEvent(EventDescription event) {
        if (event.getEventType().equalsIgnoreCase(Event.ADD_STREAM)) {
            StreamDescription stream = (StreamDescription) event.getData();
            boolean isBeacon = false;
            for (Capability capability : stream.getCapability()) {
                if (capability.getType().equalsIgnoreCase("Beacon")) {
                    isBeacon = true;
                    break;
                }
            }
            if (!isBeacon) {
                try {
                    addStream(stream);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (event.getEventType().equalsIgnoreCase(Event.REMOVE_STREAM)) {
            StreamDescription stream = (StreamDescription) event.getData();
            synchronized (streams) {
                NetworkLocation location = streams.remove(stream.getId());
                rtpManager.stopListener(location, this);
            }
        } else if (event.getEventType().equalsIgnoreCase(Event.ENTER)) {
            ClientProfile userProfile = (ClientProfile) event.getData();
            agtkParticipantEntered.put(userProfile.getConnectionId(),
                    System.currentTimeMillis());
        } else if (event.getEventType().equalsIgnoreCase(Event.EXIT)) {
            Date left = new Date();
            ClientProfile userProfile = (ClientProfile) event.getData();
            String id = userProfile.getConnectionId();
            if (agtkParticipantEntered.containsKey(id)) {
                Date joined = new Date(agtkParticipantEntered.get(id));
                log.log("AGTK", joined.toString(), left.toString(),
                        String.valueOf(joined.getTime()),
                        String.valueOf(left.getTime()), venueName, server, id,
                        userProfile.getName(), userProfile.getEmail(),
                        userProfile.getLocation(), userProfile.getPhoneNumber(),
                        userProfile.getProfileType());
            }
        }
    }

    public void connectionClosed() {
        eventClient.close();
        boolean connected = false;
        boolean messagePrinted = false;
        while (!connected && started) {
            try {
                if (!messagePrinted) {
                    System.err.println("Trying to reconnect to venue "
                            + venueName);
                    messagePrinted = true;
                }
                connectToVenue();
                eventClient.start();
                connected = true;
            } catch (Exception e) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e1) {
                    // Do Nothing
                }
            }
        }
    }

    public void start() {
        if (!started) {
            started = true;
            eventClient.start();
        }
    }

    public void stop() {
        if (started) {
            started = false;
            eventClient.close();
        }
    }
}
