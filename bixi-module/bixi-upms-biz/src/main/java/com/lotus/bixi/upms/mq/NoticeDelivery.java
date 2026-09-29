package com.lotus.bixi.upms.mq;

import com.lotus.bixi.upms.api.dto.NoticeMessageDTO;

/** Delivers a published notice through the transport selected by the deployment mode. */
public interface NoticeDelivery {

    void deliver(NoticeMessageDTO message);
}
