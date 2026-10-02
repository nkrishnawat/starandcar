package com.resilientechnology.starandcar.record;

/**
 * Outcome of a StarMail send.
 *
 * <p>The two delivery legs are independent:</p>
 * <ul>
 *   <li>{@code localDeliveryStatus} - always {@code DELIVERED} when the call returns, because
 *       the message is persisted for the recipient browser to pick up by device id.</li>
 *   <li>{@code emailCopyStatus} - {@code QUEUED} when the registered-e-mail copy is dispatched
 *       asynchronously, or {@code SKIPPED}/{@code FAILED} when it could not be dispatched.
 *       The final result is recorded against the message and can be polled.</li>
 * </ul>
 */
public record StarMailSendResult(
        Long messageId,
        String localDeliveryStatus,
        String emailCopyStatus,
        String emailCopyDetail
) {
}
