// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.SpyBeanConfig;
import com.nephest.battlenet.sc2.extension.AutoConfigureDatabase;
import com.nephest.battlenet.sc2.model.kofi.KoFiDonation;
import com.nephest.battlenet.sc2.model.local.Donation;
import com.nephest.battlenet.sc2.model.local.DonationPlatform;
import com.nephest.battlenet.sc2.model.local.GenericMoney;
import com.nephest.battlenet.sc2.model.local.PatreonDonationState;
import com.nephest.battlenet.sc2.model.local.PlatformDonationRef;
import com.nephest.battlenet.sc2.model.local.dao.DonationRepository;
import com.nephest.battlenet.sc2.model.local.dao.PatreonDonationStateRepository;
import com.nephest.battlenet.sc2.model.patreon.PatreonDocument;
import com.nephest.battlenet.sc2.model.patreon.PatreonMember;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import com.nephest.battlenet.sc2.util.TestUtil;
import com.nephest.battlenet.sc2.web.service.MonetaryAmountConverter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.money.Monetary;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.convert.ConversionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(classes = {AllTestConfig.class, SpyBeanConfig.class})
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@ExtendWith(OutputCaptureExtension.class)
@AutoConfigureDatabase(AutoConfigureDatabase.ExecutionPhase.CLASS)
public class DonationWebhookIT
{

    //upstream payloads from the built-in webhook tests of the platforms
    private static final String KO_FI_DONATION_PAYLOAD
        = "donation/ko-fi-donation.json";
    private static final String PATREON_MEMBER_PAYLOAD
        = "donation/patreon-member.json";

    private static final String KO_FI_TOKEN = "kofi-secret";
    private static final String PATREON_SECRET = "patreon-secret";
    private static final OffsetDateTime CREATED
        = OffsetDateTime.parse("2026-09-16T10:00:00Z");
    private static final RecursiveComparisonConfiguration DONATION_COMPARISON
        = RecursiveComparisonConfiguration.builder()
            .withEqualsForType
            (
                (l, r)->l.compareTo(r) == 0,
                BigDecimal.class
            )
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .build();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DonationRepository donationRepository;

    @Autowired
    private PatreonDonationStateRepository stateRepository;

    @Autowired
    private MonetaryAmountConverter monetaryAmountConverter;
    @Autowired @Qualifier("mvcConversionService")
    private ConversionService conversionService;

    @Value
    (
        "${com.nephest.battlenet.sc2.donation.webhook.patreon"
            + ".pledge-start-cutoff}"
    )
    private OffsetDateTime pledgeStartCutoff;

    @Value
    (
        "${com.nephest.battlenet.sc2.donation.webhook.patreon"
            + ".campaign-currency}"
    )
    private String campaignCurrency;

    @BeforeEach
    public void reset()
    {
        donationRepository.deleteAll();
        stateRepository.deleteAll();
    }

    @ValueSource(strings = {"Donation", "Subscription"})
    @ParameterizedTest
    public void shouldAcceptKoFiAndIgnoreDuplicate
    (
        String type,
        CapturedOutput output
    )
    throws Exception
    {
        KoFiDonation koFiDonation = koFiDonation
        (
            KO_FI_TOKEN,
            "transaction-1",
            type,
            new BigDecimal("3.125"),
            "USD"
        );
        postKoFi(koFiDonation).andExpect(status().isOk());
        postKoFi(koFiDonation).andExpect(status().isOk());

        assertThat(donationRepository.findAll())
            .usingRecursiveComparison(DONATION_COMPARISON)
            .ignoringFields("id")
            .isEqualTo(List.of(expectedKoFiDonation
            (
                "transaction-1",
                new GenericMoney(new BigDecimal("3.125"), "USD"),
                new GenericMoney(new BigDecimal("3.125"), "USD")
            )));
        assertTrue(output.getAll().contains("Skipping duplicate donation"));
    }

    @ValueSource(strings = {"Shop Order", "Commission"})
    @ParameterizedTest
    public void shouldIgnoreKoFiPurchases(String type)
    throws Exception
    {
        postKoFi(koFiDonation(
            KO_FI_TOKEN,
            "transaction-1",
            type,
            new BigDecimal("3.125"),
            "USD"
        ))
            .andExpect(status().isOk());
        assertEquals(0, donationRepository.count());
    }

    @Test
    public void shouldUseKoFiMessageIdAsFallback()
    throws Exception
    {
        postKoFi(koFiDonation(
            KO_FI_TOKEN,
            null,
            "Donation",
            new BigDecimal("3.125"),
            "USD"
        ))
            .andExpect(status().isOk());

        assertEquals
        (
            "message-1",
            donationRepository.findAll().get(0).getPlatformRef().donationId()
        );
    }

    @Test
    public void shouldRejectInvalidKoFiDonation()
    throws Exception
    {
        postKoFi(koFiDonation(
            KO_FI_TOKEN,
            "transaction-1",
            "Donation",
            BigDecimal.ZERO,
            "USD"
        ))
            .andExpect(status().isBadRequest());
        assertEquals(0, donationRepository.count());
    }

    @Test
    public void shouldRejectInvalidKoFiToken()
    throws Exception
    {
        postKoFi(koFiDonation(
            "wrong",
            "transaction-1",
            "Donation",
            new BigDecimal("3.125"),
            "USD"
        ))
            .andExpect(status().isUnauthorized());
        assertEquals(0, donationRepository.count());
    }

    @Test
    public void shouldMapUpstreamKoFiPayload()
    throws Exception
    {
        ObjectNode payload = readUpstreamPayload(KO_FI_DONATION_PAYLOAD);
        payload.put("verification_token", KO_FI_TOKEN);

        postKoFi(objectMapper.writeValueAsString(payload))
            .andExpect(status().isOk());

        GenericMoney money = new GenericMoney(new BigDecimal("3.00"), "USD");
        assertThat(donationRepository.findAll())
            .usingRecursiveComparison(DONATION_COMPARISON)
            .ignoringFields("id")
            .isEqualTo(List.of(new Donation
            (
                new PlatformDonationRef
                (
                    DonationPlatform.KO_FI,
                    "00000000-1111-2222-3333-444444444444",
                    "jo.example@example.com",
                    "Jo Example"
                ),
                money,
                money,
                SC2Pulse.offsetDateTime(OffsetDateTime.parse(
                    "2026-10-24T10:36:11Z"
                ))
            )));
    }

    @Test
    public void whenUpstreamPatreonPayloadIsNotCharged_thenIgnore()
    throws Exception
    {
        postPatreon(objectMapper.writeValueAsString(
            readUpstreamPayload(PATREON_MEMBER_PAYLOAD)))
            .andExpect(status().isOk());

        assertEquals(0, donationRepository.count());
        assertEquals(0, stateRepository.count());
    }

    /**
     * Charge statuses are not checked, only lifetime support is used.
     */
    @NullSource
    @ValueSource(strings = {"Paid", "Declined"})
    @ParameterizedTest
    public void shouldMapChargedUpstreamPatreonPayload
    (
        String chargeStatus,
        CapturedOutput output
    )
    throws Exception
    {
        OffsetDateTime chargeDate = pledgeStartCutoff.plusDays(2);
        ObjectNode payload = chargedUpstreamPatreonPayload
        (
            chargeDate,
            chargeStatus
        );
        ((ObjectNode) payload.path("included").path(0).path("attributes"))
            .put("currency", campaignCurrency);

        postPatreon(objectMapper.writeValueAsString(payload))
            .andExpect(status().isOk());

        assertThat(donationRepository.findAll())
            .usingRecursiveComparison(DONATION_COMPARISON)
            .ignoringFields("id")
            .isEqualTo(List.of(expectedUpstreamPatreonDonation(chargeDate)));
        assertFalse(output.getAll().contains("currency mismatch"));
    }

    @Test
    public void whenIncludedCampaignCurrencyMismatch_thenWarnAndUseConfigured
    (
        CapturedOutput output
    )
    throws Exception
    {
        String includedCurrency = campaignCurrency.equals("JPY")
            ? "EUR"
            : "JPY";
        OffsetDateTime chargeDate = pledgeStartCutoff.plusDays(2);
        ObjectNode payload = chargedUpstreamPatreonPayload(chargeDate, "Paid");
        ((ObjectNode) payload.path("included").path(0).path("attributes"))
            .put("currency", includedCurrency);

        postPatreon(objectMapper.writeValueAsString(payload))
            .andExpect(status().isOk());

        assertThat(donationRepository.findAll())
            .usingRecursiveComparison(DONATION_COMPARISON)
            .ignoringFields("id")
            .isEqualTo(List.of(expectedUpstreamPatreonDonation(chargeDate)));
        assertTrue(output.getAll().contains("currency mismatch"));
    }

    @Test
    public void whenConcurrentDuplicateDonation_thenSkipUniqueViolation
    (
        CapturedOutput output
    )
    throws Exception
    {
        //simulate a concurrent insert that passed the existence check
        doReturn(false)
            .when(donationRepository)
            .existsByPlatformRefPlatformAndPlatformRefDonationId(any(), any());
        KoFiDonation koFiDonation = koFiDonation
        (
            KO_FI_TOKEN,
            "transaction-1",
            "Donation",
            new BigDecimal("3"),
            "USD"
        );
        postKoFi(koFiDonation).andExpect(status().isOk());
        postKoFi(koFiDonation).andExpect(status().isOk());

        assertThat(donationRepository.findAll())
            .usingRecursiveComparison(DONATION_COMPARISON)
            .ignoringFields("id")
            .isEqualTo(List.of(expectedKoFiDonation
            (
                "transaction-1",
                new GenericMoney(new BigDecimal("3"), "USD"),
                new GenericMoney(new BigDecimal("3"), "USD")
            )));
        assertTrue(output.getAll().contains("Skipping duplicate donation"));
    }

    /*
        ECB EUR/USD reference rates: 2023-02-17(Fri) 1.0625,
        2023-02-20(Mon) 1.0674. There are no weekend rates, the most recent
        previous rate is used instead.
     */
    @CsvSource
    ({
        "2023-02-17T12:00:00Z, 3.1875",
        "2023-02-18T12:00:00Z, 3.1875",
        "2023-02-20T12:00:00Z, 3.2022",
        //Monday in ECB(Europe/Berlin) time zone
        "2023-02-19T23:30:00Z, 3.2022"
    })
    @ParameterizedTest
    public void shouldConvertDonationToTargetCurrencyUsingDonationDateRate
    (
        OffsetDateTime created,
        BigDecimal expectedAmount
    )
    throws Exception
    {
        postKoFi(new KoFiDonation(
            KO_FI_TOKEN,
            "message-1",
            "transaction-1",
            created,
            "Donation",
            "Supporter",
            "supporter@example.com",
            new BigDecimal("3"),
            "EUR"
        ))
            .andExpect(status().isOk());

        assertThat(donationRepository.findAll())
            .usingRecursiveComparison(DONATION_COMPARISON)
            .ignoringFields("id")
            .isEqualTo(List.of(new Donation
            (
                new PlatformDonationRef
                (
                    DonationPlatform.KO_FI,
                    "transaction-1",
                    "supporter@example.com",
                    "Supporter"
                ),
                new GenericMoney(new BigDecimal("3"), "EUR"),
                new GenericMoney(expectedAmount, "USD"),
                created
            )));
    }

    @Test
    public void whenCurrencyConversionFails_thenSaveDonationWithoutTargetAmount
    (
        CapturedOutput output
    )
    throws Exception
    {
        doThrow(new IllegalStateException("test"))
            .when(monetaryAmountConverter)
            .convert(any(), any(), any());

        postKoFi(koFiDonation
        (
            KO_FI_TOKEN,
            "transaction-1",
            "Donation",
            new BigDecimal("3"),
            "EUR"
        ))
            .andExpect(status().isOk());

        assertThat(donationRepository.findAll())
            .usingRecursiveComparison(DONATION_COMPARISON)
            .ignoringFields("id")
            .isEqualTo(List.of(expectedKoFiDonation
            (
                "transaction-1",
                new GenericMoney(new BigDecimal("3"), "EUR"),
                new GenericMoney(null, "USD")
            )));
        assertTrue(output.getAll().contains("Unable to convert money"));
    }

    @Test
    public void shouldUseLifetimePatreonAmountThenPositiveDeltas
    (
        CapturedOutput output
    )
    throws Exception
    {
        postPatreon(patreonPayload(10_000)).andExpect(status().isOk());
        postPatreon(patreonPayload(10_700)).andExpect(status().isOk());
        postPatreon(patreonPayload(10_700)).andExpect(status().isOk());
        postPatreon(patreonPayload(10_600)).andExpect(status().isOk());

        assertThat(donationRepository.findAll(Sort.by("id")))
            .usingRecursiveComparison(DONATION_COMPARISON)
            .ignoringFields("id")
            .isEqualTo(List.of
            (
                patreonDonation(10_000, 10_000),
                patreonDonation(10_700, 700)
            ));
        assertEquals
        (
            campaignAmount(10_700),
            stateRepository.findById("member-1")
                .orElseThrow()
                .getLifetimeAmount()
        );
        assertTrue(output.getAll().contains("Skipping duplicate Patreon data"));
    }

    @Test
    public void whenConcurrentPatreonUpdate_thenRetryInNewTransaction()
    throws Exception
    {
        //repository spy is an interface proxy, delegate to the real target
        doThrow(new DataIntegrityViolationException("test"))
            .doAnswer(mockingDetails(stateRepository)
                .getMockCreationSettings()
                .getDefaultAnswer())
            .when(stateRepository).save(any());

        postPatreon(patreonPayload(10_000)).andExpect(status().isOk());

        verify(stateRepository, times(2)).save(any());
        assertThat(donationRepository.findAll())
            .usingRecursiveComparison(DONATION_COMPARISON)
            .ignoringFields("id")
            .isEqualTo(List.of(
                patreonDonation(10_000, 10_000)
            ));
    }

    @NullSource
    @ValueSource(longs = {-1, 0})
    @ParameterizedTest
    public void whenPledgeStartedBeforeCutoff_thenSeedStateAndRecordDeltas
    (
        Long cutoffOffsetSeconds,
        CapturedOutput output
    )
    throws Exception
    {
        OffsetDateTime pledgeStart = cutoffOffsetSeconds == null
            ? null
            : pledgeStartCutoff.plusSeconds(cutoffOffsetSeconds);
        postPatreon(patreonPayload(10_000, pledgeStart))
            .andExpect(status().isOk());
        assertEquals(0, donationRepository.count());
        assertTrue(output.getAll().contains("started before the cutoff"));

        postPatreon(patreonPayload(10_700, pledgeStart))
            .andExpect(status().isOk());
        assertThat(donationRepository.findAll())
            .usingRecursiveComparison(DONATION_COMPARISON)
            .ignoringFields("id")
            .isEqualTo(List.of(
                patreonDonation(10_700, 700)
            ));
    }

    @Test
    public void whenCampaignCurrencyChanged_thenRebaseStateAndRecordDeltas
    (
        CapturedOutput output
    )
    throws Exception
    {
        //state from the previous campaign currency
        String previousCurrency = campaignCurrency.equals("JPY")
            ? "EUR"
            : "JPY";
        stateRepository.save(new PatreonDonationState
        (
            "member-1",
            previousCurrency,
            new BigDecimal("20000")
        ));

        postPatreon(patreonPayload(10_000)).andExpect(status().isOk());
        assertEquals(0, donationRepository.count());
        assertTrue(output.getAll().contains("campaign currency changed"));

        postPatreon(patreonPayload(10_700)).andExpect(status().isOk());
        assertThat(donationRepository.findAll())
            .usingRecursiveComparison(DONATION_COMPARISON)
            .ignoringFields("id")
            .isEqualTo(List.of(
                patreonDonation(10_700, 700)
            ));
        PatreonDonationState state = stateRepository.findById("member-1")
            .orElseThrow();
        assertEquals(campaignCurrency, state.getCurrency());
        assertEquals(campaignAmount(10_700), state.getLifetimeAmount());
    }

    @Test
    public void shouldRejectInvalidPatreonSignature()
    throws Exception
    {
        mvc.perform
        (
            post("/webhooks/patreon")
                .header("X-Patreon-Event", "members:update")
                .header("X-Patreon-Signature", "00")
                .contentType(MediaType.APPLICATION_JSON)
                .content(patreonPayload(10_000))
        )
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void whenPatreonLifetimeIsZero_thenIgnore()
    throws Exception
    {
        postPatreon(patreonPayload(0)).andExpect(status().isOk());
        assertEquals(0, donationRepository.count());
        assertEquals(0, stateRepository.count());
    }

    @Test
    public void shouldPageDonationsAndSerializeEnumNames()
    throws Exception
    {
        List<Donation> donations = IntStream.rangeClosed(1, 3)
            .mapToObj(i->donationRepository.save(new Donation
            (
                new PlatformDonationRef
                (
                    DonationPlatform.KO_FI,
                    "message-" + i,
                    "donor-" + i,
                    "Donor " + i
                ),
                new GenericMoney(BigDecimal.valueOf(i), "EUR"),
                new GenericMoney(BigDecimal.valueOf(i), "USD"),
                CREATED.plusHours(i)
            )))
            .map(DonationWebhookIT::anonymous)
            .toList();

        CursorNavigableResult<List<Donation>> first = response
        (
            get("/api/donations").queryParam("limit", "2")
        );
        assertThat(first.result())
            .usingRecursiveComparison(DONATION_COMPARISON)
            .isEqualTo(List.of(donations.get(2), donations.get(1)));

        CursorNavigableResult<List<Donation>> second = response
        (
            get("/api/donations")
                .queryParam("limit", "2")
                .queryParam
                (
                    "after",
                    conversionService.convert
                    (
                        first.navigation().after(),
                        String.class
                    )
                )
        );
        assertThat(second.result())
            .usingRecursiveComparison(DONATION_COMPARISON)
            .isEqualTo(List.of(donations.get(0)));

        CursorNavigableResult<List<Donation>> backward = response
        (
            get("/api/donations")
                .queryParam("limit", "2")
                .queryParam
                (
                    "before",
                    conversionService.convert
                    (
                        second.navigation().before(),
                        String.class
                    )
                )
        );
        assertThat(backward.result())
            .usingRecursiveComparison(DONATION_COMPARISON)
            .isEqualTo(first.result());

        CursorNavigableResult<List<Donation>> ascending = response
        (
            get("/api/donations")
                .queryParam("limit", "3")
                .queryParam("sort", "+created")
        );
        assertThat(ascending.result())
            .usingRecursiveComparison(DONATION_COMPARISON)
            .isEqualTo(donations);
    }

    /**
     * @param donation full donation
     * @return donation that contains only {@code AnonymousDonation} fields
     */
    private static Donation anonymous(Donation donation)
    {
        Donation anonymous = new Donation
        (
            new PlatformDonationRef
            (
                donation.getPlatformRef().platform(),
                null,
                null,
                null
            ),
            null,
            donation.getMoneyTarget(),
            donation.getCreated()
        );
        anonymous.setId(donation.getId());
        return anonymous;
    }

    private static KoFiDonation koFiDonation
    (
        String verificationToken,
        String kofiTransactionId,
        String type,
        BigDecimal amount,
        String currency
    )
    {
        return new KoFiDonation
        (
            verificationToken,
            "message-1",
            kofiTransactionId,
            CREATED,
            type,
            "Supporter",
            "supporter@example.com",
            amount,
            currency
        );
    }

    private static Donation expectedKoFiDonation
    (
        String donationId,
        GenericMoney moneyOriginal,
        GenericMoney moneyTarget
    )
    {
        return new Donation
        (
            new PlatformDonationRef
            (
                DonationPlatform.KO_FI,
                donationId,
                "supporter@example.com",
                "Supporter"
            ),
            moneyOriginal,
            moneyTarget,
            CREATED
        );
    }

    /**
     * The target currency is the same as the campaign currency in test
     * properties, so the target money is the same as the original money.
     *
     * @param lifetimeMinorUnits lifetime amount of the payload
     * @param amountMinorUnits donation amount
     * @return expected Patreon donation
     */
    private Donation patreonDonation
    (
        long lifetimeMinorUnits,
        long amountMinorUnits
    )
    {
        GenericMoney money = new GenericMoney
        (
            campaignAmount(amountMinorUnits),
            campaignCurrency
        );
        return new Donation
        (
            new PlatformDonationRef
            (
                DonationPlatform.PATREON,
                "member-1:" + campaignCurrency + ":" + lifetimeMinorUnits,
                "user-1",
                "Patron"
            ),
            money,
            money,
            CREATED
        );
    }

    private BigDecimal campaignAmount(long minorUnits)
    {
        return BigDecimal.valueOf
        (
            minorUnits,
            Monetary.getCurrency(campaignCurrency).getDefaultFractionDigits()
        );
    }

    private ResultActions postKoFi(KoFiDonation donation)
    throws Exception
    {
        return postKoFi(objectMapper.writeValueAsString(donation));
    }

    private ResultActions postKoFi(String data)
    throws Exception
    {
        return mvc.perform
        (
            post("/webhooks/ko-fi")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("data", data)
        );
    }

    private ObjectNode readUpstreamPayload(String path)
    throws Exception
    {
        return (ObjectNode) objectMapper.readTree
        (
            TestUtil.readResource(getClass(), path)
        );
    }

    private ResultActions postPatreon(String payload)
    throws Exception
    {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        return mvc.perform
        (
            post("/webhooks/patreon")
                .header("X-Patreon-Event", "members:update")
                .header("X-Patreon-Signature", signature(body))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        );
    }

    private CursorNavigableResult<List<Donation>> response
    (
        MockHttpServletRequestBuilder request
    )
    throws Exception
    {
        String response = mvc.perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readValue(response, new TypeReference<>() {});
    }

    private String patreonPayload(long lifetime)
    throws Exception
    {
        return patreonPayload(lifetime, pledgeStartCutoff.plusDays(1));
    }

    /**
     * Includes depend on webhook scopes, the payload has no campaign
     * relationship and no includes to verify that they are optional.
     */
    private String patreonPayload(long lifetime, OffsetDateTime pledgeStart)
    throws Exception
    {
        return objectMapper.writeValueAsString(new PatreonDocument<>
        (
            new PatreonMember
            (
                "member-1",
                new PatreonMember.Attributes
                (
                    lifetime,
                    null,
                    "Patron",
                    CREATED,
                    pledgeStart
                ),
                new PatreonMember.Relationships
                (
                    null,
                    relationship("user-1", "user")
                )
            ),
            null
        ));
    }

    /**
     * Upstream test payload of a member that hasn't been charged yet. The
     * charge and pledge start fields are replaced to make it a charged
     * member that pledged after the cutoff.
     *
     * @param chargeDate last charge date
     * @param chargeStatus last charge status
     * @return charged member payload
     */
    private ObjectNode chargedUpstreamPatreonPayload
    (
        OffsetDateTime chargeDate,
        String chargeStatus
    )
    throws Exception
    {
        ObjectNode payload = readUpstreamPayload(PATREON_MEMBER_PAYLOAD);
        ((ObjectNode) payload.path("data").path("attributes"))
            .put("campaign_lifetime_support_cents", 1337)
            .put("last_charge_date", chargeDate.toString())
            .put("last_charge_status", chargeStatus)
            .put
            (
                "pledge_relationship_start",
                pledgeStartCutoff.plusDays(1).toString()
            );
        return payload;
    }

    private Donation expectedUpstreamPatreonDonation(OffsetDateTime created)
    {
        GenericMoney money = new GenericMoney
        (
            campaignAmount(1337),
            campaignCurrency
        );
        return new Donation
        (
            new PlatformDonationRef
            (
                DonationPlatform.PATREON,
                "00000000-1111-2222-3333-444444444444:"
                    + campaignCurrency
                    + ":1337",
                "987654321",
                "Jules Winnfield"
            ),
            money,
            money,
            created
        );
    }

    private static PatreonDocument.Relationship relationship
    (
        String id,
        String type
    )
    {
        return new PatreonDocument.Relationship
        (
            new PatreonDocument.ResourceRef(id, type)
        );
    }

    private static String signature(byte[] body) throws GeneralSecurityException
    {
        Mac mac = Mac.getInstance("HmacMD5");
        mac.init
        (
            new SecretKeySpec
            (
                PATREON_SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacMD5"
            )
        );
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

}
