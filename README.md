# About
This is the source code of the https://www.nephest.com/sc2 website
## Disclamier
I use this project to learn the java web development process. This project is not production-ready.
## Required application properties
```
blizzard.api.key = base64 encoded client_id:client_secret
```
## Dependencies
* Java 11
* PostgreSQL

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

## Task configuration
[Cron class](src/main/java/com/nephest/battlenet/sc2/config/Cron.java) contains all scheduled tasks.
