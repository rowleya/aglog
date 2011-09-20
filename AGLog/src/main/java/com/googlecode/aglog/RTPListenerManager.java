package com.googlecode.aglog;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;

import com.googlecode.onevre.ag.types.network.NetworkLocation;
import com.googlecode.vicovre.media.rtp.UnsupportedEncryptionException;

public class RTPListenerManager {

    private HashMap<NetworkLocation, RTPListener> listeners =
        new HashMap<NetworkLocation, RTPListener>();

    private HashMap<NetworkLocation, HashSet<VenueListener>> venueListeners =
        new HashMap<NetworkLocation, HashSet<VenueListener>>();

    private boolean started = false;

    public void addListener(NetworkLocation location,
            VenueListener venueListener, boolean rtcpOnly,
            KnownAddresses knownAddresses, String encryptionKey)
            throws IOException, UnsupportedEncryptionException {
        synchronized (listeners) {
            if (!listeners.containsKey(location)) {
                System.err.println("Listening on " + location);
                InetAddress address = InetAddress.getByName(location.getHost());
                int port = location.getPort();
                RTPListener listener = new RTPListener(
                        new InetSocketAddress(address, port), venueListener,
                        rtcpOnly, knownAddresses);
                if (encryptionKey != null) {
                    listener.setEncryption(encryptionKey);
                }
                listeners.put(location, listener);
                if (started) {
                    listener.start();
                }
            }
            HashSet<VenueListener> locationVenueListeners =
                venueListeners.get(location);
            if (locationVenueListeners == null) {
                locationVenueListeners = new HashSet<VenueListener>();
                venueListeners.put(location, locationVenueListeners);
            }
            if (!locationVenueListeners.contains(venueListener)) {
                locationVenueListeners.add(venueListener);
            }
        }
    }

    public void stopListener(NetworkLocation location,
            VenueListener venueListener) {
        HashSet<VenueListener> locationVenueListeners =
            venueListeners.get(location);
        if (locationVenueListeners != null) {
            locationVenueListeners.remove(venueListener);
            if (locationVenueListeners.isEmpty()) {
                RTPListener listener = listeners.remove(location);
                if (listener != null) {
                    listener.close();
                    System.err.println("Stopped listening on " + location);
                }
            }
        }
    }

    public void start() {
        synchronized (listeners) {
            for (RTPListener listener : listeners.values()) {
                listener.start();
            }
            started = true;
        }
    }

    public void stop() {
        synchronized (listeners) {
            for (RTPListener listener : listeners.values()) {
                listener.close();
            }
            started = false;
        }
    }

}
