-- Copyright (C) 2021 Oleksandr Masniuk and contributors
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE TABLE "account_role"
(
    "account_id" BIGINT NOT NULL,
    "role" SMALLINT NOT NULL,

    PRIMARY KEY ("account_id", "role")
);
