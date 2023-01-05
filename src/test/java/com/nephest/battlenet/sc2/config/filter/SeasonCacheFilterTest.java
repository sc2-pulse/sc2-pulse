// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

@ExtendWith(MockitoExtension.class)
public class SeasonCacheFilterTest
{

    @Mock
    private SeasonDAO seasonDAO;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private SeasonCacheFilter filter;

    @BeforeEach
    public void beforeEach()
    {
        filter = new SeasonCacheFilter(seasonDAO);
    }

    @Test
    public void whenCurrentSeasonsAreMissing_thenDontCache()
    throws ServletException, IOException
    {
        List<Season> seasons =
            List.of(new Season(1, 1, Region.EU, 2020, 11, LocalDate.now(), LocalDate.now().plusMonths(1)));
        when(seasonDAO.findListByBattlenetId(any())).thenReturn(seasons);

        filter.doFilter(null, response, filterChain);
        NoCacheFilter.NO_CACHE_HEADERS.forEach((key, value)->verify(response).setHeader(key, value));
    }

    @Test
    public void whenCacheDurationIsNegative_thenDontCache()
    throws ServletException, IOException
    {
        List<Season> seasons = Arrays.stream(Region.values())
            .map(r->new Season(null, 1, r, 2020, 1,
                LocalDate.now().minusMonths(2), LocalDate.now().minusMonths(1)))
            .collect(Collectors.toList());
        when(seasonDAO.findListByBattlenetId(any())).thenReturn(seasons);

        filter.doFilter(null, response, filterChain);
        NoCacheFilter.NO_CACHE_HEADERS.forEach((key, value)->verify(response).setHeader(key, value));
    }

    @Test
    public void whenCacheDurationIsPositive_thenCache()
    throws ServletException, IOException
    {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(30);
        List<Season> seasons = Arrays.stream(Region.values())
            .map(r->new Season(null, 1, r, 2020, 1, start, start.plusMonths(10)))
            .collect(Collectors.toList());
        seasons.set(0, new Season(null, 1, Region.US, 2020, 1, start, end));
        when(seasonDAO.findListByBattlenetId(any())).thenReturn(seasons);

        filter.doFilter(null, response, filterChain);

        ArgumentCaptor<String> headerCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(eq(HttpHeaders.CACHE_CONTROL), headerCaptor.capture());
        String header = headerCaptor.getValue();
        String durationStr = header
            .substring(header.indexOf("=") + 1, header.indexOf(",", header.indexOf("=")));
        Duration duration = Duration.ofSeconds(Long.parseLong(durationStr));
        //min end datetime at start of the day is used, -10 to offset test duration
        assertTrue(duration.compareTo(Duration.between(LocalDateTime.now(), end.atStartOfDay()).minusSeconds(10)) > 0);
    }

}
