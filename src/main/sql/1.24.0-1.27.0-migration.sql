-- Copyright (C) 2021 Oleksandr Masniuk
-- SPDX-License-Identifier: AGPL-3.0-or-later
---

CREATE TABLE "persistent_logins"
(
    "username" TEXT NOT NULL,
    "series" TEXT PRIMARY KEY,
    "token" TEXT NOT NULL,
    "last_used" TIMESTAMP WITH TIME ZONE NOT NULL
);
