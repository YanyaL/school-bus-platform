ALTER TABLE event_outbox
    DROP CHECK ck_event_outbox_status;

ALTER TABLE event_outbox
    ADD CONSTRAINT ck_event_outbox_status
        CHECK (status IN ('NEW', 'PROCESSING', 'PUBLISHED', 'FAILED'));
