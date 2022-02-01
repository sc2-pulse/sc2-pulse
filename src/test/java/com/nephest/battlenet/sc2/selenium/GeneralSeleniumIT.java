// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.selenium;

/*
    This test does all UI interactions and searches for any js errors
 */

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.*;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;

@SpringBootTest
(
    classes = AllTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT
)
@ActiveProfiles({"dev", "default"})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class GeneralSeleniumIT
{

    public static final int TIMEOUT_MILLIS = 10000;

    @Autowired
    private ServletWebServerApplicationContext webServerAppCtxt;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private ClanDAO clanDAO;

    @Autowired
    private LeagueStatsDAO leagueStatsDAO;

    @Autowired
    private QueueStatsDAO queueStatsDAO;

    @Autowired
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private SeasonStateDAO seasonStateDAO;

    @Autowired
    private JdbcTemplate template;

    private static WebDriver driver;
    private static JavascriptExecutor js;

    private static boolean failed = false;

    @BeforeAll
    public static void init
    (
        @Autowired DataSource dataSource,
        @Autowired AccountDAO accountDAO,
        @Value("${selenium.driver}") String seleniumDriver
    )
    throws Exception
    {
        WebDriverManager.getInstance(seleniumDriver).setup();
        driver = (WebDriver) Class
                .forName("org.openqa.selenium." + getDriverPackage(seleniumDriver) + "." + seleniumDriver + "Driver")
                .getDeclaredConstructor()
                .newInstance();
        js = (JavascriptExecutor) driver;
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
    }

    public static String getDriverPackage(String driver)
    {
        return driver.equals("InternetExplorer") ? "ie" : driver.toLowerCase();
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        if(!failed) driver.close();
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }


    @Test
    public void testUI()
    {
        setupData();
        testUI(driver);
    }

    private void testUI(WebDriver driver)
    {
        int port = webServerAppCtxt.getWebServer().getPort();
        WebDriverWait wait = new WebDriverWait(driver, TIMEOUT_MILLIS / 1000);
        String root = "http://localhost:" + port;
        getAndWait(driver, wait, root + "/", "#form-ladder-season-picker option");
        testLadderUI(driver, wait);
        testOnline(driver, wait);
        testSearch(driver, wait);
        testSettings(driver, wait);
        testMmrHistory(driver, wait);
        checkJsErrors();
        getAndWaitAndCheckJsErrors(driver, wait, root + "/about", "#about");
        getAndWaitAndCheckJsErrors(driver, wait, root + "/privacy-policy", "#privacy");
        getAndWaitAndCheckJsErrors(driver, wait, root + "/status", "#status");
    }

    private static void testLadderUI(WebDriver driver, WebDriverWait wait)
    {
        //pagination
        clickAndWait(driver, wait, "#form-ladder button[type=\"submit\"]", "tr[data-team-id]");
        clickAndWait(driver, wait,
            "#ladder-top li.page-item:not(.disabled) a[data-page-count=\"1\"]",
            "#ladder-top li.page-item.disabled a[data-page-count=\"0\"][data-page-number=\"2\"]");

        //character
        clickAndWait(driver, wait, "#ladder a.player-link", "#player-info.modal.show");
        switchTabsAndToggleInputs(driver, wait, "#player-stats-tabs");
        clickAndWait(driver, wait, "#player-info button.close", ".no-popup-hide:not(.d-none)");

        //population
        switchTabsAndToggleInputs(driver, wait, "#stats-tabs");
    }

    private static void testOnline(WebDriver driver, WebDriverWait wait)
    {
        clickAndWait(driver, wait, "#online-tab", "#online.show.active");
        driver.findElement(By.cssSelector("#online-to")).sendKeys(SeasonGenerator.DEFAULT_SEASON_START.plusDays(1).toString());
        clickAndWait(driver, wait, "#form-online button[type=\"submit\"]", "#online-data:not(.d-none)");
        clickCanvases(driver, "#online-data");
    }

    private static void testSearch(WebDriver driver, WebDriverWait wait)
    {
        clickAndWait(driver, wait, "#search-all-tab", "#search-all.show.active");

        //player
        clickAndWait(driver, wait, "#search-tab", "#search.show.active");
        driver.findElement(By.cssSelector("#search-player-name")).sendKeys("character");
        clickAndWait(driver, wait, "#form-search button[type=\"submit\"]", "#search-result-all:not(.d-none)");

        //clan
        testClanCursorSearch(driver, wait);
    }

    private static void testClanCursorSearch(WebDriver driver, WebDriverWait wait)
    {
        clickAndWait(driver, wait, "#search-clan-tab", "#search-clan.show.active");
        Select cursor = new Select(driver.findElement(By.cssSelector("#clan-search-sort-by")));
        WebElement tagOrNameInput = driver.findElement(By.cssSelector("#clan-search-tag-name"));
        for(int i = 0; i < cursor.getOptions().size(); i++)
        {
            cursor.selectByIndex(i);
            js.executeScript("document.querySelector(\"#search-result-clan-all\").classList.add(\"d-none\");");
            clickAndWait(driver, wait, "#form-search-clan button[type=\"submit\"]", "#search-result-clan-all:not(.d-none)");
            tagOrNameInput.sendKeys("clan");
            js.executeScript("document.querySelector(\"#search-result-clan-all\").classList.add(\"d-none\");");
            clickAndWait(driver, wait, "#form-search-clan button[type=\"submit\"]", "#search-result-clan-all:not(.d-none)");
            tagOrNameInput.sendKeys(Keys.HOME, Keys.chord(Keys.SHIFT, Keys.END), Keys.BACK_SPACE);
        }
        clickAndWait(driver, wait, "#search-result-clan .clan-auto-search", "#search-result-all:not(.d-none)");
    }

    private static void testSettings(WebDriver driver, WebDriverWait wait)
    {
        clickAndWait(driver, wait, "#settings-tab", "#settings.show.active");
        toggleInputs(driver, "#settings");
    }

    private static void testMmrHistory(WebDriver driver, WebDriverWait wait)
    {
        clickAndWait(driver, wait, "#stats-tab", "#stats.show.active");
        clickAndWait(driver, wait, "#ladder-tab", "#ladder-top.show.active");
        driver.findElements(By.cssSelector("#ladder .team-buffer-toggle")).stream()
            .limit(3)
            .forEach(WebElement::click);
        WebElement teamBufferCollapse = driver.findElement(By.cssSelector("#team-buffer-collapse"));
        teamBufferCollapse.click();
        teamBufferCollapse.click();
        String mmrHistoryUrl = driver.findElement(By.cssSelector("#team-buffer-mmr")).getAttribute("href");
        driver.findElement(By.cssSelector("#team-buffer-clear")).click();
        driver.get(mmrHistoryUrl);
        wait.until(presenceOfElementLocated(By.cssSelector("tr[data-team-id]")));
        switchTabsAndToggleInputs(driver, wait, "#team-mmr-tabs");
    }


    public static void switchTabsAndToggleInputs(WebDriver driver, WebDriverWait wait, String tabContainerSelector)
    {
        driver.findElement(By.cssSelector(tabContainerSelector))
            .findElements(By.cssSelector(".nav-link"))
            .forEach(l->{
                String contentId = l.getAttribute("data-target");
                clickAndWait(driver, wait, "#" + l.getAttribute("id") , contentId +  ".show.active");
                toggleInputs(driver, contentId);
                clickCanvases(driver, contentId);
            });
    }

    public static void clickAndWait(WebDriver driver, WebDriverWait wait, String clickSelector, String waitSelector)
    {
        driver.findElement(By.cssSelector(clickSelector)).click();
        wait.until(presenceOfElementLocated(By.cssSelector(waitSelector)));
    }

    public static void getAndWait(WebDriver driver, WebDriverWait wait, String url, String waitSelector)
    {
        driver.get(url);
        wait.until(presenceOfElementLocated(By.cssSelector(waitSelector)));
    }

    private void checkJsErrors()
    {
        if(driver.findElement(By.cssSelector("body")).getAttribute("class").contains("js-error-detected"))
        {
            failed = true;
            fail("JavaScript errors detected");
        }
    }

    public void getAndWaitAndCheckJsErrors(WebDriver driver, WebDriverWait wait, String url, String waitSelector)
    {
        getAndWait(driver, wait, url, waitSelector);
        checkJsErrors();
    }

    public static void toggleInput(WebDriver driver, String selector, String value)
    {
        WebElement element = driver.findElement(By.cssSelector(selector));
        element.sendKeys(value);
        element.sendKeys("");
    }

    public static void toggleInputs(WebDriver driver, String containerSelector)
    {
        toggleCheckboxes(driver, containerSelector);
        toggleSelects(driver, containerSelector);
        toggleRadios(driver, containerSelector);
    }

    public static void toggleCheckboxes(WebDriver driver, String containerSelector)
    {
        driver.findElements(By.cssSelector(containerSelector + " input[type=\"checkbox\"]")).forEach(c->{
            c.click();
            c.click();
        });
    }

    public static void toggleSelects(WebDriver driver, String containerSelector)
    {
        driver.findElements(By.cssSelector(containerSelector + " select")).stream()
            .map(Select::new)
            .forEach(s->{
                for(int i = 0; i < s.getOptions().size(); i++) s.selectByIndex(i);
                for(int i = s.getOptions().size() - 1; i >= 0; i--) s.selectByIndex(i);
            });
    }

    public static void toggleRadios(WebDriver driver, String containerSelector)
    {
        List<WebElement> radios = driver.findElements(By.cssSelector(containerSelector + " input[type=\"radio\"]"));
        radios.forEach(WebElement::click);
        for(int i = radios.size() - 1; i >= 0; i--) radios.get(i).click();
    }

    public static void clickCanvases(WebDriver driver, String containerSelector)
    {
        driver.findElements(By.cssSelector(containerSelector + " section:not(.d-none) canvas")).forEach(WebElement::click);
    }

    private void setupData()
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.values()),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            10
        );
        Clan clan = clanDAO.merge(new Clan(null, "clanTag", Region.EU, "clanName"))[0];
        template.execute("UPDATE player_character SET clan_id = " + clan.getId());
        clanDAO.updateStats();
        teamDAO.updateRanks(SeasonGenerator.DEFAULT_SEASON_ID);
        leagueStatsDAO.calculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);
        queueStatsDAO.calculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);
        playerCharacterStatsDAO.calculate();
        seasonStateDAO.merge(SeasonGenerator.DEFAULT_SEASON_START.atStartOfDay().atOffset(ZoneOffset.UTC).plusMinutes(1),
            SeasonGenerator.DEFAULT_SEASON_ID);
    }

}
