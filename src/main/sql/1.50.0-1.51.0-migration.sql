ALTER TABLE "pro_player_account"
    ADD COLUMN "revealer_account_id" BIGINT,
    ADD CONSTRAINT "fk_pro_player_account_revealer_account_id"
        FOREIGN KEY ("revealer_account_id")
        REFERENCES "account"("id")
        ON DELETE SET NULL ON UPDATE CASCADE;

CREATE TABLE oauth2_authorized_client
(
    client_registration_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    access_token_type VARCHAR(100) NOT NULL,
    access_token_value bytea NOT NULL,
    access_token_issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    access_token_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    access_token_scopes VARCHAR(1000),
    refresh_token_value bytea,
    refresh_token_issued_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (client_registration_id, principal_name)
);

ALTER TABLE "pro_player"
    DROP COLUMN "revealed_id",
    ADD CONSTRAINT "uq_pro_player_aligulac_id"
        UNIQUE("aligulac_id");

ALTER TABLE "pro_player"
    ALTER COLUMN "name" DROP NOT NULL;
UPDATE "pro_player" SET "name" = NULL WHERE "name" = '';
