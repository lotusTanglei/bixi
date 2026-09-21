package com.lotus.bixi.workflow.api.json;

/** A JSON string cannot be hashed faithfully if its UTF-16 contains an isolated surrogate. */
public class WorkflowInvalidUnicodeException extends IllegalArgumentException {
    public WorkflowInvalidUnicodeException() {
        super("JSON文本包含未配对的Unicode代理字符");
    }
}
