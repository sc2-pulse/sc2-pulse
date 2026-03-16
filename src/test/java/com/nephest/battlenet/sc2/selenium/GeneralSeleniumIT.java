// Copyright (C) 2020-2026 Oleksandr Masniuk
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

import com.clickhouse.client.api.Client;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.util.DbTestUtil;
import com.nephest.battlenet.sc2.model.util.TestDbInitializer;
import com.nephest.battlenet.sc2.web.util.WebContextUtil;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.selenium.BrowserWebDriverContainer;

@SpringBootTest
(
    classes = AllTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT
)
@ActiveProfiles({"dev", "default"})
@TestPropertySource("classpath:application.properties")
public class GeneralSeleniumIT
{

    public static final int TIMEOUT_MILLIS = 15000;

    @Autowired
    private TestDbInitializer testDbInitializer;

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

    private static BrowserWebDriverContainer BROWSER_CONTAINER;

    @BeforeAll
    public static void init
    (
        @Autowired DataSource dataSource,
        @Autowired Client clickHouseClient,
        @Autowired AccountDAO accountDAO,
        @Autowired ServletWebServerApplicationContext webServerAppCtxt,
        @Value("${org.testcontainers.selenium.image.name}") String seleniumImageName,
        @Value("${org.testcontainers.selenium.headless:#{'true'}}") boolean headless,
        @Value("${org.testcontainers.host:'host.docker.internal'}") String testContainersHost
    )
    throws Exception
    {
        port = webServerAppCtxt.getWebServer().getPort();
        root = "http://" + testContainersHost + ":" + port;
        BROWSER_CONTAINER
            = new BrowserWebDriverContainer(seleniumImageName);
        driver = initDriver(seleniumImageName, headless, testContainersHost, root);
        wait = new WebDriverWait(driver, Duration.ofMillis(TIMEOUT_MILLIS));
        js = (JavascriptExecutor) driver;
        DbTestUtil.initDb(dataSource, clickHouseClient);
    }

    private static WebDriver initDriver
    (
        String seleniumImageName,
        boolean headless,
        String testContainersHost,
        String root
    )
    {
        BROWSER_CONTAINER.start();
        return new RemoteWebDriver
        (
            BROWSER_CONTAINER.getSeleniumAddress(),
            getCapabilities(seleniumImageName, headless, testContainersHost, root)
        );
    }

    public static Capabilities getCapabilities
    (
        String seleniumImageName,
        boolean headless,
        String testContainersHost,
        String root
    )
    {
        String seleniumImageNameLower = seleniumImageName.toLowerCase();
        if(seleniumImageNameLower.contains("firefox"))
            return getFirefoxCapabilities(headless, testContainersHost);
        if(seleniumImageNameLower.contains("chrome"))
            return getChromeCapabilities(headless, root);
        return new MutableCapabilities();
    }

    public static Capabilities getChromeCapabilities(boolean headless, String root)
    {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--unsafely-treat-insecure-origin-as-secure=" + root);
        if(headless)
        {
            options.addArguments("--headless=new");
            options.addArguments("--window-size=1920,1080");
        }
        return options;
    }

    public static Capabilities getFirefoxCapabilities(boolean headless, String testContainersHost)
    {
        FirefoxOptions options = new FirefoxOptions();
        options.addPreference("dom.securecontext.allowlist", testContainersHost)
            .addPreference("security.mixed_content.upgrade_display_content", false);
        if(headless)
        {
            options.addArguments("--headless");
            options.addArguments("--window-size=1920,1080");
        }
        return options;
    }

    //setup data in before each for easier auto wiring.
    @BeforeEach
    public void setupDataOnce()
    {
        if(dataReady) return;

        testDbInitializer.setupData();
        dataReady = true;
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource, @Autowired Client clickHouseClient)
    throws Exception
    {
        driver.close();
        BROWSER_CONTAINER.close();
        DbTestUtil.clearDb(dataSource, clickHouseClient);
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
            + "&teamType=ARRANGED"
            + "&region=US&region=EU&region=KR&region=CN"
            + "&league=BRONZE&league=SILVER&league=GOLD&league=PLATINUM&league=DIAMOND"
            + "&league=MASTER&league=GRANDMASTER"
            + "&type=ladder&sort=-rating";
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
            "tr[data-team-id='180']");
        //back
        clickAndWait(driver, wait,
            "#ladder-top li.page-item:not(.disabled) a[data-page-count=\"-1\"]",
            "tr[data-team-id='280']");

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
        clickCanvases(driver, wait, "#online-data");
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
        clickDropdown(driver, wait, driver.findElement(By.cssSelector("#team-buffer-copy")));
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
                clickCanvases(driver, wait, contentId);
            });
    }

    public static void loadMainPage(WebDriver driver, WebDriverWait wait)
    {
        getAndWait(driver, wait, root + "/", "#form-ladder-season-picker option");
    }

    public static void waitForInvisibilityOfLoadingScreen(WebDriverWait wait)
    {
        wait.until(invisibilityOfElementLocated(By.id("status-generating-fullscreen")));
    }

    public static void clickAndWait(WebDriver driver, WebDriverWait wait, String clickSelector, String waitSelector)
    {
        waitForInvisibilityOfLoadingScreen(wait);
        WebElement e = driver.findElement(By.cssSelector(clickSelector));
        waitToBeClickableAndClick(wait, e);
        wait.until(presenceOfElementLocated(By.cssSelector(waitSelector)));
        waitForInvisibilityOfLoadingScreen(wait);
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

    public static void clickCanvases(WebDriver driver, WebDriverWait wait, String containerSelector)
    {
        driver.findElements(By.cssSelector(containerSelector + " section:not(.d-none) canvas"))
            .forEach(webElement->waitToBeClickableAndClick(wait, webElement));
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

    public static void clickDropdown(WebDriver driver, WebDriverWait wait, WebElement dropdown)
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

    public static void scrollToTop()
    {
        js.executeScript("window.scrollTo(0, 0);");
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
            scrollToTop();
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

}
