// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.local.Donation;
import com.nephest.battlenet.sc2.model.local.DonationPlatform;
import com.nephest.battlenet.sc2.model.local.PatreonDonationState;
import com.nephest.battlenet.sc2.model.local.dao.DonationRepository;
import com.nephest.battlenet.sc2.model.local.dao.PatreonDonationStateRepository;
import com.nephest.battlenet.sc2.model.patreon.PatreonDocument;
import com.nephest.battlenet.sc2.model.patreon.PatreonMember;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import javax.money.Monetary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records Patreon donations from member webhooks.
 * <p>
 * Member webhooks contain membership snapshots instead of payments, so
 * donations are derived from {@code campaign_lifetime_support_cents} deltas.
 * It is the only documented amount in a known currency: the campaign's
 * currency. Other amounts are in the patron's currency, which is not exposed
 * in webhooks.
 * </p>
 * <p>
 * The campaign currency can be missing too, so it is configured via an app
 * property. Includes depend on webhook scopes, so the included campaign
 * currency is only used to log a warning when it doesn't match the configured
 * currency.
 * </p>
 * <p>
 * Members without lifetime support, e.g. members that haven't been charged
 * yet, are ignored. Charge statuses are not checked.
 * </p>
 * <p>
 * The first donation of a patron is the full lifetime amount if the pledge
 * started after the pledge start cutoff, i.e. after the integration went live.
 * Otherwise, the lifetime amount contains donations made before the
 * integration, so the state is seeded and the donation is skipped.
 * </p>
 * <p>
 * Known quirks:
 * </p>
 * <ul>
 *     <li>Campaign currency change. The campaign currency property must be
 *     updated at the same time as the Patreon campaign currency. Events
 *     received in between are labeled with the old currency, so their deltas
 *     are wrong. Lifetime amounts in different currencies can't be compared,
 *     so the state is rebased to the new currency and the donation is skipped.
 *     A payment that is made between the currency change and the next member
 *     event is not recorded.</li>
 *     <li>Returning patrons. A former patron starts a new pledge chain after
 *     the cutoff, but the lifetime amount contains the previous pledges too,
 *     so the whole history is recorded as one donation.</li>
 *     <li>Missed webhooks. A delta covers all payments since the last
 *     processed event, so several payments may be recorded as one
 *     donation.</li>
 *     <li>Refunds are not reflected.</li>
 *     <li>Amounts are gross, Patreon fees are not exposed.</li>
 *     <li>Gift memberships. The actual behavior is unknown, Patreon doesn't
 *     document how gifts are reflected in member data. Gift recipients
 *     presumably have no lifetime support, so they are ignored. The gifter's
 *     payment is not exposed in member webhooks, so gifts are presumably not
 *     recorded. A recipient that starts paying later may keep the gift's
 *     pledge start, so its first payment may be skipped if the gift started
 *     before the cutoff.</li>
 * </ul>
 * <p>
 * Pledge history from the Patreon API is a better source because it contains
 * exact payments with their currencies and statuses. Webhooks are used
 * instead to protect the Patreon manager's privacy: the API requires a creator
 * access token that has all API scopes, including the creator's personal data
 * and the members' personal data.
 * </p>
 */
@Service
@ConditionalOnProperty
(
    name = "com.nephest.battlenet.sc2.donation.webhook.patreon.secret"
)
public class PatreonDonationService
{

    public enum Result
    {
        ACCEPTED,
        IGNORED,
        DUPLICATE
    }

    private record Payload
    (
        @NotBlank String memberId,
        @NotBlank String donorId,
        String donorName,
        @NotBlank String currency,
        @NotNull @Positive Long lifetimeMinorUnits,
        OffsetDateTime pledgeRelationshipStart,
        @NotNull OffsetDateTime created
    )
    {}

    private static final Logger LOG = LoggerFactory
        .getLogger(PatreonDonationService.class);
    private static final Set<String> MEMBER_EVENTS = Set.of
    (
        "members:create",
        "members:update",
        "members:pledge:create",
        "members:pledge:update"
    );
    private static final TypeReference<PatreonDocument<PatreonMember>>
        MEMBER_DOCUMENT_TYPE = new TypeReference<>(){};

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final DonationService donationService;
    private final DonationRepository donationRepository;
    private final PatreonDonationStateRepository stateRepository;
    private final OffsetDateTime pledgeStartCutoff;
    private final String campaignCurrency;

    public PatreonDonationService
    (
        ObjectMapper objectMapper,
        Validator validator,
        DonationService donationService,
        DonationRepository donationRepository,
        PatreonDonationStateRepository stateRepository,
        @Value
        (
            "${com.nephest.battlenet.sc2.donation.webhook.patreon"
                + ".pledge-start-cutoff}"
        )
        String pledgeStartCutoff,
        @Value
        (
            "${com.nephest.battlenet.sc2.donation.webhook.patreon"
                + ".campaign-currency}"
        )
        String campaignCurrency
    )
    {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.donationService = donationService;
        this.donationRepository = donationRepository;
        this.stateRepository = stateRepository;
        this.pledgeStartCutoff = SC2Pulse.offsetDateTime(OffsetDateTime.parse(
            pledgeStartCutoff
        ));
        this.campaignCurrency = Monetary.getCurrency(campaignCurrency)
            .getCurrencyCode();
    }

    /**
     * Processes Patreon webhook. Each attempt runs in a new transaction,
     * concurrent updates of the same donor are retried.
     *
     * @param event Patreon webhook event
     * @param body JSON:API payload
     * @return processing result
     */
    @Retryable
    (
        retryFor =
        {
            DataIntegrityViolationException.class,
            OptimisticLockingFailureException.class
        }
    )
    @Transactional
    public Result process(String event, byte[] body)
    {
        if(!MEMBER_EVENTS.contains(event)) return Result.IGNORED;

        PatreonDocument<PatreonMember> document = read
        (
            body,
            MEMBER_DOCUMENT_TYPE
        );
        if(!hasLifetimeSupport(document)) return Result.IGNORED;

        checkCampaignCurrency(document);
        return save(validate(toPayload(document)));
    }

    private Result save(Payload payload)
    {
        PatreonDonationState state = stateRepository
            .findById(payload.memberId())
            .orElse(null);
        BigDecimal lifetimeAmount = majorUnits
        (
            payload.lifetimeMinorUnits(),
            payload.currency()
        );
        String donationId = payload.memberId()
            + ":" + payload.currency()
            + ":" + payload.lifetimeMinorUnits();
        if
        (
            donationRepository
                .existsByPlatformRefPlatformAndPlatformRefDonationId
                (
                    DonationPlatform.PATREON,
                    donationId
                )
        )
        {
            LOG.warn
            (
                "Skipping duplicate Patreon donation: donorId={}, "
                    + "donationId={}",
                payload.donorId(),
                donationId
            );
            return Result.DUPLICATE;
        }

        BigDecimal amount;
        if(state == null)
        {
            state = new PatreonDonationState
            (
                payload.memberId(),
                payload.currency(),
                lifetimeAmount
            );
            if(!isNewPledge(payload))
            {
                LOG.info
                (
                    "Skipping Patreon donation, pledge started before the "
                        + "cutoff, seeding state: memberId={}, "
                        + "pledgeRelationshipStart={}, lifetime={} {}",
                    payload.memberId(),
                    payload.pledgeRelationshipStart(),
                    lifetimeAmount,
                    payload.currency()
                );
                stateRepository.save(state);
                return Result.IGNORED;
            }
            amount = lifetimeAmount;
        }
        else if(!Objects.equals(state.getCurrency(), payload.currency()))
        {
            LOG.info
            (
                "Skipping Patreon donation, campaign currency changed, "
                    + "rebasing state: memberId={}, currency={}->{}, "
                    + "lifetime={}",
                payload.memberId(),
                state.getCurrency(),
                payload.currency(),
                lifetimeAmount
            );
            state.setCurrency(payload.currency());
            state.setLifetimeAmount(lifetimeAmount);
            stateRepository.save(state);
            return Result.IGNORED;
        }
        else
        {
            if(lifetimeAmount.compareTo(state.getLifetimeAmount()) <= 0)
            {
                LOG.warn
                (
                    "Skipping duplicate Patreon data: memberId={}, "
                        + "incomingLifetime={}, storedLifetime={}",
                    payload.memberId(),
                    lifetimeAmount,
                    state.getLifetimeAmount()
                );
                return Result.DUPLICATE;
            }
            amount = lifetimeAmount.subtract(state.getLifetimeAmount());
            state.setLifetimeAmount(lifetimeAmount);
        }

        Donation donation = donationService.create
        (
            DonationPlatform.PATREON,
            donationId,
            payload.donorId(),
            payload.donorName(),
            amount,
            payload.currency(),
            payload.created()
        );
        stateRepository.save(state);
        donationRepository.save(donation);
        return Result.ACCEPTED;
    }

    private <T> T read(byte[] body, TypeReference<T> type)
    {
        try
        {
            return objectMapper.readValue(body, type);
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Invalid Patreon payload", ex);
        }
    }

    private Payload validate(Payload payload)
    {
        var violations = validator.validate(payload);
        if(!violations.isEmpty())
            throw new ConstraintViolationException(violations);

        return payload;
    }

    /**
     * @param payload incoming payload
     * @return true if the pledge started after the cutoff, false if it
     * started before the cutoff or its start is unknown
     */
    private boolean isNewPledge(Payload payload)
    {
        return payload.pledgeRelationshipStart() != null
            && payload.pledgeRelationshipStart().isAfter(pledgeStartCutoff);
    }

    /**
     * Donations are derived from lifetime support only, charge statuses are
     * not checked. Declined charges don't increase lifetime support, and
     * lower lifetime support is skipped as duplicate data.
     *
     * @param document member document
     * @return true if the member has paid anything to the campaign
     */
    private static boolean hasLifetimeSupport
    (
        PatreonDocument<PatreonMember> document
    )
    {
        return document != null
            && document.data() != null
            && document.data().attributes() != null
            && document.data().attributes().hasLifetimeSupport();
    }

    /**
     * Logs a warning if the included campaign currency doesn't match the
     * configured campaign currency. The configured currency is used
     * regardless. The included campaign depends on webhook scopes, so the
     * check is skipped if it's missing.
     *
     * @param document member document
     */
    private void checkCampaignCurrency(PatreonDocument<PatreonMember> document)
    {
        PatreonMember.Relationships relationships = document.data()
            .relationships();
        PatreonDocument.Relationship campaign = relationships == null
            ? null
            : relationships.campaign();
        document
            .findIncluded
            (
                PatreonMember.CAMPAIGN_TYPE,
                campaign == null ? null : campaign.id()
            )
            .map(PatreonDocument.Resource::attributes)
            .map(PatreonDocument.ResourceAttributes::currency)
            .filter(currency->!currency.equalsIgnoreCase(campaignCurrency))
            .ifPresent(currency->LOG.warn
            (
                "Patreon campaign currency mismatch, using the configured "
                    + "currency: memberId={}, configured={}, payload={}",
                document.data().id(),
                campaignCurrency,
                currency
            ));
    }

    private Payload toPayload(PatreonDocument<PatreonMember> document)
    {
        PatreonMember member = document.data();
        PatreonMember.Attributes attributes = member.attributes();
        PatreonMember.Relationships relationships = member.relationships();
        PatreonDocument.Relationship user = relationships == null
            ? null
            : relationships.user();
        return new Payload
        (
            member.id(),
            user == null ? null : user.id(),
            attributes.fullName(),
            campaignCurrency,
            attributes.effectiveLifetimeSupportCents(),
            attributes.pledgeRelationshipStart(),
            attributes.lastChargeDate() != null
                ? SC2Pulse.offsetDateTime(attributes.lastChargeDate())
                : SC2Pulse.offsetDateTime()
        );
    }

    private static BigDecimal majorUnits(long minorUnits, String currency)
    {
        int scale = Monetary.getCurrency(currency).getDefaultFractionDigits();
        return BigDecimal.valueOf(minorUnits, scale);
    }

}
