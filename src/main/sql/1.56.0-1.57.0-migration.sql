CREATE TABLE "patch"
(
    "build" BIGINT NOT NULL,
    "version" TEXT NOT NULL,

    PRIMARY KEY ("build")
);

CREATE TABLE "patch_release"
(
    "patch_build" BIGINT NOT NULL,
    "region" SMALLINT NOT NULL,
    "released" TIMESTAMP WITH TIME ZONE NOT NULL,

    PRIMARY KEY ("patch_build", "region"),

     CONSTRAINT "fk_patch_release_patch_build"
        FOREIGN KEY ("patch_build")
        REFERENCES "patch"("build")
        ON DELETE CASCADE ON UPDATE CASCADE
);

