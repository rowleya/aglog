package com.googlecode.aglog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Vector;

import org.xml.sax.SAXException;

import com.googlecode.onevre.ag.types.ConnectionDescription;
import com.googlecode.onevre.ag.types.VenueState;
import com.googlecode.onevre.ag.types.server.Venue;
import com.googlecode.onevre.ag.types.server.VenueServer;
import com.googlecode.onevre.types.soap.exceptions.SoapException;
import com.googlecode.vicovre.media.rtp.UnsupportedEncryptionException;

public class AGLog {

    private Vector<VenueListener> listeners = new Vector<VenueListener>();

    private PrintStream logWriter = System.out;

    private RTPListenerManager rtpManager = new RTPListenerManager();

    private class DoShutdown extends Thread {

        public void run() {
            synchronized (listeners) {
                for (VenueListener listener : listeners) {
                    listener.stop();
                }
            }
        }

    }

    public AGLog(String logFile)
            throws FileNotFoundException {
        boolean exists = false;
        if (logFile != null) {
            File logFileFile = new File(logFile);
            exists = logFileFile.exists();
            FileOutputStream outputStream = new FileOutputStream(logFileFile,
                    true);
            logWriter = new PrintStream(outputStream, true);
        }
        if (!exists) {
            log("SOURCE", "Joined", "Left", "Joined (ms)",
                    "Left (ms)", "Venue", "IP", "SSRC", "CName", "Name", "Note",
                    "Email", "Tool");
            log("SITE", "Joined", "Left", "Joined (ms)",
                    "Left (ms)", "Venue", "CName", "No. Sources");
            log("MEETING", "Started", "Finished", "Started (ms)",
                    "Finished (ms)", "Venue", "No. Sources", "No. Sites");
            log("AGTK", "Joined", "Left", "Joined (ms)", "Left (ms)",
                    "Venue", "Server", "Connection ID", "Name", "Email",
                    "Location", "Phone Number", "Profile Type");
        }
    }

    public void listenToVenue(String venue, boolean rtcpOnly,
            KnownAddresses knownAddresses)
            throws NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, IOException, SAXException,
            UnsupportedEncryptionException, SoapException {
        URL url = null;
        try {
            url = new URL(venue);
        } catch (MalformedURLException e) {
            url = new URL("https://" + venue);
        }
        int port = url.getPort();
        if (port == -1) {
            port = 8000;
        }
        String protocol = url.getProtocol();
        if (protocol == null || protocol.equals("")) {
            protocol = "https";
        }
        String venueUrl = protocol + "://"
            + url.getHost() + ":" + port + url.getPath();
        ConnectionDescription connectionDescription =
            new ConnectionDescription();
        connectionDescription.setUri(venueUrl);
        VenueListener listener = new VenueListener(this, connectionDescription,
                rtcpOnly, knownAddresses, rtpManager);
        Runtime.getRuntime().addShutdownHook(new DoShutdown());
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void listenToServer(String server, boolean rtcpOnly,
            KnownAddresses knownAddresses)
            throws Exception {
        URL url = null;
        try {
            url = new URL(server);
        } catch (MalformedURLException e) {
            url = new URL("https://" + server);
        }
        int port = url.getPort();
        if (port == -1) {
            port = 8000;
        }
        String protocol = url.getProtocol();
        if (protocol == null || protocol.equals("")) {
            protocol = "https";
        }
        String serverUrl = protocol + "://"
            + url.getHost() + ":" + port + url.getPath();
        if (!serverUrl.endsWith("/VenueServer")) {
            serverUrl += "/VenueServer";
        }
        VenueServer venueServer = new VenueServer(serverUrl);
        ConnectionDescription[] venues = venueServer.getVenues(null);
        for (int i = 0; i < venues.length; i++) {
            VenueListener listener = new VenueListener(this, venues[i],
                    rtcpOnly, knownAddresses, rtpManager);
            listeners.add(listener);
        }
    }

    public void listServer(String server) throws Exception {
        URL url = null;
        try {
            url = new URL(server);
        } catch (MalformedURLException e) {
            url = new URL("https://" + server);
        }
        int port = url.getPort();
        if (port == -1) {
            port = 8000;
        }
        String protocol = url.getProtocol();
        if (protocol == null || protocol.equals("")) {
            protocol = "https";
        }
        String serverUrl = protocol + "://"
            + url.getHost() + ":" + port + url.getPath();
        if (!serverUrl.endsWith("/VenueServer")) {
            serverUrl += "/VenueServer";
        }
        VenueServer venueServer = new VenueServer(serverUrl);
        ConnectionDescription[] venues = venueServer.getVenues(null);
        for (int i = 0; i < venues.length; i++) {
            System.err.println(venues[i].getName() + ": " + venues[i].getUri());
        }
    }

    public void listVenue(String venue) throws Exception {
        URL url = null;
        try {
            url = new URL(venue);
        } catch (MalformedURLException e) {
            url = new URL("https://" + venue);
        }
        int port = url.getPort();
        if (port == -1) {
            port = 8000;
        }
        String protocol = url.getProtocol();
        if (protocol == null || protocol.equals("")) {
            protocol = "https";
        }
        String venueUrl = protocol + "://"
            + url.getHost() + ":" + port + url.getPath();
        Venue v = new Venue(venueUrl, false);
        VenueState state = v.getState();
        System.err.println(state.getName() + ": " + state.getUri());
    }

    public void log(String... parts) {
        synchronized (this) {
            for (String part : parts) {
                logWriter.print(part + ",");
            }
            logWriter.println();
        }
    }

    public void start() {
        Runtime.getRuntime().addShutdownHook(new DoShutdown());
        synchronized (listeners) {
            for (VenueListener listener : listeners) {
                listener.start();
            }
        }
        rtpManager.start();
    }

    public static void printUsage() {
        System.err.println(AGLog.class.getName()
                + "[-v <venue>] [-s <server] [-r] [-f <file>] [-k <file>]"
                + " [-c <file>]");
        System.err.println("    -v <venue>  Add a venue to log");
        System.err.println("    -s <server> Add a server to log");
        System.err.println("    -l          List venues then exit");
        System.err.println("    -r          Log rtp data as well");
        System.err.println("    -f <file>   File to log use in");
        System.err.println("    -k <file>   File of known addresses");
        System.err.println("    -c <file>   The configuration file");
    }

    public static void main(String[] args) {
        Vector<String> venues = new Vector<String>();
        Vector<String> servers = new Vector<String>();
        boolean list = false;
        Boolean rtcpOnly = null;
        String logFile = null;
        KnownAddresses knownAddresses = null;

        try {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-v")) {
                    venues.add(args[i + 1]);
                    i++;
                } else if (args[i].equals("-s")) {
                    servers.add(args[i + 1]);
                    i++;
                } else if (args[i].equals("-l")) {
                    list = true;
                } else if (args[i].equals("-r")) {
                    rtcpOnly = false;
                } else if (args[i].equals("-f")) {
                    logFile = args[i + 1];
                    i++;
                } else if (args[i].equals("-k")) {
                    knownAddresses = new KnownAddresses(args[i + 1]);
                    i++;
                } else if (args[i].equals("-c")) {
                    Config config = new Config(args[i + 1], "aglog");
                    String[] configVenues = config.getParameters("venue");
                    for (String venue : configVenues) {
                        venues.add(venue);
                    }
                    String[] configServers = config.getParameters("server");
                    for (String server : configServers) {
                        servers.add(server);
                    }
                    if (rtcpOnly == null) {
                        rtcpOnly = !Boolean.parseBoolean(
                            config.getParameter("logRtpData",
                                    String.valueOf(rtcpOnly)));
                    }
                    if (logFile == null) {
                        logFile = config.getParameter("logFile", null);
                    }
                    if (knownAddresses == null) {
                        String knownAddressesFile =
                            config.getParameter("knownAddressesFile", null);
                        if (knownAddressesFile != null) {
                            knownAddresses = new KnownAddresses(
                                    knownAddressesFile);
                        }
                    }
                    i++;
                }
            }

            if (rtcpOnly == null) {
                rtcpOnly = true;
            }

            if (venues.isEmpty() && servers.isEmpty()) {
                printUsage();
            } else {
                AGLog log = new AGLog(logFile);
                for (String venue : venues) {
                    if (!list) {
                        log.listenToVenue(venue, rtcpOnly, knownAddresses);
                    } else {
                        log.listVenue(venue);
                    }
                }
                for (String server : servers) {
                    if (!list) {
                        log.listenToServer(server, rtcpOnly, knownAddresses);
                    } else {
                        log.listServer(server);
                    }
                }
                log.start();
            }
        } catch (Exception e) {
            System.err.println("There was an error starting the log:");
            e.printStackTrace();
        }
    }
}
