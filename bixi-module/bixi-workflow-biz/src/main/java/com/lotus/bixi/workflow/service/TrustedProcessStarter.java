package com.lotus.bixi.workflow.service;

import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;

/** Internal application contract for starts accepted from the durable workflow inbox. */
public interface TrustedProcessStarter {
    StartResult startTrusted(WorkflowEvent event);

    record StartResult(ProcessInstanceVO process, String rejectionCode) {
        public static StartResult started(ProcessInstanceVO process) {
            return new StartResult(process, null);
        }

        public static StartResult rejected(String rejectionCode) {
            return new StartResult(null, rejectionCode);
        }

        public boolean rejected() {
            return rejectionCode != null;
        }
    }
}
