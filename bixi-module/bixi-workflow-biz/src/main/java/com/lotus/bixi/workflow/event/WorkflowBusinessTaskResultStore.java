package com.lotus.bixi.workflow.event;

import com.lotus.bixi.workflow.api.event.WorkflowBusinessTaskResult;
import com.lotus.bixi.workflow.api.event.WorkflowCompensationResult;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;

/** Persists workflow business-task results before a receive execution advances. */
public interface WorkflowBusinessTaskResultStore {
    boolean recordBusinessResult(WorkflowEvent event, WorkflowBusinessTaskResult result);

    boolean recordCompensationResult(WorkflowEvent event, WorkflowCompensationResult result);

    /**
     * Record a result when the Flowable receive execution is no longer visible. A true
     * return means the durable state is already terminal (or was a late result) and the
     * caller may acknowledge the message without trying to reopen the process. A false
     * return means the operation still needs the live receive execution and must be retried.
     *
     * <p>The default keeps lightweight test adapters source compatible; the JDBC owner
     * implementation supplies the durable classification.</p>
     */
    default boolean recordBusinessResultWithoutExecution(WorkflowEvent event,
            WorkflowBusinessTaskResult result) {
        return false;
    }

    /** See {@link #recordBusinessResultWithoutExecution(WorkflowEvent, WorkflowBusinessTaskResult)}. */
    default boolean recordCompensationResultWithoutExecution(WorkflowEvent event,
            WorkflowCompensationResult result) {
        return false;
    }
}
