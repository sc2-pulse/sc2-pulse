CREATE TABLE "notification"
(
    "id" BIGSERIAL,
    "account_id" BIGINT NOT NULL,
    "message" TEXT NOT NULL,
    "created" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY ("id"),

    CONSTRAINT "fk_notification_account_id"
        FOREIGN KEY ("account_id")
        REFERENCES "account"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);

ALTER TABLE "player_character_report"
    ADD COLUMN "restrictions" BOOLEAN NOT NULL DEFAULT false;

DO
$do$
DECLARE
    id BIGINT;
    i BIGINT DEFAULT 0;
    total BIGINT;
BEGIN
   total := (SELECT COUNT(*) FROM team);
   FOR id IN SELECT team.id FROM team ORDER BY id DESC LOOP
       WITH state_filter AS
       (
           SELECT team_id, timestamp,
           LAG(games) OVER(ORDER BY timestamp) AS prev_games,
           LAG(timestamp) OVER(ORDER BY timestamp) AS prev_timestamp
           FROM team_state
           WHERE team_state.team_id = id
       )
       DELETE FROM team_state
       USING state_filter
       WHERE team_state.team_id = state_filter.team_id
       AND team_state.timestamp = state_filter.timestamp
       AND team_state.games < state_filter.prev_games
       AND team_state.games > EXTRACT(epoch FROM team_state.timestamp - state_filter.prev_timestamp) / 540;
       i := i + 1;
       IF i % 1000 = 0 THEN RAISE NOTICE '% %', i, (i / total::float * 100); END IF;
   END LOOP;
END
$do$;
