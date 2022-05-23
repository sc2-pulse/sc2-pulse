// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.CollectionVar;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SupporterService
{

    private static final Logger LOG = LoggerFactory.getLogger(SupporterService.class);

    private final Random rng;
    private CollectionVar<List<String>, String> supporters;
    private CollectionVar<List<String>, String> donors;
    private final String sponsor;
    private final String sponsoredLink;
    private final List<String> sponsorsT2;
    private final List<String> sponsoredT2Links;
    private final List<String> patrons;
    private final Map<String, String> sponsoredImageLinks;
    private final Object supporterWriteBlock = new Object();

    @Autowired
    private SupporterService
    (
        VarDAO varDAO,
        @Qualifier("simpleRng") Random rng,
        @Value("${com.nephest.battlenet.sc2.sponsor:#{''}}") String sponsor,
        @Value("${com.nephest.battlenet.sc2.sponsor.link:#{''}}") String sponsoredLink,
        @Value("${com.nephest.battlenet.sc2.sponsor.t2:#{''}}") List<String> sponsorsT2,
        @Value("${com.nephest.battlenet.sc2.sponsor.t2.link:#{''}}") List<String> sponsoredT2Links,
        @Value("#{${com.nephest.battlenet.sc2.sponsor.img:{:}}}") Map<String, String> sponsoredImageLinks
    )
    {
        initVars(varDAO);
        this.rng = rng;
        this.sponsor = sponsor;
        this.sponsoredLink = sponsoredLink;
        this.sponsorsT2 = sponsorsT2;
        this.sponsoredT2Links = sponsoredT2Links;
        this.patrons = Stream.concat(Stream.of(supporters.getValue(), sponsorsT2).flatMap(Collection::stream), Stream.of(sponsor))
            .collect(Collectors.toCollection(CopyOnWriteArrayList::new));
        this.sponsoredImageLinks = sponsoredImageLinks;
    }

    private void initVars(VarDAO varDAO)
    {
        donors = new CollectionVar<>
        (
            varDAO, "donors",
            Function.identity(), Function.identity(),
            CopyOnWriteArrayList::new,
            Collectors.toCollection(CopyOnWriteArrayList::new),
            false
        );
        supporters = new CollectionVar<>
        (
            varDAO, "supporters",
            Function.identity(), Function.identity(),
            CopyOnWriteArrayList::new,
            Collectors.toCollection(CopyOnWriteArrayList::new),
            false
        );
        try
        {
            donors.load();
            supporters.load();
        }
        catch (Exception ex)
        {
            LOG.error(ex.getMessage(), ex);
        }
    }

    public List<String> getDonors()
    {
        return donors.getValue();
    }

    public CollectionVar<List<String>, String> getDonorsVar()
    {
        return donors;
    }

    public String getRandomPatron()
    {
        synchronized(supporterWriteBlock)
        {
            return patrons.isEmpty()
                ? null
                : patrons.get(rng.nextInt(patrons.size()));
        }
    }

    public String getRandomSupporter()
    {
        synchronized(supporterWriteBlock)
        {
            return supporters.getValue().isEmpty()
                ? null
                : supporters.getValue().get(rng.nextInt(supporters.getValue().size()));
        }
    }

    public void addSupporter(String name)
    {
        synchronized(supporterWriteBlock)
        {
            supporters.getValue().add(name);
            patrons.add(name);
            supporters.save();
        }
    }

    public void removeSupporter(String name)
    {
        synchronized(supporterWriteBlock)
        {
            supporters.getValue().remove(name);
            patrons.remove(name);
            supporters.save();
        }
    }

    public List<String> getSupporters()
    {
        return supporters.getValue();
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

    public Map<String, String> getSponsoredImageLinks()
    {
        return sponsoredImageLinks;
    }

}
