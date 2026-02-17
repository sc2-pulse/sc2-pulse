// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.data.clickhouse;

import java.net.URI;
import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;

public interface ClickHouseConnectionDetails
extends ConnectionDetails
{

    URI getEndpoint();

    String getDatabase();

    String getUsername();

    String getPassword();

}
