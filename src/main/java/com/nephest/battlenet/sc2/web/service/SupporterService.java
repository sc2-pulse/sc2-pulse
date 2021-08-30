// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class SupporterService
{

    private final Random rng;
    private final List<String> supporters;
    private final List<String> donors;
    private final String sponsor;
    private final String sponsoredLink;
    private final List<String> sponsorsT2;
    private final List<String> sponsoredT2Links;

    @Autowired
    private SupporterService
    (
        @Qualifier("simpleRng") Random rng,
        @Value("${com.nephest.battlenet.sc2.donors:#{''}}") List<String> donors,
        @Value("${com.nephest.battlenet.sc2.supporters:#{''}}") List<String> supporters,
        @Value("${com.nephest.battlenet.sc2.sponsor:#{''}}") String sponsor,
        @Value("${com.nephest.battlenet.sc2.sponsor.link:#{''}}") String sponsoredLink,
        @Value("${com.nephest.battlenet.sc2.sponsor.t2:#{''}}") List<String> sponsorsT2,
        @Value("${com.nephest.battlenet.sc2.sponsor.t2.link:#{''}}") List<String> sponsoredT2Links
    )
    {
        this.rng = rng;
        this.donors = donors;
        this.supporters = supporters;
        this.sponsor = sponsor;
        this.sponsoredLink = sponsoredLink;
        this.sponsorsT2 = sponsorsT2;
        this.sponsoredT2Links = sponsoredT2Links;
    }

    public List<String> getDonors()
    {
        return donors;
    }

    public String getRandomSupporter()
    {
        return supporters.get(rng.nextInt(supporters.size()));
    }

    public List<String> getSupporters()
    {
        return supporters;
    }

    public String getSponsor()
    {
        return sponsor;
    }

    public String getSponsoredLink()
    {
        return sponsoredLink;
    }

    public List<String> getSponsorsT2()
    {
        return sponsorsT2;
    }

    public List<String> getSponsoredT2Links()
    {
        return sponsoredT2Links;
    }

}
