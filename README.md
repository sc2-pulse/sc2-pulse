# About
SC2 ladder generator is fast and flexible StarCraft&reg;2 stats aggregator. It is a Spring Boot web application.
The [reference website](https://www.nephest.com/sc2/) (runs the latest release) is available 24/7.
## Features
* Flexible, user defined filters
* All seasons, races, team formats
* Player profile
    * Career summary
    * History: all teams and seasons
* BattleNet integration
    * Log in with a BattleNet account
    * Easy access to personal stats
    * Personal ladder out of your favourite players
* Season meta info
    * Race, team, player distribution with multiple filters
    * Tier MMR ranges
## Disclamier
I use this project for learning, it is not production-ready.

This application uses Battle.net&reg; API. 
This is not an official Blizzard Entertainment&reg; application.
## Dependencies
* Java 11
* PostgreSQL 11
* Maven 3

## Building
A real PostgreSQL database is required for some integration tests.
This should be only a testing db, as **tests will drop/create** the schema.

You can use the ```src/test/resources/application-private.properties``` file (ignored by git, used by a test config) 
to create a simple test config: 

```
spring.datasource.username=name
spring.datasource.password=pasword
spring.datasource.url=jdbc:postgresql://localhost:5432/db_name
spring.security.oauth2.client.registration.sc2-sys.client-id={client_id}
spring.security.oauth2.client.registration.sc2-sys.client-secret={client_secret}
```

By default, maven will build a spring boot war archive. You can use it with any 
relevant web container.

## Running
You must set the following application properties:
```
spring.security.oauth2.client.registration.sc2-sys.client-id={client_id}
spring.security.oauth2.client.registration.sc2-sys.client-secret={client_secret}
spring.security.oauth2.client.registration.sc2-lg-eu.client-id = {client_id}
spring.security.oauth2.client.registration.sc2-lg-eu.client-secret = {client_secret}
spring.security.oauth2.client.registration.sc2-lg-us.client-id = {client_id}
spring.security.oauth2.client.registration.sc2-lg-us.client-secret = {client_secret}
spring.security.oauth2.client.registration.sc2-lg-kr.client-id = {client_id}
spring.security.oauth2.client.registration.sc2-lg-kr.client-secret = {client_secret}
spring.security.oauth2.client.registration.sc2-lg-cn.client-id = {client_id}
spring.security.oauth2.client.registration.sc2-lg-cn.client-secret = {client_secret}
```

By default, DataSource must be configured in a web container:
```
spring.datasource.jndi-name=java:comp/env/jdbc/dsname
```
You can use the ```src/main/resources/application-private.properties``` file (ignored by git, used by config) 
for private/local application properties
## Task configuration
[Cron class](src/main/java/com/nephest/battlenet/sc2/config/Cron.java) contains all scheduled tasks.
## Contributing
Want to make a bug report/feature request? Any contributions are welcome, see [CONTRIBUTING](CONTRIBUTING.md) for 
more information. 
## Trademarks
Battle.net, Blizzard Entertainment and StarCraft are trademarks or registered trademarks of Blizzard Entertainment,
 Inc. in the U.S. and/or other countries. 
