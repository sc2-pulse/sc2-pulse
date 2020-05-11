package com.nephest.battlenet.sc2.service;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.dao.*;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class AccountFollowingIT
{

    public static final QueueType QUEUE_TYPE = QueueType.LOTV_4V4;
    public static final TeamType TEAM_TYPE = TeamType.ARRANGED;
    public static final BaseLeagueTier.LeagueTierType TIER_TYPE = BaseLeagueTier.LeagueTierType.FIRST;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamMemberDAO teamMemberDAO;

    @Autowired
    private DivisionDAO divisionDAO;

    @Autowired
    private AccountFollowingService accountFollowingService;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    private static Account account;

    private static MockMvc mvc;

    public static final String BATTLETAG = "refaccount#123";

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired AccountDAO accountDAO,
        @Autowired WebApplicationContext webApplicationContext
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            account = accountDAO.merge(new Account(null, BATTLETAG));
            mvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .alwaysDo(print())
                .build();
        }
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    @WithBlizzardMockUser(username = BATTLETAG)
    public void testFollowing()
    throws Exception
    {
        Region region = Region.EU;
        Season season1 = new Season(null, 1L, region, 2020, 1);
        Season season2 = new Season(null, 2L, region, 2020, 2);
        //generate some noise
        seasonGenerator.generateSeason
        (
            List.of(season1, season2),
            List.of(BaseLeague.LeagueType.values()),
            QUEUE_TYPE,
            TEAM_TYPE,
            TIER_TYPE,
            5
        );

        //create chars that we follow
        Division bronze1 = divisionDAO.findListByLadder(season2.getBattlenetId(), region, BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE).get(0);
        PlayerCharacter character1 = playerCharacterDAO
            .create(new PlayerCharacter(null, account.getId(), region, 9998L, 1, "refchar1#123"));
        PlayerCharacter character2 = playerCharacterDAO
            .create(new PlayerCharacter(null, account.getId(), region, 9999L, 1, "refchar2#123"));
        Team team1 = new Team
        (
            null, season2.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE), TIER_TYPE,
            bronze1.getId(), BigInteger.valueOf(11111L), 100L,
            100, 0, 0, 0
        );
        teamDAO.create(team1);
        TeamMember member1 = new TeamMember
        (
            team1.getId(), character1.getId(),
            100, 0, 0, 0
        );
        teamMemberDAO.create(member1);

        Team team2 = new Team
        (
            null, season2.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE), TIER_TYPE,
            bronze1.getId(), BigInteger.valueOf(11112L), 101L,
            100, 0, 0, 0
        );
        teamDAO.create(team2);
        TeamMember member2 = new TeamMember
        (
            team2.getId(), character1.getId(),
            100, 0, 0, 0
        );
        teamMemberDAO.create(member2);

        Team team3 = new Team
        (
            null, season2.getBattlenetId(), region,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE), TIER_TYPE,
            bronze1.getId(), BigInteger.valueOf(11113L), 99L,
            100, 0, 0, 0
        );
        teamDAO.create(team3);
        TeamMember member3 = new TeamMember
        (
            team3.getId(), character2.getId(),
            100, 0, 0, 0
        );
        teamMemberDAO.create(member3);

        String TOKEN_ATTR_NAME = "org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN";
        HttpSessionCsrfTokenRepository httpSessionCsrfTokenRepository = new HttpSessionCsrfTokenRepository();
        CsrfToken csrfToken = httpSessionCsrfTokenRepository.generateToken(new MockHttpServletRequest());

        //follow a random account from the first season
        mvc.perform
        (
            post("/api/my/following/10").contentType(MediaType.APPLICATION_JSON)
                .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                .param(csrfToken.getParameterName(), csrfToken.getToken())
        )
            .andExpect(status().isOk())
            .andReturn();

        //follow ref account
        mvc.perform
        (
            post("/api/my/following/" + account.getId()).contentType(MediaType.APPLICATION_JSON)
                .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                .param(csrfToken.getParameterName(), csrfToken.getToken())
        )
            .andExpect(status().isOk())
            .andReturn();
        assertEquals(2, accountFollowingService.getFollowingCount(account.getId()));

        String expectedFollowings =
            "[{\"accountId\":1,\"followingAccountId\":1},{\"accountId\":1,\"followingAccountId\":10}]";
        mvc.perform(get("/api/my/following").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().json(expectedFollowings))
            .andReturn();

        //returning data according to filters
        String expectedResult =
            "[{\"rating\":101,\"wins\":100,\"losses\":0,\"id\":72,"
            + "\"region\":\"EU\",\"league\":{\"type\":0,\"queueType\":204,\"teamType\":0},\"leagueTierType\":0,"
            + "\"members\":[{\"terranGamesPlayed\":100,\"protossGamesPlayed\":0,\"zergGamesPlayed\":0,"
            + "\"randomGamesPlayed\":0,\"character\":{\"realm\":1,\"name\":\"refchar1#123\",\"id\":281,"
            + "\"accountId\":1,\"region\":\"EU\",\"battlenetId\":9998},"
            + "\"account\":{\"battleTag\":\"refaccount#123\"}}]},"

            + "{\"rating\":100,\"wins\":100,\"losses\":0,\"id\":71,"
            + "\"region\":\"EU\",\"league\":{\"type\":0,\"queueType\":204,\"teamType\":0},\"leagueTierType\":0,"
            + "\"members\":[{\"terranGamesPlayed\":100,\"protossGamesPlayed\":0,\"zergGamesPlayed\":0,"
            + "\"randomGamesPlayed\":0,\"character\":{\"realm\":1,\"name\":\"refchar1#123\",\"id\":281,"
            + "\"accountId\":1,\"region\":\"EU\",\"battlenetId\":9998},"
            + "\"account\":{\"battleTag\":\"refaccount#123\"}}]},"

            + "{\"rating\":99,\"wins\":100,\"losses\":0,\"id\":73,"
            + "\"region\":\"EU\",\"league\":{\"type\":0,\"queueType\":204,\"teamType\":0},\"leagueTierType\":0,"
            + "\"members\":[{\"terranGamesPlayed\":100,\"protossGamesPlayed\":0,\"zergGamesPlayed\":0,"
            + "\"randomGamesPlayed\":0,\"character\":{\"realm\":1,\"name\":\"refchar2#123\",\"id\":282,"
            + "\"accountId\":1,\"region\":\"EU\",\"battlenetId\":9999},"
            + "\"account\":{\"battleTag\":\"refaccount#123\"}}]}]\n";
        String url = "/api/my/following/ladder?season=2&queue=LOTV_4V4&team-type=ARRANGED&eu=true&bro=true";
        mvc.perform(get(url).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().json(expectedResult))
            .andReturn();

        //forbid following accounts if max following is reached
        accountFollowingService.setFollowingMax(2);
        mvc.perform
        (
            post("/api/my/following/" + 10).contentType(MediaType.APPLICATION_JSON)
                .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                .param(csrfToken.getParameterName(), csrfToken.getToken())
        )
            .andExpect(status().is4xxClientError())
            .andReturn();

        //unfollowing
        mvc.perform
        (
            delete("/api/my/following/" + account.getId()).contentType(MediaType.APPLICATION_JSON)
                .sessionAttr(TOKEN_ATTR_NAME, csrfToken)
                .param(csrfToken.getParameterName(), csrfToken.getToken())
        )
            .andExpect(status().isOk())
            .andReturn();
        //was 2, now 1
        assertEquals(1, accountFollowingService.getFollowingCount(account.getId()));

    }

}
