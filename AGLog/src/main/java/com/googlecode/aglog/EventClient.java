package com.googlecode.aglog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.security.SecureRandom;
import java.text.NumberFormat;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.xml.sax.SAXException;

import com.googlecode.onevre.ag.types.EventDescription;
import com.googlecode.onevre.protocols.soap.common.SoapDeserializer;
import com.googlecode.onevre.security.AcceptAllTrustManager;

public class EventClient extends Thread {

    private EventListener listener;

    private boolean done = false;

    private SSLSocket sslsocket = null;

    private boolean firstMessageReceived = false;

    private SoapDeserializer soap = new SoapDeserializer();

    private DataInputStream input = null;

    private DataOutputStream output = null;

    private void sendString(DataOutputStream output, String string)
            throws IOException {
        output.writeInt(string.length());
        output.write(string.getBytes("UTF-8"));
        output.flush();
    }

    static{
        SoapDeserializer.mapType(EventDescription.class);
    }

    public EventClient(EventListener eventListener, String hostname, int port,
            final String connectionId, String groupId) {
        this.listener=eventListener;
        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, new TrustManager[]{
                    new AcceptAllTrustManager()}, new SecureRandom());
            SSLSocketFactory sslsocketfactory = sslContext.getSocketFactory();
            sslsocket = (SSLSocket) sslsocketfactory.createSocket(
                    hostname, port);
            InputStream inputstream = sslsocket.getInputStream();
            BufferedInputStream bufferedin =
                new BufferedInputStream(inputstream);
            input = new DataInputStream(bufferedin);

            OutputStream outputstream = sslsocket.getOutputStream();
            BufferedOutputStream buffered =
                new BufferedOutputStream(outputstream);
            output = new DataOutputStream(buffered);
            NumberFormat format = NumberFormat.getInstance();
            format.setMaximumFractionDigits(0);
            format.setMinimumIntegerDigits(2);
            String data = format.format(groupId.length()) + groupId;
            data += format.format(connectionId.length()) + connectionId;
            sendString(output, data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        String message = null;
        EventDescription event = null;
        while (!done) {
            try {
                int length = input.readInt();
                byte[] stringarray = new byte[length];
                input.readFully(stringarray);
                if (!firstMessageReceived) {
                    firstMessageReceived = true;
                } else {
                    message = new String(stringarray, "UTF-8");
                    event = (EventDescription) soap.deserialize(message);
                    listener.processEvent(event);
                }
            } catch (SAXException e) {
                System.err.println("SAX - Error parsing message "
                        + message);
            } catch (SocketException e) {
                if (sslsocket.isClosed()) {
                    if (!done) {
                        done = true;
                        listener.connectionClosed();
                    }
                } else {
                    e.printStackTrace();
                }
            } catch (EOFException e) {
                if (!done) {
                    done = true;
                    listener.connectionClosed();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Closes the connection to the server
     */
    public void close() {
        done = true;
        try {
            sslsocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
