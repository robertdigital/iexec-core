package com.iexec.core.workflow;

import com.iexec.core.task.TaskStatus;

import static com.iexec.core.task.TaskStatus.*;


public class TaskWorkflow extends Workflow<TaskStatus> {

    private static TaskWorkflow instance;

    public static synchronized TaskWorkflow getInstance() {
        if (instance == null) {
            instance = new TaskWorkflow();
        }
        return instance;
    }

    private TaskWorkflow() {
        super();

        // This is where the whole workflow is defined
        addTransition(INITIALIZED, RUNNING);
        addTransition(RUNNING, COMPUTED);
        addTransition(COMPUTED, CONSENSUS_REACHED);
        addTransition(CONSENSUS_REACHED, REVEALED);
        addTransition(REVEALED, UPLOAD_RESULT_REQUESTED);
        addTransition(COMPUTED, UPLOAD_RESULT_REQUESTED);
        addTransition(UPLOAD_RESULT_REQUESTED, RESULT_UPLOADING);
        addTransition(RESULT_UPLOADING, RESULT_UPLOADED);
        addTransition(RESULT_UPLOADED, COMPLETED);
        addTransition(RESULT_UPLOADING, ERROR);
    }
}
