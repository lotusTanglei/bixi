package com.lotus.bixi.upms.service.impl;

/** Publishes a refresh to other application instances when such transport exists. */
@FunctionalInterface
public interface NoticeSseBroadcaster {

    void publish(NoticeSseRefresh refresh);
}
