ALTER TABLE "league_tier"
    ALTER COLUMN "type" DROP NOT NULL,
    DROP CONSTRAINT "uq_league_tier_league_id_type";
CREATE UNIQUE INDEX "uq_league_tier_league_id_type" ON "league_tier"("league_id", COALESCE("type", -1));
