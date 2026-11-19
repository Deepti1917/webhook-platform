package com.webhookplatform.delivery;

public enum DeliveryStatus {
    /** Waiting for its first attempt, or waiting for a retry at next_attempt_at. */
    PENDING,
    /** The receiver answered 2xx. */
    SUCCESS,
    /** Out of attempts (the dead-letter state). Can be re-queued with the replay endpoint. */
    DEAD
}
