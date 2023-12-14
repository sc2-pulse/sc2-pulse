// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import com.nephest.battlenet.sc2.model.local.SocialMediaUserId;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SocialMediaLinkDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.util.LogUtil;
import com.nephest.battlenet.sc2.util.wrapper.ThreadLocalRandomSupplier;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class CommunityService
{

    private static final Logger LOG = LoggerFactory.getLogger(CommunityService.class);

    public enum Featured
    {
        POPULAR,
        SKILLED,
        SPONSORED,
        RANDOM
    }

    public static final Comparator<VideoStream> STREAM_COMPARATOR
        = Comparator.comparing(VideoStream::getViewerCount).reversed()
            .thenComparing(VideoStream::getId)
            .thenComparing(VideoStream::getService);
    public static final Comparator<LadderTeam> CURRENT_TEAM_COMPARATOR
        = Comparator.comparing(Team::getLastPlayed);
    public static final Duration CURRENT_TEAM_MAX_DURATION_OFFSET = Duration.ofDays(14);
    public static final Predicate<LadderTeam> CURRENT_TEAM_PREDICATE = t->
        t.getLastPlayed() != null
        && Duration.between(t.getLastPlayed(), OffsetDateTime.now())
            .compareTo(CURRENT_TEAM_MAX_DURATION_OFFSET)
            <= 0;
    public static final Duration CURRENT_FEATURED_TEAM_MAX_DURATION_OFFSET = Duration.ofMinutes(40);
    public static final Predicate<LadderTeam> CURRENT_FEATURED_TEAM_PREDICATE = t->
        t != null
        && t.getLastPlayed() != null
        && Duration.between(t.getLastPlayed(), OffsetDateTime.now())
            .compareTo(CURRENT_FEATURED_TEAM_MAX_DURATION_OFFSET)
            <= 0;

    //twitch baseline dimensions
    public static final int STREAM_PROFILE_IMAGE_WIDTH = 50;
    public static final int STREAM_PROFILE_IMAGE_HEIGHT = 50;
    public static final int STREAM_THUMBNAIL_TARGET_WIDTH = 440;
    public static final int STREAM_THUMBNAIL_TARGET_HEIGHT = 248;
    public static final Duration STREAM_CACHE_EXPIRE_AFTER = Duration.ofMinutes(5);
    public static final Duration STREAM_CACHE_REFRESH_AFTER = Duration.ofMinutes(1);
    public static final Duration FEATURED_STREAM_CACHE_EXPIRE_AFTER = Duration.ofMinutes(2);
    public static final int FEATURED_STREAM_SKILLED_SLOT_COUNT = 3;

    private final PlayerCharacterDAO playerCharacterDAO;
    private final SocialMediaLinkDAO socialMediaLinkDAO;
    private final LadderProPlayerDAO ladderProPlayerDAO;
    private final LadderSearchDAO ladderSearchDAO;
    private final ThreadLocalRandomSupplier randomSupplier;
    private final List<VideoStreamSupplier> streamSuppliers;

    private LadderVideoStream currentRandomStream;
    private Instant currentRandomStreamAssigned;
    public static final Duration RANDOM_STREAM_MAX_DURATION = Duration.ofHours(1);

    @Autowired @Lazy
    private CommunityService communityService;

    @Autowired
    public CommunityService
    (
        PlayerCharacterDAO playerCharacterDAO,
        SocialMediaLinkDAO socialMediaLinkDAO,
        LadderProPlayerDAO ladderProPlayerDAO,
        LadderSearchDAO ladderSearchDAO,
        ThreadLocalRandomSupplier randomSupplier,
        List<VideoStreamSupplier> streamSuppliers
    )
    {
        this.playerCharacterDAO = playerCharacterDAO;
        this.socialMediaLinkDAO = socialMediaLinkDAO;
        this.ladderProPlayerDAO = ladderProPlayerDAO;
        this.ladderSearchDAO = ladderSearchDAO;
        this.randomSupplier = randomSupplier;
        this.streamSuppliers = streamSuppliers;
    }

    @Cacheable(cacheNames = "community-video-stream")
    public Mono<CommunityStreamResult> getStreams()
    {
        return getStreamsNoCache();
    }

    public Mono<CommunityStreamResult> getStreamsNoCache()
    {
        Set<SocialMedia> errors = EnumSet.noneOf(SocialMedia.class);
        return Flux.fromIterable(streamSuppliers)
            .flatMap(supplier->WebServiceUtil.getOnErrorLogAndSkipFlux(
                supplier.getStreams(),
                t->errors.add(supplier.getService()),
                t->LogUtil.LogLevel.ERROR
            ))
            .sort(STREAM_COMPARATOR)
            .collectList()
            .flatMap(streams->WebServiceUtil.blockingCallable(()->enrich(streams)))
            .map(streams->new CommunityStreamResult(streams, errors))
            .cache((m)->STREAM_CACHE_EXPIRE_AFTER, (t)->Duration.ZERO, ()->STREAM_CACHE_EXPIRE_AFTER);
    }

    private List<LadderVideoStream> enrich(List<VideoStream> streams)
    {
        Map<VideoStream, SocialMediaUserId> serviceUserIdMap = streams.stream()
            .collect(Collectors.toMap(
                Function.identity(),
                stream->new SocialMediaUserId(stream.getService(), stream.getUserId()),
                (l, r)->r
            ));
        Map<SocialMediaUserId, SocialMediaLink> links = socialMediaLinkDAO
            .findByServiceUserIds(Set.copyOf(serviceUserIdMap.values()))
            .stream()
            .collect(Collectors.toMap(
                link->new SocialMediaUserId(link.getType(), link.getServiceUserId()),
                Function.identity(),
                (l, r)->r
            ));
        Set<Long> proPlayerIds = links.values().stream()
            .map(SocialMediaLink::getProPlayerId)
            .collect(Collectors.toSet());
        Map<Long, LadderProPlayer> proPlayers = ladderProPlayerDAO.findByIds(proPlayerIds)
            .stream()
            .collect(Collectors.toMap(lpp->lpp.getProPlayer().getId(), Function.identity()));
        return streams.stream()
            .map(stream->{
                SocialMediaLink link = links.get(serviceUserIdMap.get(stream));
                LadderProPlayer proPlayer = link != null
                    ? proPlayers.get(link.getProPlayerId())
                    : null;
                LadderTeam team = proPlayer != null
                    ? getStreamerTeam(proPlayer.getProPlayer())
                    : null;
                return new LadderVideoStream(stream, proPlayer, team);
            })
            .collect(Collectors.toList());
    }

    private LadderTeam getStreamerTeam(ProPlayer proPlayer)
    {
        List<Long> charIds = playerCharacterDAO
            .findCharacterIdsByProPlayerIds(Set.of(proPlayer.getId()));
        return ladderSearchDAO.findCharacterTeams(new HashSet<>(charIds)).stream()
            .filter(CURRENT_TEAM_PREDICATE)
            .max(CURRENT_TEAM_COMPARATOR)
            .orElse(null);
    }

    @Cacheable(cacheNames = "community-video-stream-featured")
    public Mono<CommunityStreamResult> getFeaturedStreams()
    {
        return getFeaturedStreamsNoCache();
    }

    public Mono<CommunityStreamResult> getFeaturedStreamsNoCache()
    {
        return communityService.getStreams()
            .map(result->new CommunityStreamResult(
                getFeaturedStreams(result.getStreams()), result.getErrors()))
            .cache((m)->
                FEATURED_STREAM_CACHE_EXPIRE_AFTER,
                (t)->Duration.ZERO,
                ()->FEATURED_STREAM_CACHE_EXPIRE_AFTER
            );
    }

    private List<LadderVideoStream> getFeaturedStreams(List<LadderVideoStream> streams)
    {
        if(streams.isEmpty()) return List.of();

        streams.forEach(stream->stream.setFeatured(null));
        List<LadderVideoStream> streamsRemaining = new ArrayList<>(streams);
        LadderVideoStream mostViewed = streamsRemaining.stream()
            .max(Comparator.comparing((LadderVideoStream stream)->stream.getStream().getViewerCount())
                .thenComparing((LadderVideoStream stream)->stream.getStream().getId())
                .thenComparing((LadderVideoStream stream)->stream.getStream().getService()))
            .orElse(null);
        if(mostViewed == null) return List.of();
        streamsRemaining.remove(mostViewed);

        List<LadderVideoStream> mostSkilled
            = getMostSkilledStream(streamsRemaining, FEATURED_STREAM_SKILLED_SLOT_COUNT);
        mostSkilled.forEach(s->s.setFeatured(Featured.SKILLED));
        streamsRemaining.removeAll(mostSkilled);

        LadderVideoStream randomStream = getSameOrNewRandomStream(streamsRemaining);

        List<LadderVideoStream> featured = new ArrayList<>(3);
        mostViewed.setFeatured(Featured.POPULAR);
        featured.add(mostViewed);
        featured.addAll(mostSkilled);
        if(randomStream != null)
        {
            randomStream.setFeatured(Featured.RANDOM);
            featured.add(randomStream);
        }
        return featured;
    }

    private List<LadderVideoStream> getMostSkilledStream(List<LadderVideoStream> streams, int limit)
    {
        return streams.stream()
            .filter(stream->CURRENT_FEATURED_TEAM_PREDICATE.test(stream.getTeam()))
            .sorted(Comparator.comparing((LadderVideoStream stream)->stream.getTeam().getRating())
                .reversed()
                .thenComparing((LadderVideoStream stream)->stream.getStream().getId())
                .thenComparing((LadderVideoStream stream)->stream.getStream().getService()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    private LadderVideoStream getSameOrNewRandomStream(List<LadderVideoStream> streams)
    {
        LadderVideoStream updatedCurrentStream;
        if
        (
            currentRandomStream != null
                && Duration.between(currentRandomStreamAssigned, Instant.now())
                    .compareTo(RANDOM_STREAM_MAX_DURATION) < 0
                &&
                (
                    updatedCurrentStream = streams.stream()
                        .filter(s->s.getStream().equals(currentRandomStream.getStream()))
                        .findAny()
                        .orElse(null)
                ) != null
                && CURRENT_FEATURED_TEAM_PREDICATE.test(updatedCurrentStream.getTeam())
        )
        {
            currentRandomStream = updatedCurrentStream;
            LOG.trace
            (
                "Same random featured stream: {}, user {}, assigned {}",
                currentRandomStream.getStream().getId(),
                currentRandomStream.getStream().getUserId(),
                currentRandomStreamAssigned
            );
            return currentRandomStream;
        }

        currentRandomStream = getRandomStream(streams);
        currentRandomStreamAssigned = Instant.now();
        if(currentRandomStream != null)
        {
            LOG.trace
            (
                "New random featured stream: {}, user {}",
                currentRandomStream.getStream().getId(),
                currentRandomStream.getStream().getUserId()
            );
        }
        else
        {
            LOG.trace("Random featured stream not found");
        }
        return currentRandomStream;
    }

    private LadderVideoStream getRandomStream(List<LadderVideoStream> streams)
    {
        if(streams.isEmpty()) return null;

        List<LadderVideoStream> eligibleStreams = streams.stream()
            .filter(stream->CURRENT_FEATURED_TEAM_PREDICATE.test(stream.getTeam()))
            .collect(Collectors.toList());
        return  eligibleStreams.isEmpty()
            ? null
            : eligibleStreams.get(randomSupplier.get().nextInt(eligibleStreams.size()));
    }

    public Instant getCurrentRandomStreamAssigned()
    {
        return currentRandomStreamAssigned;
    }

    protected void setCurrentRandomStreamAssigned(Instant currentRandomStreamAssigned)
    {
        this.currentRandomStreamAssigned = currentRandomStreamAssigned;
    }

    protected void resetRandomFeaturedStream()
    {
        currentRandomStream = null;
        currentRandomStreamAssigned = null;
    }

}
