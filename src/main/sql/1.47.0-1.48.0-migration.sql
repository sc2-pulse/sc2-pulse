ALTER TABLE "account_role"
    ADD CONSTRAINT "fk_account_role_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE;

CREATE TABLE "authentication_request"
(
    "name" TEXT NOT NULL,
    "created" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY ("name")
);
CREATE INDEX "ix_authentication_request_created" ON "authentication_request"("created");
