package com.googlecode.aglog;

import com.googlecode.onevre.ag.types.EventDescription;

public interface EventListener {

    void processEvent(EventDescription event);

    void connectionClosed();
}
