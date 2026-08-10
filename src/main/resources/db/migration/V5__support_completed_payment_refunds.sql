ALTER TABLE payment_record
    ADD COLUMN refund_no VARCHAR(64) NULL AFTER failure_reason,
    ADD COLUMN refunded_at DATETIME(3) NULL AFTER completed_at;

ALTER TABLE payment_record
    DROP CHECK ck_payment_status;

ALTER TABLE payment_record
    ADD CONSTRAINT ck_payment_status
        CHECK (
            status IN (
                'PROCESSING',
                'SUCCEEDED',
                'FAILED',
                'REFUND_PENDING',
                'REFUNDED'
            )
        );
