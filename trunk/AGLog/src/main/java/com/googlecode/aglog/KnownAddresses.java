package com.googlecode.aglog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Properties;

public class KnownAddresses {

    private final File knownAddressFile;

    private long lastModifiedTime = -1;

    private Properties knownAddresses = new Properties();

    public KnownAddresses(String knownAddressFileName) throws IOException {
        knownAddressFile = new File(knownAddressFileName);
        synchronized (knownAddressFile) {
            readFile();
        }
    }

    private void readFile() throws IOException {
        if (knownAddressFile.lastModified() != lastModifiedTime) {
            knownAddresses.clear();
            knownAddresses.load(new FileInputStream(knownAddressFile));
            lastModifiedTime = knownAddressFile.lastModified();
        }
    }

    public String getAddressName(InetAddress address) {
        return getAddressName(address.getHostAddress());
    }

    public String getAddressName(String address) {
        synchronized (knownAddressFile) {
            try {
                readFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return knownAddresses.getProperty(address);
        }
    }

    public void setAddressName(InetAddress address, String name)
            throws IOException {
        setAddressName(address.getHostAddress(), name);
    }

    public void setAddressName(String address, String name) throws IOException {
        synchronized (knownAddressFile) {
            readFile();
            knownAddresses.setProperty(address, name);
            knownAddresses.store(new FileOutputStream(knownAddressFile), null);
            lastModifiedTime = knownAddressFile.lastModified();
        }
    }
}
