package com.resilientechnology.starandcar.controller.owner;

import com.resilientechnology.starandcar.record.MailMessageVO;
import com.resilientechnology.starandcar.service.owner.MailRelayService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("mail")
public class MailController {

    @Autowired
    MailRelayService mailRelayService;

    /**
     * Email endpoint: relays a message to the receiver's browser.
     * Nothing is written to the database - the message is queued in the
     * compressed per-user spool file only until the receiver's browser
     * confirms it has stored the message in its localStorage.
     */
    @PostMapping(value = "send", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MailRelayService.SendResult send(@Valid @RequestBody MailMessageVO mailMessageVO) {
        return mailRelayService.send(mailMessageVO);
    }

    /**
     * Keeps the receiver's browser on the line so incoming mail can be
     * pushed straight into its localStorage (queued mail is flushed on connect).
     */
    @GetMapping(value = "stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String email) {
        return mailRelayService.subscribe(email);
    }

    /**
     * The receiver's browser confirms a message now lives in its localStorage,
     * which lets the server prune it from the spool file.
     */
    @PostMapping("received")
    public void received(@RequestParam String email, @RequestParam String messageId) {
        mailRelayService.received(email, messageId);
    }
}
