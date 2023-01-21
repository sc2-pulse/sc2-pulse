ALTER TABLE "pro_player_account"
    ADD COLUMN "revealer_account_id" BIGINT,
    ADD CONSTRAINT "fk_pro_player_account_revealer_account_id"
        FOREIGN KEY ("revealer_account_id")
        REFERENCES "account"("id")
        ON DELETE SET NULL ON UPDATE CASCADE;