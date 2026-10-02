package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.record.MailMessageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The STARMail folder queue - the core of the requirement.
 *
 * <p>A message that cannot be pushed to the receiver's browser must be kept in a Linux folder
 * until that device is turned on and its browser confirms the message is in localStorage.
 * These tests run against a real directory to prove the copy genuinely lands on disk.</p>
 */
class StarMailSpoolServiceTest {

    private static final String DEVICE = "02:bb:55:66:77:88";

    @TempDir
    Path spoolDir;

    private StarMailSpoolService spool;

    @BeforeEach
    void setUp() {
        spool = new StarMailSpoolService();
        ReflectionTestUtils.setField(spool, "spoolDir", spoolDir.toString());
    }

    private MailMessageVO message(String messageId, String body) {
        return MailMessageVO.builder()
                .messageId(messageId)
                .fromDeviceId("02:aa:11:22:33:44")
                .toDeviceId(DEVICE)
                .subject("Hello")
                .body(body)
                .sentAt("2026-10-02 12:00")
                .build();
    }

    @Test
    void aMessageIsWrittenToTheLinuxFolderWhenTheReceiverCannotBeReached() throws Exception {
        spool.append(DEVICE, message("m1", "queued while you were out"));

        Path queue = spool.spoolFile(DEVICE);
        assertThat(queue).exists();
        assertThat(queue.toString()).endsWith(".gz");
        // exactly one compressed queue file per device
        assertThat(Files.list(spoolDir).count()).isEqualTo(1);
    }

    @Test
    void queuedMessagesAreStillThereWhenTheDeviceComesBackOnline() {
        spool.append(DEVICE, message("m1", "first"));
        spool.append(DEVICE, message("m2", "second"));

        // device turned on -> its browser pulls the queue
        List<MailMessageVO> queued = spool.readAll(DEVICE);

        assertThat(queued).hasSize(2);
        assertThat(queued).extracting(MailMessageVO::getMessageId).containsExactly("m1", "m2");
        assertThat(queued.get(0).getBody()).isEqualTo("first");
    }

    @Test
    void aMessageStaysOnDiskUntilTheBrowserConfirmsItIsInLocalStorage() {
        spool.append(DEVICE, message("m1", "first"));
        spool.append(DEVICE, message("m2", "second"));

        // the browser stored m1 in localStorage and acknowledged it
        spool.prune(DEVICE, "m1");

        assertThat(spool.readAll(DEVICE)).extracting(MailMessageVO::getMessageId).containsExactly("m2");

        // m2 acknowledged too -> nothing left, and the queue file is removed
        spool.prune(DEVICE, "m2");
        assertThat(spool.readAll(DEVICE)).isEmpty();
        assertThat(spool.spoolFile(DEVICE)).doesNotExist();
    }

    @Test
    void eachDeviceHasItsOwnIsolatedQueueFile() throws Exception {
        spool.append(DEVICE, message("m1", "for one device"));
        spool.append("02:cc:99:88:77:66", message("m2", "for another"));

        assertThat(spool.readAll(DEVICE)).hasSize(1);
        assertThat(spool.readAll("02:cc:99:88:77:66")).hasSize(1);
        assertThat(Files.list(spoolDir).count()).isEqualTo(2);
    }

    @Test
    void anUnknownDeviceHasAnEmptyQueue() {
        assertThat(spool.readAll("02:dd:00:00:00:01")).isEmpty();
        assertThat(spool.spoolFile("02:dd:00:00:00:01")).doesNotExist();
    }

    @Test
    void aCorruptQueueIsPreservedOnDiskRatherThanDropped() throws Exception {
        spool.append(DEVICE, message("m1", "precious"));
        Path queue = spool.spoolFile(DEVICE);

        // clobber it with garbage that is not valid gzip/json
        try (OutputStream out = Files.newOutputStream(queue)) {
            out.write("not a gzip stream".getBytes());
        }

        assertThat(spool.readAll(DEVICE)).isEmpty();
        // the unreadable queue is kept as .corrupt-<ts> instead of being destroyed
        assertThat(Files.list(spoolDir)
                .filter(p -> p.getFileName().toString().contains(".corrupt-"))
                .count()).isEqualTo(1);
    }

    @Test
    void theParallelEmailOutcomeIsRecordedAgainstTheQueuedMessage() {
        spool.append(DEVICE, message("m1", "hello"));

        spool.recordEmailCopyStatus(DEVICE, "m1", "FAILED", "SMTP down");

        MailMessageVO queued = spool.readAll(DEVICE).get(0);
        assertThat(queued.getEmailCopyStatus()).isEqualTo("FAILED");
        assertThat(queued.getEmailCopyDetail()).isEqualTo("SMTP down");
        // and the message is still queued for the browser regardless
        assertThat(queued.getBody()).isEqualTo("hello");
    }

    @Test
    void aMessageCanBeFoundAnywhereInTheFolderForStatusPolling() {
        spool.append(DEVICE, message("m1", "hello"));

        assertThat(spool.findByMessageId("m1")).isNotNull();
        assertThat(spool.findByMessageId("nope")).isNull();
    }

    @Test
    void writesAreAtomicAndLeaveNoTempFilesBehind() throws Exception {
        for (int i = 0; i < 25; i++) {
            spool.append(DEVICE, message("m" + i, "body " + i));
        }

        assertThat(spool.readAll(DEVICE)).hasSize(25);
        assertThat(Files.list(spoolDir)
                .filter(p -> p.getFileName().toString().endsWith(".tmp"))
                .count()).isZero();
    }
}
