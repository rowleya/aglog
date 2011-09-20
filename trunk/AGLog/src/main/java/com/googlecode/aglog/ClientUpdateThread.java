package com.googlecode.aglog;

import com.googlecode.onevre.ag.types.server.Venue;

public class ClientUpdateThread extends Thread {

    // The default time between updates
    private static final float DEFAULT_LIFETIME = 10.0f;

    // The value to convert ms to seconds
    private static final int MS_TO_SECS = 1000;

    // The venue to update
    private Venue venue = null;

    // The connection to update
    private String connectionId = null;

    // The timeout before the next update
    private long timeout = 0;

    // True if the thread has finished
    private boolean done = false;

    /**
     * Creates a new ClientUpdateThread
     * @param venue The venue to update
     * @param connectionId The id of the connection
     */
    public ClientUpdateThread(Venue venue, String connectionId) {
        this.venue = venue;
        this.connectionId = connectionId;
        done = false;
        doUpdate();
        start();
    }

    private synchronized void doUpdate() {
        if (!done) {
            try {
                timeout = (long) (venue.updateLifetime(connectionId,
                        DEFAULT_LIFETIME) * MS_TO_SECS);
                if (timeout <= 0) {
                    timeout = (long) DEFAULT_LIFETIME;
                }
            } catch (Exception e) {
                System.err.println("Warning: updateLifetime failed: "
                        + e.getMessage());
                timeout = (long) DEFAULT_LIFETIME;
            }
        }
    }

    /**
     *
     * @see java.lang.Runnable#run()
     */
    public void run() {
        done = false;
        while (!done && (venue != null)) {
            try {
                sleep(timeout);
            } catch (InterruptedException e) {
                // Do Nothing
            }
            doUpdate();
        }
    }

    /**
     * Stops the updates
     *
     */
    public synchronized void close() {
        done = true;
        interrupt();
    }
}
