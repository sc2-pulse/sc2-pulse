CREATE TABLE "patch"
(
    "id" SERIAL,
    "build" BIGINT NOT NULL,
    "version" TEXT NOT NULL,
    "versus" BOOLEAN,

    PRIMARY KEY ("id"),

    CONSTRAINT "uq_patch_build_version"
        UNIQUE("build", "version")
);

CREATE TABLE "patch_release"
(
    "patch_id" INTEGER NOT NULL,
    "region" SMALLINT NOT NULL,
    "released" TIMESTAMP WITH TIME ZONE NOT NULL,

    PRIMARY KEY ("patch_id", "region"),

    CONSTRAINT "fk_patch_release_patch_id"
        FOREIGN KEY ("patch_id")
        REFERENCES "patch"("id")
        ON DELETE CASCADE ON UPDATE CASCADE
);
