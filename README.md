# About
SC2 Pulse is the fastest and most reliable ranked ladder tracker for StarCraft&reg;2. It is a Spring Boot web application.
The [reference website](https://www.nephest.com/sc2/) (runs the latest release) is available 24/7.
## Features
* Flexible, user defined filters
* All seasons, races, team formats
* Player profile
    * Career summary
    * History: all teams and seasons, mmr chart, matches
    * Linked profiles
    * Public reports
* BattleNet integration
    * Log in with a BattleNet account
    * Easy access to personal stats
    * Create a personal ladder out of your favourite players
* Built-in statistics
    * Player count, team count, games played
    * League, region, race distribution
    * Daily activity
    * Steam-like online stats
    * Match-up win rates, average game duration, games played
* Tier MMR ranges
* Pro player info, [sc2revealed](http://sc2revealed.com/) and [aligulac](http://aligulac.com/) integration
    * Barcode unmasking
    * Pro player stats
    * Social media links
## Disclaimer
I use this project for learning, it is not production-ready.

This application uses Battle.net&reg; API. 
This is not an official Blizzard Entertainment&reg; application.
## Dependencies
* Java 11
* PostgreSQL 11 with btree_gist extension
* Maven 3
* BattleNet API access keys(you must use your own keys)
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
spring.security.oauth2.client.registration.sc2-sys-cn.client-id={client_id}
spring.security.oauth2.client.registration.sc2-sys-cn.client-secret={client_secret}
com.nephest.battlenet.sc2.aligulac.api.key={api_key}
```

To run all the tests execute the following command in a terminal
```
mvn verify
```

## Running
The `dev` profile will help you to start the local server. Reload a browser tab to instantly see resource modifications.
Build project to hotswap(if possible) the new classes.

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
spring.security.oauth2.client.registration.sc2-sys-cn.client-id={client_id}
spring.security.oauth2.client.registration.sc2-sys-cn.client-secret={client_secret}
spring.security.oauth2.client.registration.sc2-lg-eu.client-id = {client_id}
spring.security.oauth2.client.registration.sc2-lg-eu.client-secret = {client_secret}
spring.security.oauth2.client.registration.sc2-lg-us.client-id = {client_id}
spring.security.oauth2.client.registration.sc2-lg-us.client-secret = {client_secret}
spring.security.oauth2.client.registration.sc2-lg-kr.client-id = {client_id}
spring.security.oauth2.client.registration.sc2-lg-kr.client-secret = {client_secret}
spring.security.oauth2.client.registration.sc2-lg-cn.client-id = {client_id}
spring.security.oauth2.client.registration.sc2-lg-cn.client-secret = {client_secret}
com.nephest.battlenet.sc2.aligulac.api.key={api_key}
```

You can use the ```src/main/resources/application-private.properties``` file (ignored by git, used by config) 
for private/local application properties

You can use the [latest DB dump](https://www.nephest.com/sc2/dl/db-dump) to kickstart the deployment. You are free to
use the DB dump for non-commercial purposes if you credit the [reference website](https://www.nephest.com/sc2/). Bear in
mind that some tables may be empty due to privacy policy.

You can also use a [DB init script](src/main/resources/schema-postgres.sql) to have an empty DB if you wish so.

To run the local server execute the following command in a terminal
```
mvn spring-boot:run
```
## Blizzard API clients
The default config expects you to use different API clients/keys for each region. You **must** reduce the 
`BlizzardSC2API.REQUESTS_PER_SECOND_CAP` to 25 if you want to use one API client for all regions. 
## Alternative update
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
## Task configuration
[Cron class](src/main/java/com/nephest/battlenet/sc2/config/Cron.java) contains all scheduled tasks.
## Discord bot
You can add `discord.token = {token}` application property to run the optional discord bot.
## Contributing
Want to make a bug report/feature request? Any contributions are welcome, see [CONTRIBUTING](CONTRIBUTING.md) for 
more information.
## Licenses
* [Main license](LICENSE.txt)
* [3rd party licenses](3rd-party-licenses.txt)
## Trademarks
Battle.net, Blizzard Entertainment and StarCraft are trademarks or registered trademarks of Blizzard Entertainment,
 Inc. in the U.S. and/or other countries. 
