// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLegacyProfile;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.ClanMemberEvent;
import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.local.LongVar;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.TimerVar;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberEventDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.local.inner.ClanMemberEventData;
import com.nephest.battlenet.sc2.service.EventService;
import com.nephest.battlenet.sc2.util.MiscUtil;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Service
public class ClanService
{

    private static final Logger LOG = LoggerFactory.getLogger(ClanService.class);

    public static final Duration STATS_UPDATE_FRAME = Duration.ofDays(2);
    public static final int CLAN_STATS_BATCH_SIZE = 12;

    public static final Duration CLAN_MEMBER_INACTIVE_AFTER = Duration.ofDays(7);
    public static final Duration CLAN_MEMBER_UPDATE_FRAME = ClanMemberDAO.TTL
        .minus(CLAN_MEMBER_INACTIVE_AFTER)
        .dividedBy(3);
    public static final int INACTIVE_CLAN_MEMBER_BATCH_SIZE = 200;

    private final PlayerCharacterDAO playerCharacterDAO;
    private final ClanDAO clanDAO;
    private final ClanMemberDAO clanMemberDAO;
    private final ClanMemberEventDAO clanMemberEventDAO;
    private final BlizzardSC2API api;
    private final AlternativeLadderService alternativeLadderService;
    private final ExecutorService dbExecutorService;
    private final ExecutorService webExecutorService;

    private InstantVar statsUpdated;
    private TimerVar nullifyStatsTask;
    private LongVar statsCursor;

    private InstantVar inactiveClanMembersUpdated;
    private LongVar inactiveClanMembersCursor;

    private Future<?> inactiveClanMembersUpdateTask = CompletableFuture.completedFuture(null);

    @Autowired @Lazy
    private ClanService clanService;

    private static final Duration CLAN_UPDATE_INSTANT_TTL = Duration.ofHours(1);
    private final Map<Long, Instant> characterClanUpdateInstants = new ConcurrentHashMap<>();
    private final Predicate<ClanMemberEventData> clanUpdatePredicate = t->
        characterClanUpdateInstants.compute(t.getCharacter().getId(),
            (id, ts)->ts == null || ts.isBefore(t.getCreatedAt()) ? t.getCreatedAt() : ts)
                .compareTo(t.getCreatedAt()) <= 0;

    @Autowired
    public ClanService
    (
        PlayerCharacterDAO playerCharacterDAO,
        ClanDAO clanDAO,
        ClanMemberDAO clanMemberDAO,
        ClanMemberEventDAO clanMemberEventDAO,
        VarDAO varDAO,
        BlizzardSC2API api,
        EventService eventService,
        @Lazy AlternativeLadderService alternativeLadderService,
        @Qualifier("dbExecutorService") ExecutorService dbExecutorService,
        @Qualifier("webExecutorService") ExecutorService webExecutorService
    )
    {
        this.playerCharacterDAO = playerCharacterDAO;
        this.clanDAO = clanDAO;
        this.clanMemberDAO = clanMemberDAO;
        this.clanMemberEventDAO = clanMemberEventDAO;
        this.api = api;
        this.alternativeLadderService = alternativeLadderService;
        this.dbExecutorService = dbExecutorService;
        this.webExecutorService = webExecutorService;
        init(varDAO);
        subscribeToEvents(eventService);
    }

    private void init(VarDAO varDAO)
    {
        statsUpdated = new InstantVar(varDAO, "clan.stats.updated", false);
        nullifyStatsTask = new TimerVar
        (
            varDAO,
            "clan.stats.nullified",
            false,
            STATS_UPDATE_FRAME,
            this::nullifyStats
        );
        statsCursor = new LongVar(varDAO, "clan.stats.id", false);

        inactiveClanMembersUpdated = new InstantVar(varDAO, "clan.member.inactive.updated", false);
        inactiveClanMembersCursor = new LongVar(varDAO, "clan.member.inactive.id", false);

        try
        {
            if(statsUpdated.load() == null) statsUpdated.setValueAndSave(Instant.now());
            if(nullifyStatsTask.load() == null) nullifyStatsTask
                .setValueAndSave(Instant.now().minus(STATS_UPDATE_FRAME));
            if(statsCursor.load() == null) statsCursor.setValueAndSave(0L);

            if(inactiveClanMembersUpdated.load() == null)
                inactiveClanMembersUpdated.setValueAndSave(Instant.now());
            if(inactiveClanMembersCursor.load() == null)
                inactiveClanMembersCursor.setValueAndSave(Long.MAX_VALUE);
        }
        catch (Exception ex)
        {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private void subscribeToEvents(EventService eventService)
    {
        eventService.getLadderUpdateEvent()
            .flatMap(allStats->WebServiceUtil.getOnErrorLogAndSkipMono(Mono.fromRunnable(this::update)))
            .subscribe();
    }

    protected InstantVar getStatsUpdated()
    {
        return statsUpdated;
    }

    protected TimerVar getNullifyStatsTask()
    {
        return nullifyStatsTask;
    }

    protected LongVar getStatsCursor()
    {
        return statsCursor;
    }

    protected InstantVar getInactiveClanMembersUpdated()
    {
        return inactiveClanMembersUpdated;
    }

    protected LongVar getInactiveClanMembersCursor()
    {
        return inactiveClanMembersCursor;
    }

    protected void setClanService(ClanService clanService)
    {
        this.clanService = clanService;
    }

    @Scheduled(cron="0 0/10 * * * *")
    public boolean removeOldClanUpdates()
    {
        Instant min = Instant.now().minus(CLAN_UPDATE_INSTANT_TTL);
        return characterClanUpdateInstants.entrySet().removeIf(e->e.getValue().isBefore(min));
    }


    private void update()
    {
        updateClanMembers();
        dbExecutorService.submit(()->clanService.updateAndNullifyStats());
    }

    @Transactional
    public void updateAndNullifyStats()
    {
        if(shouldUpdateStats()) updateStats();
        nullifyStatsTask.runIfAvailable().block();
    }

    private boolean shouldUpdateStats()
    {
        return true;
    }

    private void updateStats()
    {
        int batchSize = getClanBatchSize();
        if(batchSize < 1) return;

        List<Integer> batch = clanDAO.findIdsByMinMemberCount
        (
            ClanDAO.CLAN_STATS_MIN_MEMBERS,
            (int) (long) statsCursor.getValue(),
            batchSize
        );
        if(batch.isEmpty())
        {
            statsCursor.setValueAndSave(0L);
            statsUpdated.setValueAndSave(Instant.now());
        }
        else
        {
            updateStats(batch);
            statsCursor.setValueAndSave((long) batch.get(batch.size() - 1));
            statsUpdated.setValueAndSave(Instant.now());
        }

    }

    public int updateStats(List<Integer> validClans)
    {
        int batchIx = 0;
        int count = 0;
        while(batchIx < validClans.size())
        {
            List<Integer> batch = validClans.subList(batchIx, Math.min(batchIx + CLAN_STATS_BATCH_SIZE, validClans.size()));
            count += clanDAO.updateStats(batch);
            batchIx += batch.size();
            LOG.trace("Clan stats progress: {}/{} ", batchIx, validClans.size());
        }
        LOG.info("Updates stats of {} clans", count);
        return count;
    }

    private int getClanBatchSize()
    {
        int clansTotal = clanDAO.getCountByMinMemberCount(ClanDAO.CLAN_STATS_MIN_MEMBERS);
        if(clansTotal == 0) return 0;
        Duration durationPerClan = STATS_UPDATE_FRAME.dividedBy(clansTotal);
        return (int) Duration.between(statsUpdated.getValue(), Instant.now())
            .dividedBy(durationPerClan);
    }

    private void nullifyStats()
    {
        clanDAO.nullifyStats(ClanDAO.CLAN_STATS_MIN_MEMBERS - 1);
    }

    private int getInactiveClanMembersBatchSize()
    {
        OffsetDateTime inactiveTo = OffsetDateTime.now().minus(CLAN_MEMBER_INACTIVE_AFTER);
        int inactiveMembersTotal = clanMemberDAO.getInactiveCount(inactiveTo);
        if(inactiveMembersTotal < 1) return 0;

        Duration durationPerClanMember = CLAN_MEMBER_UPDATE_FRAME.dividedBy(inactiveMembersTotal);
        return (int) Duration.between(inactiveClanMembersUpdated.getValue(), Instant.now())
            .dividedBy(durationPerClanMember);
    }

    private void updateClanMembers()
    {
        dbExecutorService.submit(this::removeExpiredClanMembers);
        if(inactiveClanMembersUpdateTask.isDone())
            inactiveClanMembersUpdateTask = webExecutorService.submit(this::updateInactiveClanMembers);
    }

    private void removeExpiredClanMembers()
    {
        int removedExpiredMembers = clanMemberDAO.removeExpired();
        if(removedExpiredMembers > 0) LOG.info("Removed {} expired clan members", removedExpiredMembers);
    }

    private ClanMemberEventData extractClanMembers
    (Tuple2<BlizzardLegacyProfile, PlayerCharacterNaturalId> src)
    {
        PlayerCharacter character = (PlayerCharacter) src.getT2();
        Clan clan = src.getT1().getClanName() != null
            ? new Clan
                (
                    null,
                    src.getT1().getClanTag(),
                    character.getRegion(),
                    src.getT1().getClanName()
                )
            : null;
        return new ClanMemberEventData(character, clan, src.getT1().getCreatedAt());
    }

    private void updateInactiveClanMembersBatch(List<PlayerCharacter> clanMembers)
    {
        List<Future<Void>> dbTasks = new ArrayList<>();
        Flux.fromIterable
        (
            clanMembers.stream()
                .collect(Collectors.groupingBy(PlayerCharacter::getRegion))
                .entrySet()
        )
            .flatMap(entry->api.getLegacyProfiles
            (
                entry.getValue(),
                alternativeLadderService.isProfileLadderWebRegion(entry.getKey())
                    || alternativeLadderService.isDiscoveryWebRegion(entry.getKey())
            ))
            .map(this::extractClanMembers)
            .buffer(INACTIVE_CLAN_MEMBER_BATCH_SIZE)
            .toStream()
            .forEach(profiles->dbTasks.add(dbExecutorService.submit(()->
                clanService.saveClans(profiles), null)));
        MiscUtil.awaitAndLogExceptions(dbTasks, true);
        LOG.info("Updated {} inactive clan members", clanMembers.size());
    }

    private void updateInactiveClanMembers()
    {
        int batchSize = getInactiveClanMembersBatchSize();
        if(batchSize < 1) return;

        OffsetDateTime inactiveTo = OffsetDateTime.now().minus(CLAN_MEMBER_INACTIVE_AFTER);
        List<PlayerCharacter> inactiveMembers = playerCharacterDAO.findInactiveClanMembers
        (
            inactiveTo,
            inactiveClanMembersCursor.getValue(),
            batchSize
        );
        if(inactiveMembers.isEmpty())
        {
            inactiveClanMembersCursor.setValueAndSave(Long.MAX_VALUE);
        }
        else
        {
            updateInactiveClanMembersBatch(inactiveMembers);
            inactiveClanMembersCursor.setValueAndSave(inactiveMembers.get(inactiveMembers.size() - 1).getId());
            inactiveClanMembersUpdated.setValueAndSave(Instant.now());
        }
    }

    @Retryable
    @Transactional
    public void saveClans(Collection<ClanMemberEventData> clanData)
    {
        if(clanData.isEmpty()) return;
        List<ClanMemberEventData> clans = clanData.stream()
            .filter(clanUpdatePredicate)
            .collect(Collectors.toList());
        LOG.debug("Saving clans {}/{}", clans.size(), clanData.size());
        if(clans.isEmpty()) return;

        List<ClanMemberEventData> nonNullClans = clans.stream()
            .filter(p->p.getClan() != null)
            .collect(Collectors.toList());

        Map<Clan, Clan> updatedClans =
            clanDAO.merge(nonNullClans.stream()
                    .map(ClanMemberEventData::getClan)
                    .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Function.identity(), Function.identity()));
        nonNullClans.forEach(c->c.getClan().setId(updatedClans.get(c.getClan()).getId()));

        Set<ClanMember> members = nonNullClans.stream()
            .map(t->new ClanMember(t.getCharacter().getId(), t.getClan().getId()))
            .collect(Collectors.toSet());
        clanMemberDAO.merge(members);

        Set<Long> charactersWithNoClan = clans.stream()
            .filter(c->c.getClan() == null)
            .map(ClanMemberEventData::getCharacter)
            .map(PlayerCharacter::getId)
            .collect(Collectors.toSet());
        clanMemberDAO.remove(charactersWithNoClan);
        createClanEvents(clans);
    }

    private void createClanEvents(Collection<ClanMemberEventData> clans)
    {
        if(clans.isEmpty()) return;

        Set<ClanMemberEvent> events = clans.stream()
            .map(p->ClanMemberEvent.from(p.getCharacter(), p.getClan()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        clanMemberEventDAO.merge(events);
        LOG.debug("Created {} clan events", events.size());
    }

}
