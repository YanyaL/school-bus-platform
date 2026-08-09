ALTER TABLE payment_record
    DROP CHECK ck_payment_status;

ALTER TABLE payment_record
    ADD CONSTRAINT ck_payment_status
        CHECK (
            status IN (
                'PROCESSING',
                'SUCCEEDED',
                'FAILED',
                'REFUND_PENDING'
            )
        );
