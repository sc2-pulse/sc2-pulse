CREATE TABLE "patch"
(
    "id" BIGINT NOT NULL,
    "version" TEXT NOT NULL,
    "published" TIMESTAMP WITH TIME ZONE NOT NULL,

    PRIMARY KEY ("id")
);

CREATE INDEX "ix_patch_published" ON "patch"("published");

ALTER TABLE "patch"
    RENAME COLUMN "id" TO "build";
