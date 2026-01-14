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
* Docker/Podman
* Java 17+
* Maven 3
* BattleNet API access keys(you must use your own keys)
* Twitch API keys(you must use your own keys)
* Aligulac API key(you must use your own key)

## Application properties
You can provide application properties via the following methods:
* Env variables. App property names should be converted to uppercase, dots replaced with underscores, 
and hyphens should be removed. For example, the `x.y.z-z` app property name should be converted to `X_Y_ZZ` env var name.
* Secret files. Add a `SPRING_CONFIG_IMPORT=optional:configtree:/path/to/secrets/` env variable. In the supplied directory,
create files with app property names, their content will be used as values.
* Properties. Add a `SPRING_CONFIG_IMPORT=optional:file:/dir/file.properties` env variable. Use the supplied file as 
a regular properties file (name-value map).

## Podman(rootless)
The container config is compatible with rootless podman.
Requirements:
* [General Container runtime requirements](https://java.testcontainers.org/supported_docker_environment/)
* **Active** podman socket `podman system service --time=0`. Make it a systemd user service, a login script, whatever.
Systemd socket activation (`systemctl --user enable podman.socket`) doesn't work. 
* Spring test app property `org.testcontainers.host=host.containers.internal`
* uid/gid mapping(`usermod --add-subuids from-to username`, `usermod --add-subgids from-to username`)
* `loginctl enable-linger username`

## Testing
Run the tests to ensure that you have a valid environment set up. You must also pass the tests
before creating a PR.

Required properties:
```
spring.security.oauth2.client.blizzard.client-id={client_id}
spring.security.oauth2.client.blizzard.client-secret={client_secret}
com.nephest.battlenet.sc2.discord.bot.token={token}
spring.security.oauth2.client.discord.client-id={client_id}
spring.security.oauth2.client.discord.client-secret={client_secret}
spring.security.oauth2.client.twitch.client-id={client_id}
spring.security.oauth2.client.twitch.client-secret={client_secret}
com.nephest.battlenet.sc2.api.aligulac.key={api_key}
```

To run all the tests execute the following command in a terminal
```
mvn verify
```

## Development
Execute `mvn spring-boot:test-run` command to launch the project in dev mode.
* Default ephemeral tempfs storage with predefined data.
* Optional persistent storage. Create docker volume `docker volume create volume-name` and add the following app 
property `org.testcontainers.dev.volume.name=volume-name`
* By default, the HTTP server uses a random free port which is displayed in the log. You can use the
`org.testcontainers.dev.http.server.port` app property to pin the port if needed.

### Optional application properties 
The server will run without errors if these properties are missing, but the corresponding features will be disabled.
```
com.nephest.battlenet.sc2.api.replaystats.key
```

### Misc
Use [DB init script](src/main/resources/schema-postgres.sql) to create a fresh DB.

To run the local server execute the following command in a terminal
```
mvn spring-boot:run
```

Scheduled tasks are disabled in the dev mode. You can remove the `@Profile` annotation from the Cron class if you want 
to run the tasks(like ladder scans) in the dev mode.

## Production
### Docker compose
`docker compose up`

#### Restart
There is no restart config. You are encouraged to create a systemd service to start/stop/restart it, use
the `--abort-on-container-failure` flag in compose up script.

#### Config
You can merge additional compose configs via config merge. The directory structure must be preserved. The project
root must be the working directory. For example 
`docker compose -f "compose.yaml" -f "containers/traefik/dispatcher/http/compose.yaml" up`

#### Exposure
Nothing is exposed by default. Sc2pulse service port is 8080 by default, expose it directly or add a reverse proxy.

See [containers/traefik/dispatcher](containers/traefik/dispatcher).

#### Secrets
You can provide required secrets via env variables or compose secrets(file mounts). The sc2pulse service accepts secrets
in the `/run/secrets` directory, and copies them into service readable secrets in the `/run/sc2pulse` directory.

You can use [containers/secrets/infisical](containers/secrets/infisical) to inject secrets via tmpfs.
* Set up [shm-sc2pulse-dir](containers/secrets/infisical/shm-sc2pulse-dir.service) systemd service. This is needed
because docker is bad at sharing tmpfs. If you are using podman, then you can modify the infisical compose config and
share named tmpfs volumes directly. 
* Register on https://infisical.com/, create a secret management project, add secrets, add machine identity access.
* Mount `/etc/sc2pulse/id` and `/etc/sc2pulse/secret` files into the infisical service with machine identity universal 
access id and secret content.
* Replace `"PROJECT_ID"` with your project id in [template.tmpl](containers/secrets/infisical/template.tmpl). You can
also replace `prod` with `dev` or `stage`, depending on what secrets you want to pull.
* Replace `address` in [agent-config.yaml](containers/secrets/infisical/agent-config.yaml) with your infisical address 
if you are using other infisical instance(EU(`https://eu.infisical.com`) or self-hosted).

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
* a very low request rate is used
* the data is considered as if it came from the regular dev API and the relevant ToS and Privacy Policy are applied.

**Use it at your own risk.**

## Task configuration
[Cron class](src/main/java/com/nephest/battlenet/sc2/config/Cron.java) contains all scheduled tasks.
## Common application properties
* `com.nephest.battlenet.sc2.cron.enabled` controls [Cron](src/main/java/com/nephest/battlenet/sc2/config/Cron.java)
* `com.nephest.battlenet.sc2.url.public` public URL of your service
* `com.nephest.battlenet.sc2.mmr.history.main.length` 1v1 mmr history length in days, 180 by default.
* `com.nephest.battlenet.sc2.mmr.history.secondary.length` team mmr history length in days, 180 by default.
* `com.nephest.battlenet.sc2.ladder.regions` set of regions that will be updated, currently active regions by default.
* `com.nephest.battlenet.sc2.db-dump-file` path to the database dump.
* `com.nephest.battlenet.sc2.api.request.limit.separate` Activates legacy mode where each region uses a separate request
limiter. False by default.
* `com.nephest.battlenet.sc2.privacy.character.profile.update` update characters directly from their bnet profiles. True
by default.
* `com.nephest.battlenet.sc2.contacts.email`
* `com.nephest.battlenet.sc2.contacts.discord.server`
* `com.nephest.battlenet.sc2.contacts.discord.server.name`
* `com.nephest.battlenet.sc2.contacts.twitter`
* `com.nephest.battlenet.sc2.contacts.github`
* `com.nephest.battlenet.sc2.donate` HTML text for the "donate" page
* `security.remember-me.token.key` key for signing remember-me tokens
* `security.remember-me.token.max-age` Max age(duration) of the cookie, P3650D(~10 years) by default
## Contributing
Want to make a bug report/feature request? Any contributions are welcome, see [CONTRIBUTING](CONTRIBUTING.md) for 
more information.
## Licenses
* [Main license](LICENSE.txt)
* [3rd party licenses](3rd-party-licenses.txt)
## Blizzard ToS
SC2 Pulse is fully compliant with the Blizzard ToS.
* Requests per hour cap is guaranteed if your clock desync is within 5 seconds.
* Requests per second cap is guaranteed.
* BattleTags, player names, and matches are removed after 30 days from the moment they were deleted from the API
## Trademarks
Battle.net, Blizzard Entertainment and StarCraft are trademarks or registered trademarks of Blizzard Entertainment,
 Inc. in the U.S. and/or other countries. 
