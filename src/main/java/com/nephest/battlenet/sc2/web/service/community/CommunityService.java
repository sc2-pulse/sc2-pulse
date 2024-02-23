// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.TeamFormat;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    private static final Comparator<LadderVideoStream> STREAM_VIEWERS_COMPARATOR =
        Comparator.<LadderVideoStream, Integer>
            comparing(s->s.getStream().getViewerCount(), Comparator.reverseOrder())
            .thenComparing(s->s.getStream().getService())
            .thenComparing(s->s.getStream().getId());

    public enum StreamSorting
    {

        VIEWERS(STREAM_VIEWERS_COMPARATOR),
        RATING(Comparator.<LadderVideoStream, Long>comparing(
            s->s.getTeam() != null ? s.getTeam().getRating() : null,
            Comparator.nullsLast(Comparator.reverseOrder())
        )
            .thenComparing(STREAM_VIEWERS_COMPARATOR)),
        TOP_PERCENT_REGION(Comparator.<LadderVideoStream, Float>comparing(
            s->s.getTeam() != null
                && s.getTeam().getRegionRank() != null
                && s.getTeam().getPopulationState() != null
                && s.getTeam().getPopulationState().getRegionTeamCount() != null
                    ? (s.getTeam().getRegionRank()
                        / s.getTeam().getPopulationState().getRegionTeamCount().floatValue())
                            * 100
                    : null,
            Comparator.nullsLast(Comparator.naturalOrder())
        )
            .thenComparing(STREAM_VIEWERS_COMPARATOR));

        private final Comparator<LadderVideoStream> comparator;

        StreamSorting(Comparator<LadderVideoStream> comparator)
        {
            this.comparator = comparator;
        }

        public Comparator<LadderVideoStream> getComparator()
        {
            return comparator;
        }

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
    public static final int FEATURED_STREAM_SKILLED_SLOT_COUNT = 5;

    private final PlayerCharacterDAO playerCharacterDAO;
    private final SocialMediaLinkDAO socialMediaLinkDAO;
    private final LadderProPlayerDAO ladderProPlayerDAO;
    private final LadderSearchDAO ladderSearchDAO;
    private final ThreadLocalRandomSupplier randomSupplier;
    private final List<VideoStreamSupplier> streamSuppliers;
    private final Set<SocialMedia> streamServices;

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
        this.streamServices = Collections.unmodifiableSet(streamSuppliers.stream()
            .map(VideoStreamSupplier::getService)
            .collect(Collectors.toCollection(()->EnumSet.noneOf(SocialMedia.class))));
    }

    public Set<SocialMedia> getStreamServices()
    {
        return streamServices;
    }

    @Cacheable(cacheNames = "community-video-stream")
    public Mono<CommunityStreamResult> getStreams()
    {
        return getStreamsNoCache();
    }

    public Mono<CommunityStreamResult> getStreams
    (
        Set<SocialMedia> services,
        Comparator<LadderVideoStream> comparator,
        boolean identifiedOnly,
        Set<Race> races,
        Set<Locale> languages,
        Set<TeamFormat> teamFormats,
        Integer ratingMin, Integer ratingMax,
        Integer limit, Integer limitPlayer,
        boolean lax
    )
    {
        if(!getStreamServices().containsAll(services))
            return Mono.error(new IllegalArgumentException("Unsupported service"));

        return communityService.getStreams()
            .map(result->new CommunityStreamResult(
                getStreams
                (
                    result.getStreams().stream(),
                    services,
                    comparator,
                    identifiedOnly,
                    races,
                    languages,
                    teamFormats,
                    ratingMin, ratingMax,
                    limit, limitPlayer,
                    lax
                )
                    .collect(Collectors.toList()),
                result.getErrors().isEmpty()
                    ? result.getErrors()
                    : result.getErrors().stream()
                        .filter(services::contains)
                        .collect(Collectors.toSet())
            ));
    }

    public Stream<LadderVideoStream> getStreams
    (
        Stream<LadderVideoStream> streams,
        Set<SocialMedia> services,
        Comparator<LadderVideoStream> comparator,
        boolean identifiedOnly,
        Set<Race> races,
        Set<Locale> languages,
        Set<TeamFormat> teamFormats,
        Integer ratingMin, Integer ratingMax,
        Integer limit, Integer limitPlayer,
        boolean lax
    )
    {
        if(!services.isEmpty() && !services.containsAll(getStreamServices())) streams
            = streams.filter(stream->services.contains(stream.getStream().getService()));
        if(identifiedOnly) streams = streams
            .filter(s->CURRENT_FEATURED_TEAM_PREDICATE.test(s.getTeam()));
        if(!languages.isEmpty())
        {
            Set<String> languageCodes = languages.stream()
                .map(Locale::getLanguage)
                .collect(Collectors.toSet());
            streams = streams.filter
            (
                s->lax
                    ? s.getStream().getLanguage() == null || containsLanguage(s, languageCodes)
                    : containsLanguage(s, languageCodes)
            );
        }
        if(!races.isEmpty()) streams = streams
            .filter
            (
                s->lax
                    ? s.getTeam() == null || containsFavoriteRace(s, races)
                    : containsFavoriteRace(s, races)
            );
        if(ratingMin != null) streams = streams
            .filter
            (
                s->lax
                    ? s.getTeam() == null || s.getTeam().getRating() >= ratingMin
                    : s.getTeam() != null && s.getTeam().getRating() >= ratingMin
            );
        if(ratingMax != null) streams = streams
            .filter
            (
                s->lax
                    ? s.getTeam() == null || s.getTeam().getRating() <= ratingMax
                    : s.getTeam() != null && s.getTeam().getRating() <= ratingMax
            );
        if(!teamFormats.isEmpty()) streams = streams
            .filter
            (
                s->lax
                    ? s.getTeam() == null || containsTeamFormat(s, teamFormats)
                    : containsTeamFormat(s, teamFormats)
            );
        if(comparator != null) streams = streams.sorted(comparator);
        if(limitPlayer != null) streams = limitPlayers(streams, limitPlayer);
        if(limit != null) streams = streams.limit(limit);
        return streams;
    }

    private static Stream<LadderVideoStream> limitPlayers
    (
        Stream<LadderVideoStream> streams,
        int limit
    )
    {
        Set<Long> proPlayers = new HashSet<>();
        AtomicInteger anonymousStreams = new AtomicInteger(0);
        return streams.filter(s->
        {
            int count = proPlayers.size() + anonymousStreams.get();
            if(s.getProPlayer() != null)
            {
                if(proPlayers.contains(s.getProPlayer().getProPlayer().getId())) return true;
                if(count < limit)
                {
                    proPlayers.add(s.getProPlayer().getProPlayer().getId());
                    return true;
                }
                return false;
            } else if(count < limit)
            {
                anonymousStreams.incrementAndGet();
                return true;
            }
            return false;
        });
    }

    private static boolean containsFavoriteRace(LadderVideoStream stream, Set<Race> races)
    {
        return stream.getTeam() != null
            && stream.getTeam().getMembers().stream()
                .anyMatch(m->races.contains(m.getFavoriteRace()));
    }

    private static boolean containsLanguage(LadderVideoStream stream, Set<String> languages)
    {
        return stream.getStream().getLanguage() != null
            && languages.contains(stream.getStream().getLanguage().getLanguage());
    }

    private static boolean containsTeamFormat(LadderVideoStream stream, Set<TeamFormat> formats)
    {
        return stream.getTeam() != null
            && formats.contains(stream.getTeam().getQueueType().getTeamFormat());
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

    public Mono<CommunityStreamResult> getFeaturedStreams(Set<SocialMedia> services)
    {
        if(!getStreamServices().containsAll(services))
            return Mono.error(new IllegalArgumentException("Unsupported service"));

        if(services.isEmpty() || services.containsAll(getStreamServices()))
            return communityService.getFeaturedStreams();

        return communityService.getStreams
            (
                services,
                StreamSorting.RATING.getComparator(),
                true,
                Set.of(),
                Set.of(),
                Set.of(),
                null, null,
                FEATURED_STREAM_SKILLED_SLOT_COUNT,
                FEATURED_STREAM_SKILLED_SLOT_COUNT,
                false
            )
            .map(result->new CommunityStreamResult(
                getFeaturedStreams(result.getStreams()),
                result.getErrors().isEmpty()
                    ? result.getErrors()
                    : result.getErrors().stream()
                        .filter(services::contains)
                        .collect(Collectors.toSet())
            ));
    }

    private List<LadderVideoStream> getFeaturedStreams(List<LadderVideoStream> streams)
    {
        if(streams.isEmpty()) return List.of();

        streams.forEach(stream->stream.setFeatured(null));
        List<LadderVideoStream> foldedStreams = Stream.concat
        (
            streams.stream()
                .filter(stream->stream.getProPlayer() == null),
            streams.stream()
                .filter(stream->stream.getProPlayer() != null)
                .collect(Collectors.toMap(
                    stream->stream.getProPlayer().getProPlayer().getId(),
                    Function.identity(),
                    (l, r)->l,
                    HashMap::new
                ))
                .values()
                .stream()
        )
            .sorted(Comparator.comparing(LadderVideoStream::getStream, STREAM_COMPARATOR))
            .collect(Collectors.toList());

        List<LadderVideoStream> mostSkilled
            = getMostSkilledStream(foldedStreams, FEATURED_STREAM_SKILLED_SLOT_COUNT);
        mostSkilled.forEach(s->s.setFeatured(Featured.SKILLED));
        return mostSkilled;
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
