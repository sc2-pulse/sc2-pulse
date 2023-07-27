ALTER TABLE "pro_player"
    ADD COLUMN "version" SMALLINT NOT NULL DEFAULT 1;

CREATE OR REPLACE FUNCTION increase_own_version()
RETURNS trigger
AS
'
    BEGIN
        IF(OLD.version != NEW.version OR OLD.* IS NOT DISTINCT FROM NEW.*) THEN return NEW; END IF;
        NEW.version := OLD.version + 1;
        RETURN NEW;
    END
'
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION increase_own_version_excluding_updated()
RETURNS trigger
AS
'
    DECLARE new_updated TIMESTAMP WITH TIME ZONE;
    BEGIN
        IF(OLD.version != NEW.version) THEN return NEW; END IF;
        new_updated := NEW.updated;
        NEW.updated := OLD.updated;
        IF(OLD.* IS NOT DISTINCT FROM NEW.*)
        THEN
            NEW.updated := new_updated;
            return NEW;
        END IF;

        NEW.version := OLD.version + 1;
        NEW.updated := new_updated;
        RETURN NEW;
    END
'
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION increase_foreign_version()
RETURNS trigger
AS
'
    DECLARE CUR record;
    BEGIN
        IF(TG_OP = ''UPDATE'' AND OLD.* IS NOT DISTINCT FROM NEW.*) THEN return NEW; END IF;
        CUR := COALESCE(NEW, OLD);
        EXECUTE
            ''UPDATE '' || TG_ARGV[0] || ''
            SET version = version + 1
            WHERE id = $1.'' || TG_ARGV[0] || ''_id''
            USING CUR;
        RETURN CUR;
    END
'
LANGUAGE plpgsql;

CREATE TRIGGER update_version
BEFORE UPDATE
ON pro_player
FOR EACH ROW
EXECUTE FUNCTION increase_own_version_excluding_updated();

CREATE TRIGGER update_parent_version
AFTER INSERT OR DELETE OR UPDATE
ON social_media_link
FOR EACH ROW
EXECUTE FUNCTION increase_foreign_version('pro_player');