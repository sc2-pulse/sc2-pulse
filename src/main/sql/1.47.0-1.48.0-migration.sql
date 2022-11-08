ALTER TABLE "account_role"
    ADD CONSTRAINT "fk_account_role_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE;
