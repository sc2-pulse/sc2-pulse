CREATE UNIQUE INDEX "uq_discord_user_name_discriminator" ON "discord_user"("name", "discriminator") WHERE "discriminator" IS NOT NULL;
CREATE UNIQUE INDEX "uq_discord_user_name" ON "discord_user"("name") WHERE "discriminator" IS NULL;
ALTER TABLE "discord_user"
    ALTER COLUMN "discriminator" DROP NOT NULL,
    DROP CONSTRAINT "uq_discord_user_name_discriminator";

UPDATE "discord_user"
SET "discriminator" = NULL
WHERE "discriminator" = 0;

