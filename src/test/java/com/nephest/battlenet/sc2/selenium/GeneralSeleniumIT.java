// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.selenium;

/*
    This test does all UI interactions and searches for any js errors
 */

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable;
import static org.openqa.selenium.support.ui.ExpectedConditions.invisibilityOf;
import static org.openqa.selenium.support.ui.ExpectedConditions.invisibilityOfElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;
import static org.openqa.selenium.support.ui.ExpectedConditions.visibilityOf;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.ClanMemberEvent;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberEventDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.QueueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.service.StatsService;
import com.nephest.battlenet.sc2.web.util.WebContextUtil;
import io.github.bonigarcia.wdm.WebDriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
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

    public static final int TIMEOUT_MILLIS = 15000;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private ClanDAO clanDAO;

    @Autowired
    private ClanMemberDAO clanMemberDAO;

    @Autowired
    private ClanMemberEventDAO clanMemberEventDAO;

    @Autowired
    private LeagueStatsDAO leagueStatsDAO;

    @Autowired
    private QueueStatsDAO queueStatsDAO;

    @Autowired
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private SeasonStateDAO seasonStateDAO;

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

    @Autowired
    private MatchParticipantDAO matchParticipantDAO;

    @Autowired
    private PopulationStateDAO populationStateDAO;

    @Autowired
    private WebContextUtil webContextUtil;

    @Autowired
    private JdbcTemplate template;

    private static WebDriver driver;
    private static WebDriverWait wait;
    private static JavascriptExecutor js;

    private static String root;

    private static boolean dataReady = false;
    private static int port;

    @BeforeAll
    public static void init
    (
        @Autowired DataSource dataSource,
        @Autowired AccountDAO accountDAO,
        @Autowired ServletWebServerApplicationContext webServerAppCtxt,
        @Value("${selenium.driver}") String seleniumDriver,
        @Value("${selenium.driver.headless:#{'true'}}") boolean headless
    )
    throws Exception
    {
        WebDriverManager.getInstance(seleniumDriver).setup();
        driver = initDriver(seleniumDriver, headless);
        wait = new WebDriverWait(driver, Duration.ofMillis(TIMEOUT_MILLIS));
        js = (JavascriptExecutor) driver;
        port = webServerAppCtxt.getWebServer().getPort();
        root = "http://localhost:" + port;
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
    }

    public static WebDriver initDriver(String seleniumDriver, boolean headless)
    throws Exception
    {
        String lowerCase = seleniumDriver.toLowerCase();
        switch (lowerCase)
        {
            case "firefox":
                return initFirefoxDriver(seleniumDriver, headless);
            case "chrome":
            case "chromium":
                return initChromeDriver(seleniumDriver, headless);
            default:
                return (WebDriver) Class
                    .forName("org.openqa.selenium." + getDriverPackage(seleniumDriver) + "." + seleniumDriver + "Driver")
                    .getDeclaredConstructor()
                    .newInstance();
        }
    }

    private static WebDriver initFirefoxDriver(String seleniumDriver, boolean headless)
    throws Exception
    {
        FirefoxOptions options = new FirefoxOptions();
        if(headless) options.addArguments("--headless");
        return (WebDriver) Class
            .forName("org.openqa.selenium." + getDriverPackage(seleniumDriver) + "." + seleniumDriver + "Driver")
            .getDeclaredConstructor(FirefoxOptions.class)
            .newInstance(options);
    }

    private static WebDriver initChromeDriver(String seleniumDriver, boolean headless)
    throws Exception
    {
        ChromeOptions options = new ChromeOptions();
        if(headless) options.addArguments("--headless=new");
        return (WebDriver) Class
            .forName("org.openqa.selenium." + getDriverPackage(seleniumDriver) + "." + seleniumDriver + "Driver")
            .getDeclaredConstructor(ChromeOptions.class)
            .newInstance(options);
    }

    //setup data in before each for easier auto wiring.
    @BeforeEach
    public void setupDataOnce()
    {
        if(dataReady) return;

        setupData();
        dataReady = true;
    }

    public static String getDriverPackage(String driver)
    {
        return driver.equals("InternetExplorer") ? "ie" : driver.toLowerCase();
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        driver.close();
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testAboutUI()
    {
        getAndWaitAndCheckJsErrors(driver, wait, root + "/about", "#about");
    }

    @Test
    public void testPrivacyUI()
    {
        getAndWaitAndCheckJsErrors(driver, wait, root + "/privacy-policy", "#privacy");
    }

    @Test
    public void testStatusUI()
    {
        getAndWaitAndCheckJsErrors(driver, wait, root + "/status", "#status");
    }

    @Test
    public void testContactsUI()
    {
        getAndWaitAndCheckJsErrors(driver, wait, root + "/contacts", "#contacts");
    }

    @Test
    public void testDiscordBotUI()
    {
        getAndWaitAndCheckJsErrors(driver, wait, root + "/discord/bot", "#faq");
    }

    @Test
    public void testSitemap()
    {
        String url = webContextUtil.getPublicUrl()
            + "?season=" + SeasonGenerator.DEFAULT_SEASON_ID
            + "&queue=LOTV_1V1"
            + "&team-type=ARRANGED"
            + "&us=true&eu=true&kr=true&cn=true"
            + "&bro=true&sil=true&gol=true&pla=true&dia=true&mas=true&gra=true"
            + "&page=0&type=ladder&ratingAnchor=99999&idAnchor=0&count=1";
        url = url.replaceAll("&", "&amp;");
        driver.get(root + "/sitemap.xml");
        //xml document with XPath locator doesn't work, using text match instead
        assertTrue(driver.getPageSource().contains("<loc>" + url + "</loc>"));
    }

    @Test
    public void testLadderUI()
    {
        loadMainPage(driver, wait);
        clickAndWait(driver, wait, "#stats-tab", "#stats.show.active");
        clickAndWait(driver, wait, "#form-ladder button[type=\"submit\"]", "tr[data-team-id]");

        //popovers
        togglePopovers(driver, wait, driver.findElement(By.cssSelector("#ladder-tab")), "#ladder tbody tr:nth-child(10n)");

        //pagination
        clickAndWait(driver, wait,
            "#ladder-top li.page-item:not(.disabled) a[data-page-count=\"1\"]",
            "#ladder-top li.page-item.disabled a[data-page-count=\"0\"][data-page-number=\"2\"]");
        //back
        clickAndWait(driver, wait,
            "#ladder-top li.page-item:not(.disabled) a[data-page-count=\"-1\"]",
            "#ladder-top li.page-item.disabled a[data-page-count=\"0\"][data-page-number=\"1\"]");

        //character
        clickAndWait(driver, wait, "#ladder a.player-link", "#player-info.modal.show");
        switchTabsAndToggleInputs(driver, wait, "#player-stats-tabs");
        clickAndWait(driver, wait, "#player-info button.close:not(.close-left)", ".no-popup-hide:not(.d-none)");

        //population
        switchTabsAndToggleInputs(driver, wait, "#stats-tabs");

        checkJsErrors();
    }

    @Test
    public void testOnline()
    {
        loadMainPage(driver, wait);
        clickAndWait(driver, wait, "#online-tab", "#online.show.active");
        js.executeScript
        (
            "document.querySelector('#online-to').value="
                + "'"
                + SeasonGenerator.DEFAULT_SEASON_START
                    .toLocalDate()
                    .plusDays(1)
                + "'"
        );
        clickAndWait(driver, wait, "#form-online button[type=\"submit\"]", "#online-data:not(.d-none)");
        clickCanvases(driver, "#online-data");
        checkJsErrors();
    }


    @Test
    public void testSearch()
    {
        loadMainPage(driver, wait);
        clickAndWait(driver, wait, "#search-all-tab", "#search-all.show.active");

        WebElement searchInput = driver.findElement(By.cssSelector("#search-player-name"));
        //player
        clickAndWait(driver, wait, "#search-tab", "#search.show.active");
        searchInput.sendKeys("character");
        clickAndWait(driver, wait, "#form-search button[type=\"submit\"]", "#search-result-all:not(.d-none)");

        searchInput.sendKeys(Keys.HOME, Keys.chord(Keys.SHIFT, Keys.END), Keys.BACK_SPACE);
        //find by bnet profile link
        searchInput.sendKeys("https://starcraft2.blizzard.com/en-us/profile/1/1/10");
        clickAndWait(driver, wait, "#form-search button[type=\"submit\"]", "#search-result-all:not(.d-none) tbody tr");

        //clan
        testClanCursorSearch(driver, wait);

        checkJsErrors();
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

    @Test
    public void testSettings()
    {
        loadMainPage(driver, wait);
        clickAndWait(driver, wait, "#settings-tab", "#settings.show.active");
        toggleInputs(driver, "#settings");
        checkJsErrors();
    }

    @Test
    public void testMmrHistory()
    {
        loadMainPage(driver, wait);
        clickAndWait(driver, wait, "#stats-tab", "#stats.show.active");
        clickAndWait(driver, wait, "#form-ladder button[type=\"submit\"]", "tr[data-team-id]");
        driver.findElements(By.cssSelector("#ladder .team-buffer-toggle")).stream()
            .limit(3)
            .forEach(e->waitToBeClickableAndClick(wait, e));
        clickDropdowns(driver, wait, "#team-buffer");
        WebElement teamBufferCollapse = driver.findElement(By.cssSelector("#team-buffer-collapse"));
        teamBufferCollapse.click();
        teamBufferCollapse.click();
        String mmrHistoryUrl = driver.findElement(By.cssSelector("#team-buffer-mmr")).getAttribute("href");
        driver.findElement(By.cssSelector("#team-buffer-clear")).click();
        driver.get(mmrHistoryUrl);
        wait.until(presenceOfElementLocated(By.cssSelector("tr[data-team-id]")));
        switchTabsAndToggleInputs(driver, wait, "#team-mmr-tabs");
        checkJsErrors();
    }

    @Test
    public void testVersus()
    {
        loadMainPage(driver, wait);
        clickAndWait(driver, wait, "#search-all-tab", "#search-all.show.active");

        clickAndWait(driver, wait, "#search-clan-tab", "#search-clan.show.active");
        driver.findElement(By.cssSelector("#clan-search-tag-name")).sendKeys("clan");
        clickAndWait(driver, wait, "#form-search-clan button[type=\"submit\"]", "#search-result-clan-all:not(.d-none)");
        driver.findElements(By.cssSelector("#search-result-clan .team-buffer-toggle")).stream()
            .limit(2)
            .forEach(WebElement::click);
        driver.findElement(By.cssSelector("#team-buffer-collapse")).click();
        Select groupSelect = new Select(driver.findElement(By.cssSelector(".buffer-group")));
        groupSelect.selectByIndex(1);
        clickAndWait(driver, wait, "#team-buffer-versus", "#versus-modal:not(.d-none)");
        driver.findElement(By.cssSelector("#team-buffer-collapse")).click();
        driver.findElement(By.cssSelector("#team-buffer-clear")).click();
        clickAndWait(driver, wait, "#load-more-matches-versus", "#matches-versus tbody tr:nth-child(20)");
        clickAndWait(driver, wait, "#load-more-matches-versus", "#matches-versus tbody tr:nth-child(25)");
        toggleInputs(driver, "[data-view-name=\"versus\"]");
        clickAndWait(driver, wait, "#versus-modal .close:not(.close-left)", ".tab-content-main:not(.d-none)");
        checkJsErrors();
    }

    @Test
    public void testClanGroup()
    {
        loadMainPage(driver, wait);
        clickAndWait(driver, wait, "#search-all-tab", "#search.show.active");
        clickAndWait(driver, wait, "#search-clan-tab", "#search-clan.show.active");
        clickAndWait(driver, wait, "#form-search-clan button[type=\"submit\"]", "#search-result-clan-all:not(.d-none)");
        clickAndWait(driver, wait, "#search-result-clan .clan-auto-search", "#group:not(.d-none)");
        switchTabsAndToggleInputs(driver, wait, "#group-tabs");
        checkJsErrors();
    }

    @Test
    public void testAccountGroup()
    {
        loadMainPage(driver, wait);
        clickAndWait(driver, wait, "#stats-tab", "#stats.show.active");
        clickAndWait(driver, wait, "#form-ladder button[type=\"submit\"]", "tr[data-team-id]");
        clickAndWait(driver, wait, "#ladder a.player-link", "#player-info.modal.show");
        clickAndWait(driver, wait, "#player-info .group-link", "#group:not(.d-none)");
        switchTabsAndToggleInputs(driver, wait, "#group-tabs");
        checkJsErrors();
    }

    @Test
    public void testStreamUI()
    {
        loadMainPage(driver, wait);
        clickAndWait(driver, wait, "#search-all-tab", "#search.show.active");
        clickAndWait(driver, wait, "#search-stream-tab",
            "#search-stream.show.active.loading-complete");
        toggleInputs(driver, "#search-stream");
        checkJsErrors();
    }

    @Test
    public void testTeamSearchUI()
    {
        loadMainPage(driver, wait);
        clickAndWait(driver, wait, "#search-all-tab", "#search.show.active");
        clickAndWait(driver, wait, "#search-team-tab", "#search-team.show.active");

        testTeamSearchSelects();
        testTeamSearchRating();
        testTeamSearchWins();
    }

    private void testTeamSearchSelects()
    {
        Select queue = new Select(driver.findElement(By.cssSelector("#search-team-queue")));
        Select league = new Select(driver.findElement(By.cssSelector("#search-team-league")));
        Select region = new Select(driver.findElement(By.cssSelector("#search-team-region")));
        for(int qi = 0; qi < queue.getOptions().size(); qi++)
        {
            queue.selectByIndex(qi);
            for(int li = 0; li < league.getOptions().size(); li++)
            {
                league.selectByIndex(li);
                for(int ri = 0; ri < region.getOptions().size(); ri++)
                {
                    region.selectByIndex(ri);
                    clickAndWait
                    (
                        driver,
                        wait,
                        "#search-team button[type=\"submit\"]",
                        "#team-search-teams *[data-team-id]"
                    );
                    toggleSelects(driver.findElements(By.cssSelector("#search-team-sort")));
                    js.executeScript("document.querySelector(\"#team-search-teams\").classList.add(\"d-none\");");
                }
            }
        }
        queue.selectByIndex(0);
        league.selectByIndex(0);
        region.selectByIndex(0);
        checkJsErrors();
    }

    private void testTeamSearchRating()
    {
        WebElement mmrInput = driver.findElement(By.cssSelector("#search-team-rating"));
        mmrInput.sendKeys("10");
        clickAndWait
        (
            driver,
            wait,
            "#search-team button[type=\"submit\"]",
            "#team-search-teams *[data-team-id]"
        );
        toggleSelects(driver.findElements(By.cssSelector("#search-team-sort")));
        js.executeScript("document.querySelector(\"#team-search-teams\").classList.add(\"d-none\");");
        checkJsErrors();
    }

    private void testTeamSearchWins()
    {
        WebElement winsInput = driver.findElement(By.cssSelector("#search-team-wins"));
        winsInput.sendKeys("30");
        clickAndWait
        (
            driver,
            wait,
            "#search-team button[type=\"submit\"]",
            "#team-search-teams *[data-team-id]"
        );
        toggleSelects(driver.findElements(By.cssSelector("#search-team-sort")));
        js.executeScript("document.querySelector(\"#team-search-teams\").classList.add(\"d-none\");");
        checkJsErrors();
    }

    public static void switchTabsAndToggleInputs(WebDriver driver, WebDriverWait wait, String tabContainerSelector)
    {
        driver.findElement(By.cssSelector(tabContainerSelector))
            .findElements(By.cssSelector(".nav-link"))
            .forEach(l->{
                String contentId = l.getAttribute("data-target");
                clickAndWait(driver, wait, "#" + l.getAttribute("id") , contentId +  ".show.active");
                waitForDynamicContent(driver, wait, contentId);
                toggleInputs(driver, contentId);
                clickCanvases(driver, contentId);
            });
    }

    public static void loadMainPage(WebDriver driver, WebDriverWait wait)
    {
        getAndWait(driver, wait, root + "/", "#form-ladder-season-picker option");
    }

    public static void clickAndWait(WebDriver driver, WebDriverWait wait, String clickSelector, String waitSelector)
    {
        WebElement e = driver.findElement(By.cssSelector(clickSelector));
        waitToBeClickableAndClick(wait, e);
        wait.until(presenceOfElementLocated(By.cssSelector(waitSelector)));
    }

    public static void waitToBeClickableAndClick(WebDriverWait wait, WebElement element)
    {
        wait.until(elementToBeClickable(element));
        element.click();
    }

    public static void getAndWait(WebDriver driver, WebDriverWait wait, String url, String waitSelector)
    {
        driver.get(url);
        wait.until(presenceOfElementLocated(By.cssSelector(waitSelector)));
    }

    private static void checkJsErrors()
    {
        if(driver.findElement(By.cssSelector("body")).getAttribute("class").contains("js-error-detected"))
        {
            fail("JavaScript errors detected");
        }
    }

    public static void getAndWaitAndCheckJsErrors
    (
        WebDriver driver,
        WebDriverWait wait,
        String url,
        String waitSelector
    )
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

    public static void makeEnhancedInputsVisible(WebDriver driver, String containerSelector)
    {
        driver.findElements(By.cssSelector(containerSelector + " .enhanced"))
            .forEach(e->js.executeScript("arguments[0].classList.add('enhanced-ctl-visible')", e));
    }

    public static void toggleInputs(WebDriver driver, String containerSelector)
    {
        makeEnhancedInputsVisible(driver, containerSelector);
        toggleCheckboxes(driver, containerSelector);
        toggleSelects(driver, containerSelector);
        toggleRadios(driver, containerSelector);
    }

    public static void toggleCheckboxes(Collection<? extends WebElement> checkboxes)
    {
        for(WebElement checkbox : checkboxes)
        {
            checkbox.click();
            checkbox.click();
        }
    }

    public static void toggleCheckboxes(WebDriver driver, String containerSelector)
    {
        toggleCheckboxes(driver.findElements(By.cssSelector(
            containerSelector + " input[type=\"checkbox\"]")));
    }

    public static void toggleSelects(Collection<? extends WebElement> selects)
    {
        selects.stream()
            .map(Select::new)
            .forEach(s->{
                if(s.isMultiple())
                {
                    for(int i = 0; i < 2; i++)
                        s.getOptions().forEach(option->
                        {
                            scrollTo(driver, option);
                            boolean wasSelected = option.isSelected();
                            new Actions(driver)
                                .keyDown(Keys.CONTROL)
                                .click(option)
                                .keyUp(Keys.CONTROL)
                                .build()
                                .perform();
                            wait.until(d->option.isSelected() != wasSelected);
                        });
                }
                else
                {
                    for(int i = 0; i < s.getOptions().size(); i++) s.selectByIndex(i);
                    for(int i = s.getOptions().size() - 1; i >= 0; i--) s.selectByIndex(i);
                }
            });
    }

    public static void toggleSelects(WebDriver driver, String containerSelector)
    {
        toggleSelects(driver.findElements(By.cssSelector(containerSelector + " select")));
    }

    public static void toggleRadios(List<? extends WebElement> radios)
    {
        radios.forEach(WebElement::click);
        for(int i = radios.size() - 1; i >= 0; i--) radios.get(i).click();
    }

    public static void toggleRadios(WebDriver driver, String containerSelector)
    {
        toggleRadios(driver.findElements(By.cssSelector(containerSelector + " input[type=\"radio\"]")));
    }

    public static void clickCanvases(WebDriver driver, String containerSelector)
    {
        driver.findElements(By.cssSelector(containerSelector + " section:not(.d-none) canvas")).forEach(WebElement::click);
    }

    public static void togglePopovers
    (WebDriver driver, WebDriverWait wait, WebElement nonPopoverElement, String containerSelector)
    {
        for(WebElement elem : driver.findElements(By.cssSelector(containerSelector + " [data-toggle=\"popover\"]")))
        {
            elem.click();
            wait.until(presenceOfElementLocated(By.cssSelector(".popover.show")));
            nonPopoverElement.click();
            wait.until(invisibilityOfElementLocated(By.cssSelector(".popover.show")));
        }
    }

    public static void clickDropdowns
    (WebDriver driver, WebDriverWait wait, String containerSelector)
    {
        for(WebElement dropdown : driver.findElements(By.cssSelector(containerSelector + " [data-toggle=\"dropdown\"]")))
        {
            String id = dropdown.getAttribute("id");
            WebElement menu = driver.findElement(
                By.cssSelector(".dropdown-menu[aria" + "-labelledby=\"" + id + "\"]"));
            for (WebElement menuItem : menu.findElements(By.cssSelector(".dropdown-item")))
            {
                dropdown.click();
                wait.until(visibilityOf(menu));
                menuItem.click();
                wait.until(invisibilityOf(menu));
            }
        }
    }

    public static void scrollTo(WebDriver driver, WebElement element)
    {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
    }

    public static void waitForDynamicContent(WebDriver driver, WebDriverWait wait, String selector)
    {
        WebElement container = driver.findElement(By.cssSelector(selector));
        if(!container.getAttribute("class").contains("container-loading")) return;

        WebElement loadingIndicators = container
            .findElement(By.cssSelector(".container-indicator-loading-default"));
        while
        (
            !container.getAttribute("class").contains("loading-complete")
            && !container.getAttribute("class").contains("loading-error")
        )
        {
            scrollTo(driver, loadingIndicators);
            ExpectedCondition<Boolean> contentLoaded = ExpectedConditions.or
            (
                ExpectedConditions.attributeContains(container, "class", "loading-complete"),
                ExpectedConditions.attributeContains(container, "class", "loading-error"),
                ExpectedConditions.attributeContains(container, "class", "loading-none")
            );
            wait.until(contentLoaded);
        }
    }

    private void setupData()
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.values()),
            List.of(BaseLeague.LeagueType.values()),
            List.copyOf(QueueType.getTypes(StatsService.VERSION)),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            10
        );
        template.update("UPDATE team SET last_played = NOW()");
        Clan clan1 = clanDAO.merge(Set.of(new Clan(null, "clanTag1", Region.EU, "clanName1")))
            .iterator().next();
        setupClanData
        (
            template
                .queryForList("SELECT id FROM player_character WHERE id <= 140", Long.class),
            clan1
        );
        Clan clan2 = clanDAO.merge(Set.of(new Clan(null, "clanTag2", Region.EU, "clanName2")))
            .iterator().next();
        setupClanData
        (
            template.queryForList
            (
                "SELECT id FROM player_character WHERE id BETWEEN 141 AND 280",
                Long.class
            ),
            clan2
        );
        OffsetDateTime startDateTime = SC2Pulse.offsetDateTime();
        int matchCount = (int) Math.round(ladderMatchDAO.getResultsPerPage() * 2.5);
        seasonGenerator.createMatches
        (
            BaseMatch.MatchType._1V1,
            1, 280, new long[]{1}, new long[]{280},
            startDateTime, Region.EU, 1, 28,
            matchCount
        );
        matchParticipantDAO.identify(SeasonGenerator.DEFAULT_SEASON_ID, startDateTime.minusYears(1));
        matchParticipantDAO.calculateRatingDifference(startDateTime.minusYears(1));
        clanDAO.updateStats(List.of(clan1.getId(), clan2.getId()));
        leagueStatsDAO.calculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);
        populationStateDAO.takeSnapshot(List.of(SeasonGenerator.DEFAULT_SEASON_ID));
        teamDAO.updateRanks(SeasonGenerator.DEFAULT_SEASON_ID);
        queueStatsDAO.calculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);
        playerCharacterStatsDAO.calculate();
        seasonStateDAO.merge(SeasonGenerator.DEFAULT_SEASON_START.plusMinutes(1),
            SeasonGenerator.DEFAULT_SEASON_ID);
    }
    
    private void setupClanData(List<Long> charIds, Clan clan)
    {
        Set<ClanMember> cm = charIds
            .stream()
            .map(id->new ClanMember(id, clan.getId()))
            .collect(Collectors.toSet());
        clanMemberDAO.merge(cm);
        Set<ClanMemberEvent> cme = charIds.stream()
            .map(id->new ClanMemberEvent(
                id, clan.getId(), ClanMemberEvent.EventType.JOIN, SC2Pulse.offsetDateTime()))
            .collect(Collectors.toSet());
        clanMemberEventDAO.merge(cme);
    }

}
