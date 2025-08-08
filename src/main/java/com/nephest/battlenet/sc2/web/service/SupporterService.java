// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.CollectionVar;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.util.wrapper.ThreadLocalRandomSupplier;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class SupporterService
{

    private static final Logger LOG = LoggerFactory.getLogger(SupporterService.class);

    public static final String DEFAULT_DELIMITER = ", ";

    private final ThreadLocalRandomSupplier rng;
    private CollectionVar<List<String>, String> supporters;
    private CollectionVar<List<String>, String> donors;
    private final String sponsor;
    private final String sponsoredLink;
    private final List<String> sponsorsT2;
    private final List<String> sponsoredT2Links;
    private final List<String> patrons;
    private final Map<String, String> sponsoredImageLinks;
    private final Map<String, String> friendImageLinks;
    private final Object supporterWriteBlock = new Object();

    @Autowired @Lazy
    private SupporterService supporterService;

    @Autowired
    public SupporterService
    (
        VarDAO varDAO,
        ThreadLocalRandomSupplier rng,
        @Value("${com.nephest.battlenet.sc2.sponsor:#{''}}") String sponsor,
        @Value("${com.nephest.battlenet.sc2.sponsor.link:#{''}}") String sponsoredLink,
        @Value("${com.nephest.battlenet.sc2.sponsor.t2:#{''}}") List<String> sponsorsT2,
        @Value("${com.nephest.battlenet.sc2.sponsor.t2.link:#{''}}") List<String> sponsoredT2Links,
        @Value("#{${com.nephest.battlenet.sc2.sponsor.img:{:}}}") Map<String, String> sponsoredImageLinks,
        @Value("#{${com.nephest.battlenet.sc2.friend.img:{:}}}") Map<String, String> friendImageLinks
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
        this.friendImageLinks = friendImageLinks;
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

    protected void setSupporterService(SupporterService supporterService)
    {
        this.supporterService = supporterService;
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
        return patrons.isEmpty()
            ? null
            : patrons.get(rng.get().nextInt(patrons.size()));
    }

    public String getRandomSupporter()
    {
        return supporters.getValue().isEmpty()
            ? null
            : supporters.getValue().get(rng.get().nextInt(supporters.getValue().size()));
    }

    public String getRandomSupporters(int count)
    {
        int maxSubListIx = supporters.getValue().size() / count;
        int randomSublist = rng.get().nextInt(maxSubListIx + 1);
        return supporterService.getRandomSupporters(count, randomSublist);
    }

    @Cacheable(cacheNames = "supporters")
    public String getRandomSupporters(int count, int subListIndex)
    {
        List<String> names = supporters.getValue();
        if(count >= names.size()) return String.join(DEFAULT_DELIMITER, names);
        if(count < 1) throw new IllegalArgumentException();

        List<String> subList = names
            .subList(subListIndex * count, Math.min((subListIndex + 1) * count, names.size()));
        return String.join(DEFAULT_DELIMITER, subList);
    }

    @CacheEvict(cacheNames = "supporters")
    public void addSupporter(String name)
    {
        synchronized(supporterWriteBlock)
        {
            supporters.getValue().add(name);
            patrons.add(name);
            supporters.save();
        }
    }

    @CacheEvict(cacheNames = "supporters")
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

    public Map<String, String> getFriendImageLinks()
    {
        return friendImageLinks;
    }

}
