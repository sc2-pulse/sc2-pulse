// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.discord.GuildRoleStore;
import com.nephest.battlenet.sc2.discord.IdentifiableEntity;
import com.nephest.battlenet.sc2.discord.PulseMappings;
import com.nephest.battlenet.sc2.discord.connection.ApplicationRoleConnection;
import com.nephest.battlenet.sc2.discord.connection.PulseConnectionParameters;
import com.nephest.battlenet.sc2.discord.event.RolesSlashCommand;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.AccountDiscordUser;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountDiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.service.EventService;
import com.nephest.battlenet.sc2.util.MiscUtil;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
@Discord
public class DiscordService
{

    private static final Logger LOG = LoggerFactory.getLogger(DiscordService.class);

    public static final int DB_CURSOR_BATCH_SIZE = 1000;
    public static final int USER_UPDATE_BATCH_SIZE = 200;
    public static final int MAIN_TEAM_SEASON_DEPTH = 3;
    public static final Set<QueueType> MAIN_TEAM_QUEUE_TYPES = Set.of(QueueType.LOTV_1V1);
    public static final Comparator<LadderTeam> MAIN_TEAM_COMPARATOR = Comparator
        .comparing(LadderTeam::getSeason).reversed()
        .thenComparing(LadderTeam::getQueueType)
        .thenComparing(Comparator.comparingLong(LadderTeam::getRating).reversed())
        .thenComparing(LadderTeam::getId);

    private final DiscordUserDAO discordUserDAO;
    private final AccountDiscordUserDAO accountDiscordUserDAO;
    private final SeasonDAO seasonDAO;
    private final AccountDAO accountDAO;
    private final PlayerCharacterDAO playerCharacterDAO;
    private final LadderSearchDAO ladderSearchDAO;
    private DiscordAPI discordAPI;
    private final GuildRoleStore guildRoleStore;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final PulseConnectionParameters pulseConnectionParameters;
    private final ExecutorService dbExecutorService;
    private final ConversionService conversionService;

    @Autowired @Lazy
    private DiscordService discordService;

    @Autowired @Lazy
    private RolesSlashCommand rolesSlashCommand;

    public enum RoleUpdateMode
    {
        UPDATE(RolesSlashCommand.UPDATE_REASON),
        DROP("User has unlinked their Discord account"),
        REVOKE("User has revoked their Discord permissions");

        private final String reason;

        RoleUpdateMode(String reason)
        {
            this.reason = reason;
        }

        public String getReason()
        {
            return reason;
        }

    }

    @Autowired
    public DiscordService
    (
        DiscordUserDAO discordUserDAO,
        AccountDiscordUserDAO accountDiscordUserDAO,
        SeasonDAO seasonDAO,
        AccountDAO accountDAO,
        PlayerCharacterDAO playerCharacterDAO,
        LadderSearchDAO ladderSearchDAO,
        DiscordAPI discordAPI,
        GuildRoleStore guildRoleStore,
        OAuth2AuthorizedClientService oAuth2AuthorizedClientService,
        EventService eventService,
        PulseConnectionParameters pulseConnectionParameters,
        @Qualifier("dbExecutorService") ExecutorService dbExecutorService,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.discordUserDAO = discordUserDAO;
        this.accountDiscordUserDAO = accountDiscordUserDAO;
        this.seasonDAO = seasonDAO;
        this.accountDAO = accountDAO;
        this.playerCharacterDAO = playerCharacterDAO;
        this.ladderSearchDAO = ladderSearchDAO;
        this.pulseConnectionParameters = pulseConnectionParameters;
        this.discordAPI = discordAPI;
        this.guildRoleStore = guildRoleStore;
        this.oAuth2AuthorizedClientService = oAuth2AuthorizedClientService;
        this.dbExecutorService = dbExecutorService;
        this.conversionService = conversionService;
        eventService.getLadderCharacterActivityEvent()
            .publishOn(Schedulers.boundedElastic())
            .flatMap(c->updateRoles(c.getAccountId()))
            .subscribe();
    }

    protected DiscordAPI getDiscordAPI()
    {
        return discordAPI;
    }

    protected void setDiscordAPI(DiscordAPI discordAPI)
    {
        this.discordAPI = discordAPI;
    }

    protected DiscordService getDiscordService()
    {
        return discordService;
    }

    protected void setDiscordService(DiscordService discordService)
    {
        this.discordService = discordService;
    }

    protected RolesSlashCommand getRolesSlashCommand()
    {
        return rolesSlashCommand;
    }

    protected void setRolesSlashCommand(RolesSlashCommand rolesSlashCommand)
    {
        this.rolesSlashCommand = rolesSlashCommand;
    }

    /**
     * Remove existing connections and link the ids afterwards within the same transaction.
     *
     * @param accountId accountId
     * @param discordUserId discordUserId to link with
     */
    @Transactional
    public void linkAccountToDiscordUser(Long accountId, Snowflake discordUserId)
    {
        accountDiscordUserDAO.remove(accountId, discordUserId);
        accountDiscordUserDAO.create(Set.of(new AccountDiscordUser(accountId, discordUserId)));
    }

    @Transactional
    public void linkAccountToNewDiscordUser(Long accountId, DiscordUser discordUser)
    {
        discordUserDAO.merge(Set.of(discordUser));
        discordService.linkAccountToDiscordUser(accountId, discordUser.getId());
    }

    public void unlinkAccountFromDiscordUser(Long accountId, Snowflake discordUserId)
    {
        discordAPI.getAuthorizedClient(accountId)
            .ifPresent(oAuth2Client -> {
                dropRoles(oAuth2Client).blockLast();
                discordAPI.revokeRefreshToken(oAuth2Client).blockLast();
            });
        discordService.unlinkAccountFromDiscordUserDB(accountId, discordUserId);
    }

    @Transactional
    public void unlinkAccountFromDiscordUserDB(Long accountId, Snowflake discordUserId)
    {
        accountDiscordUserDAO.remove(accountId, discordUserId);
        oAuth2AuthorizedClientService
            .removeAuthorizedClient(DiscordAPI.USER_CLIENT_REGISTRATION_ID, String.valueOf(accountId));
    }

    public Set<Long> getUsersWithoutOauth2Permissions()
    {
        return accountDiscordUserDAO.findAccountIds().stream()
            .map(id->new ImmutablePair<Long, OAuth2AuthorizedClient>(
                id,
                oAuth2AuthorizedClientService.loadAuthorizedClient(
                    DiscordAPI.USER_CLIENT_REGISTRATION_ID, String.valueOf(id))))
            .filter(pair->pair.getRight() == null)
            .map(Pair::getKey)
            .collect(Collectors.toSet());
    }

    public void updateRolesAndUnlinkUsersWithoutOauth2Permissions()
    {
        Set<Long> accountIds = getUsersWithoutOauth2Permissions();
        if(accountIds.isEmpty()) return;

        Flux.fromIterable(accountIds)
            .flatMap(id->updateRoles(null, RoleUpdateMode.REVOKE))
            .blockLast();
        discordService.unlinkUsers(accountIds);
        LOG.info("Cleared {} discord users without a corresponding oauth2 client", accountIds.size());
    }

    @Transactional
    public void unlinkUsers(Set<Long> accountIds)
    {
        accountIds.forEach(id->accountDiscordUserDAO.remove(id, null));
    }

    public void setVisibility(Long accountId, boolean isVisible)
    {
        accountDiscordUserDAO.updatePublicFlag(accountId, isVisible);
    }

    public void update()
    {
        updateRolesAndUnlinkUsersWithoutOauth2Permissions();
        removeUsersWithNoAccountLinked();
        updateUsersFromAPI();
    }

    private void removeUsersWithNoAccountLinked()
    {
        int removed = discordUserDAO.removeUsersWithNoAccountLinked();
        if(removed > 0) LOG.info("Removed {} empty discord users", removed);
    }

    private int updateUsersFromAPI()
    {
        Snowflake idCursor = Snowflake.of(0);
        int count = 0;
        while(true)
        {
            List<Snowflake> toUpdate = discordUserDAO.findIdsByIdCursor(idCursor, DB_CURSOR_BATCH_SIZE);
            if(toUpdate.isEmpty()) break;

            List<Future<Void>> tasks = new ArrayList<>();
            discordAPI.getUsers(toUpdate)
                .buffer(USER_UPDATE_BATCH_SIZE)
                .toStream()
                .forEach(batch->tasks.add(
                    dbExecutorService.submit(()->updateUsers(Set.copyOf(batch)), null)));
            MiscUtil.awaitAndThrowException(tasks, true, true);

            count += toUpdate.size();
            idCursor = toUpdate.get(toUpdate.size() - 1);
        }

        if(count > 0) LOG.info("Updated {} discord users", count);
        return count;
    }

    private void updateUsers(Set<DiscordUser> users)
    {
        discordUserDAO.merge(users);
    }

    public Optional<LadderTeam> findMainTeam(Long accountId)
    {
        List<PlayerCharacter> characters = playerCharacterDAO.findByAccountId(accountId);
        if(characters.isEmpty()) return Optional.empty();

        int curSeason = seasonDAO.getMaxBattlenetId();
        Set<Integer> seasons = IntStream
            .rangeClosed(curSeason - MAIN_TEAM_SEASON_DEPTH + 1, curSeason)
            .boxed()
            .collect(Collectors.toSet());
        return characters.stream()
            .flatMap(c->ladderSearchDAO
                .findCharacterTeams(c.getId(), seasons, MAIN_TEAM_QUEUE_TYPES, 1).stream())
            .min(MAIN_TEAM_COMPARATOR);
    }

    public Flux<Void> updateRoles(Long accountId)
    {
        if(!accountDiscordUserDAO.findAccountIds().contains(accountId)) return Flux.empty();
        return updateRoles(discordAPI.getAuthorizedClient(accountId).orElse(null));
    }

    private Flux<Void> updateRoles(@Nullable OAuth2AuthorizedClient oAuth2Client)
    {
        return updateRoles(oAuth2Client, RoleUpdateMode.UPDATE);
    }

    private Flux<Void> dropRoles(@Nullable OAuth2AuthorizedClient oAuth2Client)
    {
        return updateRoles(oAuth2Client, RoleUpdateMode.DROP);
    }

    protected Flux<Void> updateRoles
    (
        @Nullable OAuth2AuthorizedClient oAuth2Client,
        RoleUpdateMode mode
    )
    {
        //user tries to drop revoked connection, revoke it instead
        RoleUpdateMode finalMode = mode == RoleUpdateMode.DROP && oAuth2Client == null
            ? RoleUpdateMode.REVOKE
            : mode;
        if(finalMode == RoleUpdateMode.REVOKE) return Flux.empty();
        if(oAuth2Client == null) return
            Flux.error(new IllegalStateException("Oauth2AuthorizedClient not found"));
        Long accountId = Long.valueOf(oAuth2Client.getPrincipalName());

        Tuple2<LadderTeam, LadderTeamMember> mainTuple = finalMode != RoleUpdateMode.UPDATE
            ? null
            : findMainTuple(accountId);
        ApplicationRoleConnection roleConnection = mainTuple != null
            ? 
                ApplicationRoleConnection.from
                (
                    mainTuple.getT1(),
                    mainTuple.getT2().getAccount().getBattleTag(),
                    mainTuple.getT2().getFavoriteRace(),
                    pulseConnectionParameters.getParameters(),
                    conversionService
                )
            : ApplicationRoleConnection.empty(accountDAO.findByIds(Set.of(accountId)).get(0).getBattleTag());

        return Flux.concat
        (
            discordAPI.updateConnectionMetaData(oAuth2Client, roleConnection),
            getManagedRoleGuilds(oAuth2Client)
                .flatMap
                (
                    guild->getMemberMappings
                    (
                        guild,
                        discordUserDAO.findByAccountId(accountId, false)
                            .orElseThrow()
                            .getId()
                    )
                )
                .flatMap
                (
                    t->rolesSlashCommand.updateRoles
                    (
                        mainTuple != null ? mainTuple.getT1() : null,
                        mainTuple != null ? mainTuple.getT2().getFavoriteRace() : null,
                        t.getT2(),
                        t.getT1(),
                        finalMode.getReason()
                    ).getRight()
                )
        )
        .onErrorResume
        (
            t->finalMode != RoleUpdateMode.UPDATE && WebServiceUtil.isOauth2ClientMissing(t),
            t->Flux.empty()
        )
        .subscribeOn(Schedulers.boundedElastic());
    }

    public Tuple2<LadderTeam, LadderTeamMember> findMainTuple(Long accountId)
    {
        LadderTeam mainTeam = findMainTeam(accountId).orElse(null);
        if(mainTeam == null) return null;

        LadderTeamMember member = mainTeam.getMembers().stream()
            .filter(m->Objects.equals(m.getAccount().getId(), accountId))
            .findFirst()
            .orElseThrow();
        return Tuples.of(mainTeam, member);
    }

    public Flux<Guild> getManagedRoleGuilds(OAuth2AuthorizedClient oAuth2Client)
    {
        return getManagedRoleGuilds(discordAPI.getGuilds(oAuth2Client, IdentifiableEntity.class));
    }

    public Flux<Guild> getManagedRoleGuilds(Flux<? extends IdentifiableEntity> guilds)
    {
        return guilds
            .map(IdentifiableEntity::getId)
            .filter(discordAPI.getBotGuilds().keySet()::contains)
            .flatMap(id->discordAPI.getDiscordClient().getClient().getGuildById(id))
            .filterWhen(guild->DiscordBootstrap.haveSelfPermissions(guild, RolesSlashCommand.REQUIRED_PERMISSIONS))
            .filterWhen(guild->guildRoleStore.getManagedRoleMappings(guild).map(mappings->!mappings.isEmpty()));
    }

    private Mono<Tuple2<Member, PulseMappings<Role>>> getMemberMappings
    (
        Guild guild,
        Snowflake memberId
    )
    {
        return guild.getMemberById(memberId)
            .zipWith(guildRoleStore.getManagedRoleMappings(guild));
    }

}
