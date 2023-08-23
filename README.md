# About
SC2 Pulse is the fastest and most reliable ranked ladder tracker for StarCraft&reg;2. It is a Spring Boot web application.
The [reference website](https://www.nephest.com/sc2/) (runs the latest release) is available 24/7.
[The public discord bot](https://discord.com/api/oauth2/authorize?client_id=908047161994915901&permissions=264192&scope=bot%20applications.commands)
is also available.
## Disclaimer
This application uses Battle.net&reg; API. 
This is not an official Blizzard Entertainment&reg; application.

## Warning
**Please do not use this project as a reference if you are new to programming**.

This is my first Spring Boot project, and it's old, there is a lot of legacy code that is poorly though out.

I use this project to learn new stuff, so it's just a bunch of helloworlds of different technologies that evolve over time.
Furthermore, I'm only interested in backend stack, so the frontend part of the project is a mess. I focus on new features
and I don't have time to rewrite the old code. The project will remain as it is until I implement all the planned features.

## Dependencies
* Java 11+
* PostgreSQL 13+ with btree_gist extension
* Maven 3
* BattleNet API access keys(you must use your own keys)
* Twitch API keys(you must use your own keys)
* Aligulac API key(you must use your own key)

## Testing
Run the tests to ensure that you have a valid environment set up. You must also pass the tests
before creating a PR.

A real PostgreSQL database with btree_gist extension is required for some integration tests.
**This should be only a testing db, as tests will drop/create the schema. Do not use your real DB.**

You can use the ```src/test/resources/application-private.properties``` file (ignored by git, used by a test config) 
to create a simple test config: 

```
spring.datasource.username={name}
spring.datasource.password={pasword}
spring.datasource.url=jdbc:postgresql://localhost:5432/{test_db_name}
spring.security.oauth2.client.registration.sc2-sys-us.client-id={client_id}
spring.security.oauth2.client.registration.sc2-sys-us.client-secret={client_secret}
spring.security.oauth2.client.registration.sc2-sys-eu.client-id={client_id}
spring.security.oauth2.client.registration.sc2-sys-eu.client-secret={client_secret}
spring.security.oauth2.client.registration.sc2-sys-kr.client-id={client_id}
spring.security.oauth2.client.registration.sc2-sys-kr.client-secret={client_secret}
discord.token={token}
spring.security.oauth2.client.registration.discord-lg.client-id={client_id}
spring.security.oauth2.client.registration.discord-lg.client-secret={client_secret}
twitch.client-id={client_id}
twitch.client-secret={client_secret}
com.nephest.battlenet.sc2.aligulac.api.key={api_key}
```

To run all the tests execute the following command in a terminal
```
mvn verify
```

### Selenium tests
The Firefox is used in selenium tests because it is one of the major browsers that is available on all platforms and has an
ESR version, which makes it easier to find a correct selenium driver for it. 

You can change the browser by modifying the `selenium.driver` application property.

## Running
The `dev` profile will help you to start the local server. Reload a browser tab to instantly see resource modifications.
Build project to hotswap(if possible) the new classes.

### Required application properties
You must set the following application properties:
```
server.port={port}
spring.datasource.username={name}
spring.datasource.password={pasword}
spring.datasource.url=jdbc:postgresql://localhost:5432/{db_name}
spring.security.oauth2.client.registration.sc2-sys-us.client-id={client_id}
spring.security.oauth2.client.registration.sc2-sys-us.client-secret={client_secret}
spring.security.oauth2.client.registration.sc2-sys-eu.client-id={client_id}
spring.security.oauth2.client.registration.sc2-sys-eu.client-secret={client_secret}
spring.security.oauth2.client.registration.sc2-sys-kr.client-id={client_id}
spring.security.oauth2.client.registration.sc2-sys-kr.client-secret={client_secret}
spring.security.oauth2.client.registration.sc2-lg-eu.client-id = {client_id}
spring.security.oauth2.client.registration.sc2-lg-eu.client-secret = {client_secret}
spring.security.oauth2.client.registration.sc2-lg-us.client-id = {client_id}
spring.security.oauth2.client.registration.sc2-lg-us.client-secret = {client_secret}
spring.security.oauth2.client.registration.sc2-lg-kr.client-id = {client_id}
spring.security.oauth2.client.registration.sc2-lg-kr.client-secret = {client_secret}
discord.token={token}
discord.bot.invite.url={discord_bot_invite_url}
spring.security.oauth2.client.registration.discord-lg.client-id={client_id}
spring.security.oauth2.client.registration.discord-lg.client-secret={client_secret}
twitch.client-id={client_id}
twitch.client-secret={client_secret}
com.nephest.battlenet.sc2.aligulac.api.key={api_key}
```

### Optional application properties 
The server will run without errors if these properties are missing, but the corresponding features will be disabled.
```
com.nephest.battlenet.sc2.replaystats.api.key
```

### Misc
You can use the ```src/main/resources/application-private.properties``` file (ignored by git, used by config) 
for private/local application properties

Use [DB init script](src/main/resources/schema-postgres.sql) to create a fresh DB.

To run the local server execute the following command in a terminal
```
mvn spring-boot:run
```

Scheduled tasks are disabled in the dev mode. You can remove the `@Profile` annotation from the Cron class if you want 
to run the tasks(like ladder scans) in the dev mode.
## Blizzard API clients
The default config expects you to use different API clients/keys for each region. You **must** reduce the 
`BlizzardSC2API.REQUESTS_PER_SECOND_CAP` to 25 if you want to use one API client for all regions. 
## Alternative update
### Legacy and profile ladders
The Blizzard API can sometimes break and return stale data. The app checks the API state before every update and will
switch the endpoint route if any problems are found. This happens automatically and individually for each region,
so you can have a situation when KR region uses the alternative route, while other regions use the usual route.

Alternative update limitations:
* no league tiers
* no BattleTags
* partial racial info
* slower update

The missing info can be fetched from the main endpoint when it's back up(it happens automatically).

Original idea by [Keiras](http://keiras.cz/)

### Forced API host
You can manually remap some endpoints to use another API host. This is useful when one region is broken but others are not.
See `addForceAPIRegion` method of the [AdminController](src/main/java/com/nephest/battlenet/sc2/web/controller/AdminController.java).
Set application property `com.nephest.battlenet.sc2.api.force.region.auto` to `true` to enable auto remap algorithm.

### Web API
The Blizzard web API can be used as a last resort when everything else breaks. It is disabled by default. Some
endpoints can be manually redirected to web API via AdminController. You can enable auto web API by setting the 
`com.nephest.battlenet.sc2.ladder.alternative.web.auto` application property to `true`.

#### Blizzard ToS compatibility
Even though the API is not forbidden via robots.txt, the 
[Blizzard Developer API Terms Of Use](https://www.blizzard.com/en-us/legal/a2989b50-5f16-43b1-abec-2ae17cc09dd6/blizzard-developer-api-terms-of-use) 
clause directly forbids it
```
You May Not Data Mine Blizzard Products Or Services. Except as permitted through authorized use of the 
Blizzard Developer APIs, You will not perform any data-mining, scraping, crawling, or use any processes that sends 
automated queries to Blizzard or any Blizzard game, service, or website, or use any other similar methods or tools 
to gather or extract data other information from Blizzard or any Blizzard game or service.
```
To ensure that the potential violation is a minor one, the following rules are applied:
* only required data is pulled every hour
    * 1 full ladder scan
    * 2 small scans(1v1)
    * match history of top 500 1v1 players
* a very low request rate is used
* the data is considered as if it came from the regular dev API and the relevant ToS and Privacy Policy are applied.

**Use it at your own risk.**

## Task configuration
[Cron class](src/main/java/com/nephest/battlenet/sc2/config/Cron.java) contains all scheduled tasks.
## Application properties
* `com.nephest.battlenet.sc2.url.public` public URL of your service
* `com.nephest.battlenet.sc2.mmr.history.main.length` 1v1 mmr history length in days, 180 by default.
* `com.nephest.battlenet.sc2.mmr.history.secondary.length` team mmr history length in days, 180 by default.
* `com.nephest.battlenet.sc2.ladder.regions` set of regions that will be updated, currently active regions by default.
* `com.nephest.battlenet.sc2.db-dump-file` path to the database dump.
* `contacts.email`
* `contacts.discord.server`
* `contacts.discord.server.name`
* `contacts.twitter`
* `contacts.github`
* `cache.ttl` TTL(duration) of cache entries, 7 days by default
* `cache.size` max size of the cache, 20000 by default
* `security.remember-me.token.key` key for signing remember-me tokens
* `security.remember-me.token.max-age` Max age(duration) of the cookie, P3650D(~10 years) by default
* `com.nephest.battlenet.sc2.ladder.alternative.web.queue.separate` controls if a separate, independent, optimized
queue will be used when the web flag is active. Boolean, false by default.
## Contributing
Want to make a bug report/feature request? Any contributions are welcome, see [CONTRIBUTING](CONTRIBUTING.md) for 
more information.
## Licenses
* [Main license](LICENSE.txt)
* [3rd party licenses](3rd-party-licenses.txt)
## Blizzard ToS
SC2 Pulse is fully compliant with the Blizzard ToS.
* Requests per hour cap is guaranteed if your clock desync is within 5 seconds.
* Requests per second cap is optimistically synchronized on the client side. It works most of the time, but sometimes
  you may see a 429 response.
* BattleTags, player names, and matches are removed after 30 days from the moment they were deleted from the API
## Trademarks
Battle.net, Blizzard Entertainment and StarCraft are trademarks or registered trademarks of Blizzard Entertainment,
 Inc. in the U.S. and/or other countries. 
