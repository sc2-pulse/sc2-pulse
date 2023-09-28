ALTER TABLE "social_media_link"
    ADD COLUMN "service_user_id" TEXT;

UPDATE social_media_link
SET service_user_id = twitch_user_id::text
FROM pro_player
WHERE pro_player_id = id
AND type = 2;

DELETE FROM social_media_link
WHERE type = 2
AND service_user_id IS NULL;

ALTER TABLE pro_player
    DROP COLUMN twitch_user_id;

ALTER TABLE team_member
    ADD COLUMN "team_season" SMALLINT,
    ADD COLUMN "team_queue_type" SMALLINT;
DO
$do$
DECLARE
    seasonId SMALLINT;
BEGIN
    FOR seasonId IN SELECT DISTINCT(battlenet_id) FROM season ORDER BY battlenet_id DESC LOOP
        UPDATE team_member
        SET team_season = team.season,
        team_queue_type = team.queue_type
        FROM team
        WHERE team_member.team_id = team.id
        AND team.season = seasonId;
    END LOOP;
END
$do$;
VACUUM(ANALYZE) team_member;
CREATE INDEX "ix_team_member_group_search" ON "team_member"("player_character_id", "team_season", "team_queue_type");
ALTER TABLE team_member
    ALTER COLUMN "team_season" SET NOT NULL,
    ALTER COLUMN "team_queue_type" SET NOT NULL;
CREATE OR REPLACE FUNCTION update_team_member_meta_data()
RETURNS trigger
AS
'
    BEGIN
        SELECT team.season, team.queue_type
        INTO NEW.team_season, NEW.team_queue_type
        FROM team
        WHERE team.id = NEW.team_id;
        return NEW;
    END
'
LANGUAGE plpgsql;
CREATE TRIGGER update_meta_data
BEFORE INSERT
ON team_member
FOR EACH ROW
EXECUTE FUNCTION update_team_member_meta_data();