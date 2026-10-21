package org.jaldrishti.adapter.rest;

import org.jaldrishti.application.port.out.MessageLogQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only message log for the operator console: dispatched duty messages and CAP alerts of a run.
 */
@RestController
@RequestMapping("/api/v1")
public class MessageLogController {

    private final MessageLogQuery messageLogQuery;

    public MessageLogController(MessageLogQuery messageLogQuery) {
        this.messageLogQuery = messageLogQuery;
    }

    @GetMapping("/messages")
    public List<MessageLogQuery.MessageLogView> messages(@RequestParam("runId") String runId) {
        return messageLogQuery.findMessagesByRunId(runId);
    }

    @GetMapping("/cap-messages")
    public List<MessageLogQuery.CapMessageView> capMessages(@RequestParam("runId") String runId) {
        return messageLogQuery.findCapMessagesByRunId(runId);
    }
}
