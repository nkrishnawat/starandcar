package com.resilientechnology.starandcar.controller.owner;

import com.resilientechnology.starandcar.entity.StarMailDevice;
import com.resilientechnology.starandcar.record.MailMessageVO;
import com.resilientechnology.starandcar.record.StarMailDeviceVO;
import com.resilientechnology.starandcar.record.StarMailRegisterRequest;
import com.resilientechnology.starandcar.repository.notification.StarMailRepository;
import com.resilientechnology.starandcar.service.owner.MailRelayService;
import com.resilientechnology.starandcar.service.owner.StarMailSpoolService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * STARMail endpoints.
 *
 * <p>Everything here is addressed by MAC address / DeviceID / MachineID. No endpoint accepts
 * or returns an e-mail address: the registered address lives in MariaDB and is used only for
 * the independent carbon-copy leg.</p>
 */
@RestController
@RequestMapping("mail")
public class MailController {

    @Autowired
    MailRelayService mailRelayService;

    @Autowired
    StarMailRepository starMailRepository;

    @Autowired
    StarMailSpoolService spoolService;

    /**
     * Send a STARMail message to a device id. The message is queued in the STARMail folder on
     * the server so it reaches the recipient's browser (and localStorage) as soon as that
     * device is online. In parallel and independently, a copy may be e-mailed to the address
     * registered for that device - but delivery never depends on it.
     */
    @PostMapping(value = "send", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MailRelayService.SendResult send(@Valid @RequestBody MailMessageVO mailMessageVO) {
        return mailRelayService.send(mailMessageVO);
    }

    /**
     * Keeps the receiver's browser on the line so incoming mail can be pushed straight into
     * its localStorage (anything already queued is flushed on connect).
     */
    @GetMapping(value = "stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String deviceId) {
        return mailRelayService.subscribe(deviceId);
    }

    /**
     * Polling fallback: returns every message waiting for this device. Guarantees delivery
     * even when Server-Sent Events cannot get through (proxy buffering, blocked stream).
     */
    @GetMapping(value = "inbox", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MailMessageVO> inbox(@RequestParam String deviceId) {
        return mailRelayService.inbox(deviceId);
    }

    /**
     * The receiver's browser confirms a message now lives in its localStorage, which lets the
     * server clear the message body.
     */
    @PostMapping("received")
    public void received(@RequestParam String deviceId, @RequestParam String messageId) {
        mailRelayService.received(deviceId, messageId);
    }

    /**
     * Address book: device ids and labels only. The registered e-mail addresses held in
     * MariaDB are deliberately never returned.
     */
    @GetMapping(value = "devices", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<StarMailDeviceVO> devices() {
        return starMailRepository.listDevices().stream()
                .map(StarMailDeviceVO::from)
                .toList();
    }

    /**
     * Registers this browser's device id so it can be reached in STARMail. The e-mail is
     * stored server-side in MariaDB for the carbon copy and is never echoed back.
     */
    @PostMapping(value = "device/register", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public StarMailDeviceVO register(@Valid @RequestBody StarMailRegisterRequest request) {
        starMailRepository.upsertDevice(request.deviceId(), request.deviceLabel(), request.registeredEmail());
        return starMailRepository.findDevice(request.deviceId())
                .map(StarMailDeviceVO::from)
                .orElseGet(() -> new StarMailDeviceVO(request.deviceId(), request.deviceLabel(), false));
    }

    /**
     * Resolve a single device, e.g. to show who a listing owner is before composing.
     * Returns the device id and label only.
     */
    @GetMapping(value = "device", produces = MediaType.APPLICATION_JSON_VALUE)
    public StarMailDeviceVO device(@RequestParam String deviceId) {
        return starMailRepository.findDevice(deviceId)
                .map(StarMailDeviceVO::from)
                .orElseGet(() -> new StarMailDeviceVO(deviceId, null, false));
    }

    /**
     * Delivery status for one message, so the sender can see whether the parallel external
     * e-mail copy went out. Read from the STARMail folder; never required for delivery.
     */
    @GetMapping(value = "status", produces = MediaType.APPLICATION_JSON_VALUE)
    public MailMessageVO status(@RequestParam String messageId) {
        return spoolService.findByMessageId(messageId);
    }
}
