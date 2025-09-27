ALTER TABLE room_messages
    ADD COLUMN client_msg_id VARCHAR(64) NULL;

ALTER TABLE room_messages
    ADD CONSTRAINT uq_room_client_msg UNIQUE (room_id, client_msg_id);
