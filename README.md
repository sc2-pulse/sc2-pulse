# About
SC2 ladder generator is what its name suggests - a SC2 ladder generator.
## Features
* Flexible, user defined filters
* All seasons, races, team formats
* Player profile/history
* Season meta info
## Disclamier
I use this project for learning, it is not production-ready.
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
blizzard.api.key=123
```

By default, maven will build a spring boot war archive. You can use it with any 
relevant web container.

## Running
You must set the following application properties:
```
blizzard.api.key = base64 encoded client_id:client_secret
```

By default, DataSource must be configured in a web container:
```
spring.datasource.jndi-name=java:comp/env/jdbc/dsname
```
You can use the ```src/main/resources/application-private.properties``` file (ignored by git, used by config) 
for private/local application properties
## Task configuration
[Cron class](src/main/java/com/nephest/battlenet/sc2/config/Cron.java) contains all scheduled tasks.
