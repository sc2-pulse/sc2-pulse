ALTER TABLE "social_media_link"
    ADD COLUMN "service_user_id" TEXT;

UPDATE social_media_link
SET service_user_id = twitch_user_id::text
FROM pro_player
WHERE pro_player_id = id
AND type = 2;

CREATE INDEX "ix_social_media_link_type_service_user_id"
ON "social_media_link"("type", "service_user_id")
WHERE "service_user_id" IS NOT NULL;

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

CREATE INDEX "ix_recent_team_search" ON "team"("queue_type", "league_type", "last_played")
    WHERE "last_played" IS NOT NULL;

-- An audit history is important on most tables. Provide an audit trigger that logs to
-- a dedicated audit table for the major relations.
--
-- This file should be generic and not depend on application roles or structures,
-- as it's being listed here:
--
--    https://wiki.postgresql.org/wiki/Audit_trigger_91plus
--
-- This trigger was originally based on
--   http://wiki.postgresql.org/wiki/Audit_trigger
-- but has been completely rewritten.
--
-- Should really be converted into a relocatable EXTENSION, with control and upgrade files.

CREATE SCHEMA audit;
REVOKE ALL ON SCHEMA audit FROM public;

COMMENT ON SCHEMA audit IS 'Out-of-table audit/history logging tables and trigger functions';

--
-- Audited data. Lots of information is available, it's just a matter of how much
-- you really want to record. See:
--
--   http://www.postgresql.org/docs/9.1/static/functions-info.html
--
-- Remember, every column you add takes up more audit table space and slows audit
-- inserts.
--
-- Every index you add has a big impact too, so avoid adding indexes to the
-- audit table unless you REALLY need them. The hstore GIST indexes are
-- particularly expensive.
--
-- It is sometimes worth copying the audit table, or a coarse subset of it that
-- you're interested in, into a temporary table where you CREATE any useful
-- indexes and do your analysis.
--
CREATE TABLE audit.logged_actions (
    event_id bigserial primary key,
    schema_name text not null,
    table_name text not null,
    relid oid not null,
    session_user_name text,
    action_tstamp_tx TIMESTAMP WITH TIME ZONE NOT NULL,
    action_tstamp_stm TIMESTAMP WITH TIME ZONE NOT NULL,
    action_tstamp_clk TIMESTAMP WITH TIME ZONE NOT NULL,
    client_query text,
    action TEXT NOT NULL CHECK (action IN ('I','D','U', 'T')),
    row_data jsonb,
    changed_fields jsonb,
    statement_only boolean not null
);

REVOKE ALL ON audit.logged_actions FROM public;

COMMENT ON TABLE audit.logged_actions IS 'History of auditable actions on audited tables, from audit.if_modified_func()';
COMMENT ON COLUMN audit.logged_actions.event_id IS 'Unique identifier for each auditable event';
COMMENT ON COLUMN audit.logged_actions.schema_name IS 'Database schema audited table for this event is in';
COMMENT ON COLUMN audit.logged_actions.table_name IS 'Non-schema-qualified table name of table event occured in';
COMMENT ON COLUMN audit.logged_actions.relid IS 'Table OID. Changes with drop/create. Get with ''tablename''::regclass';
COMMENT ON COLUMN audit.logged_actions.session_user_name IS 'Login / session user whose statement caused the audited event';
COMMENT ON COLUMN audit.logged_actions.action_tstamp_tx IS 'Transaction start timestamp for tx in which audited event occurred';
COMMENT ON COLUMN audit.logged_actions.action_tstamp_stm IS 'Statement start timestamp for tx in which audited event occurred';
COMMENT ON COLUMN audit.logged_actions.action_tstamp_clk IS 'Wall clock time at which audited event''s trigger call occurred';
COMMENT ON COLUMN audit.logged_actions.client_query IS 'Top-level query that caused this auditable event. May be more than one statement.';
COMMENT ON COLUMN audit.logged_actions.action IS 'Action type; I = insert, D = delete, U = update, T = truncate';
COMMENT ON COLUMN audit.logged_actions.row_data IS 'Record value. Null for statement-level trigger. For INSERT this is the new tuple. For DELETE and UPDATE it is the old tuple.';
COMMENT ON COLUMN audit.logged_actions.changed_fields IS 'New values of fields changed by UPDATE. Null except for row-level UPDATE events.';
COMMENT ON COLUMN audit.logged_actions.statement_only IS '''t'' if audit event is from an FOR EACH STATEMENT trigger, ''f'' for FOR EACH ROW';

CREATE INDEX "ix_logged_actions_tstamp_tx_stm" ON "audit"."logged_actions"("action_tstamp_stm");

CREATE OR REPLACE FUNCTION audit.if_modified_func() RETURNS TRIGGER AS '
DECLARE
    audit_row audit.logged_actions;
    include_values boolean;
    log_diffs boolean;
    h_old jsonb;
    h_new jsonb;
    excluded_cols text[] = ARRAY[]::text[];
BEGIN
    IF TG_WHEN <> ''AFTER'' THEN
        RAISE EXCEPTION ''audit.if_modified_func() may only run as an AFTER trigger'';
    END IF;

    audit_row = ROW(
        nextval(''audit.logged_actions_event_id_seq''), -- event_id
        TG_TABLE_SCHEMA::text,                        -- schema_name
        TG_TABLE_NAME::text,                          -- table_name
        TG_RELID,                                     -- relation OID for much quicker searches
        current_setting(''sc2pulse.user_id'', true),    -- session_user_name
        current_timestamp,                            -- action_tstamp_tx
        statement_timestamp(),                        -- action_tstamp_stm
        clock_timestamp(),                            -- action_tstamp_clk
        current_query(),                              -- top-level query or queries (if multistatement) from client
        substring(TG_OP,1,1),                         -- action
        NULL, NULL,                                   -- row_data, changed_fields
        ''f''                                           -- statement_only
        );

    IF NOT TG_ARGV[0]::boolean IS DISTINCT FROM ''f''::boolean THEN
        audit_row.client_query = NULL;
    END IF;

    IF TG_ARGV[1] IS NOT NULL THEN
        excluded_cols = TG_ARGV[1]::text[];
    END IF;

    IF (TG_OP = ''UPDATE'' AND TG_LEVEL = ''ROW'') THEN
        audit_row.row_data = row_to_json(OLD)::JSONB - excluded_cols;
        --Computing differences
        SELECT jsonb_object_agg(tmp_new_row.key, tmp_new_row.value) AS new_data INTO audit_row.changed_fields
        FROM jsonb_each_text(row_to_json(NEW)::JSONB) AS tmp_new_row
            JOIN jsonb_each_text(audit_row.row_data) AS tmp_old_row ON (
                tmp_new_row.key = tmp_old_row.key
                AND tmp_new_row.value IS DISTINCT
                FROM tmp_old_row.value
            );
        IF audit_row.changed_fields IS NULL OR audit_row.changed_fields = ''{}''::JSONB THEN
            -- All changed fields are ignored. Skip this update.
            RETURN NULL;
        END IF;
    ELSIF (TG_OP = ''DELETE'' AND TG_LEVEL = ''ROW'') THEN
        audit_row.row_data = row_to_json(OLD)::JSONB - excluded_cols;
    ELSIF (TG_OP = ''INSERT'' AND TG_LEVEL = ''ROW'') THEN
        audit_row.row_data = row_to_json(NEW)::JSONB - excluded_cols;
    ELSIF (TG_LEVEL = ''STATEMENT'' AND TG_OP IN (''INSERT'',''UPDATE'',''DELETE'',''TRUNCATE'')) THEN
        audit_row.statement_only = ''t'';
    ELSE
        RAISE EXCEPTION ''[audit.if_modified_func] - Trigger func added as trigger for unhandled case: %, %'',TG_OP, TG_LEVEL;
        RETURN NULL;
    END IF;
    INSERT INTO audit.logged_actions VALUES (audit_row.*);
    RETURN NULL;
END;
'
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public;


COMMENT ON FUNCTION audit.if_modified_func() IS '
Track changes to a table at the statement and/or row level.

Optional parameters to trigger in CREATE TRIGGER call:

param 0: boolean, whether to log the query text. Default ''t''.

param 1: text[], columns to ignore in updates. Default [].

         Updates to ignored cols are omitted from changed_fields.

         Updates with only ignored cols changed are not inserted
         into the audit log.

         Almost all the processing work is still done for updates
         that ignored. If you need to save the load, you need to use
         WHEN clause on the trigger instead.

         No warning or error is issued if ignored_cols contains columns
         that do not exist in the target table. This lets you specify
         a standard set of ignored columns.

There is no parameter to disable logging of values. Add this trigger as
a ''FOR EACH STATEMENT'' rather than ''FOR EACH ROW'' trigger if you do not
want to log row values.

Note that the user name logged is the login role for the session. The audit trigger
cannot obtain the active role because it is reset by the SECURITY DEFINER invocation
of the audit trigger its self.
';



CREATE OR REPLACE FUNCTION audit.audit_table(target_table regclass, audit_rows boolean, audit_query_text boolean, ignored_cols text[]) RETURNS void AS '
DECLARE
  stm_targets text = ''INSERT OR UPDATE OR DELETE OR TRUNCATE'';
  _q_txt text;
  _ignored_cols_snip text = '''';
BEGIN
    EXECUTE ''DROP TRIGGER IF EXISTS audit_trigger_row ON '' || target_table;
    EXECUTE ''DROP TRIGGER IF EXISTS audit_trigger_stm ON '' || target_table;

    IF audit_rows THEN
        IF array_length(ignored_cols,1) > 0 THEN
            _ignored_cols_snip = '', '' || quote_literal(ignored_cols);
        END IF;
        _q_txt = ''CREATE TRIGGER audit_trigger_row AFTER INSERT OR UPDATE OR DELETE ON '' ||
                 target_table ||
                 '' FOR EACH ROW EXECUTE PROCEDURE audit.if_modified_func('' ||
                 quote_literal(audit_query_text) || _ignored_cols_snip || '');'';
        RAISE NOTICE ''%'',_q_txt;
        EXECUTE _q_txt;
        stm_targets = ''TRUNCATE'';
    ELSE
    END IF;

    _q_txt = ''CREATE TRIGGER audit_trigger_stm AFTER '' || stm_targets || '' ON '' ||
             target_table ||
             '' FOR EACH STATEMENT EXECUTE PROCEDURE audit.if_modified_func(''||
             quote_literal(audit_query_text) || '');'';
    RAISE NOTICE ''%'',_q_txt;
    EXECUTE _q_txt;

END;
'
language 'plpgsql';

COMMENT ON FUNCTION audit.audit_table(regclass, boolean, boolean, text[]) IS '
Add auditing support to a table.

Arguments:
   target_table:     Table name, schema qualified if not on search_path
   audit_rows:       Record each row change, or only audit at a statement level
   audit_query_text: Record the text of the client query that triggered the audit event?
   ignored_cols:     Columns to exclude from update diffs, ignore updates that change only ignored cols.
';

-- Pg doesn't allow variadic calls with 0 params, so provide a wrapper
CREATE OR REPLACE FUNCTION audit.audit_table(target_table regclass, audit_rows boolean, audit_query_text boolean) RETURNS void AS '
SELECT audit.audit_table($1, $2, $3, ARRAY[]::text[]);
' LANGUAGE SQL;

-- And provide a convenience call wrapper for the simplest case
-- of row-level logging with no excluded cols and query logging enabled.
--
CREATE OR REPLACE FUNCTION audit.audit_table(target_table regclass) RETURNS void AS '
SELECT audit.audit_table($1, BOOLEAN ''t'', BOOLEAN ''t'');
' LANGUAGE 'sql';

COMMENT ON FUNCTION audit.audit_table(regclass) IS '
Add auditing support to the given table. Row-level changes will be logged with full client query text. No cols are ignored.
';

CREATE OR REPLACE VIEW audit.tableslist AS
 SELECT DISTINCT triggers.trigger_schema AS schema,
    triggers.event_object_table AS auditedtable
   FROM information_schema.triggers
    WHERE triggers.trigger_name::text IN ('audit_trigger_row'::text, 'audit_trigger_stm'::text)
ORDER BY schema, auditedtable;

COMMENT ON VIEW audit.tableslist IS '
View showing all tables with auditing set up. Ordered by schema, then table.
';

CREATE OR REPLACE FUNCTION audit.fill_log(target_table regclass, ignoreFields text[]) RETURNS void AS '
BEGIN
EXECUTE ''INSERT INTO audit.logged_actions
(
    schema_name,
    table_name,
    relid,
    action_tstamp_tx, action_tstamp_stm, action_tstamp_clk,
    action,
    row_data,
    statement_only
)
SELECT
(
    SELECT schemaname
    FROM pg_catalog.pg_statio_user_tables
    WHERE relid = '' || target_table::integer || ''
),
(
    SELECT relname
    FROM pg_catalog.pg_statio_user_tables
    WHERE relid = '' || target_table::integer || ''
),
'' || target_table::integer || '',
NOW(), NOW(), NOW(),
''''I'''',
to_jsonb('' || target_table || '') - '''''' || ignoreFields::text || ''''''::text[],
false
FROM '' || target_table;
END
' LANGUAGE 'plpgsql';

SELECT audit.fill_log('pro_player', '{version, updated}'::text[]);
SELECT audit.fill_log('social_media_link', '{updated}'::text[]);
SELECT audit.fill_log('pro_player_account', '{updated}'::text[]);

SELECT audit.audit_table('pro_player', 'true', 'false', '{version, updated}'::text[]);
SELECT audit.audit_table('social_media_link', 'true', 'false', '{updated}'::text[]);
SELECT audit.audit_table('pro_player_account', 'true', 'false', '{updated}'::text[]);
