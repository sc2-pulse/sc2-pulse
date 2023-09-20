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
