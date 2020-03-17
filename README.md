# About
This is the source code of the https://www.nephest.com/sc2 website
## Disclamier
I use this project to learn the java web development process. This project is not production-ready.
## How to run it
You have to inject your bnet API key into the BlizzardSC2API bean
```xml
<bean id="blizzardSC2API" class="com.nephest.battlenet.sc2.web.service.blizzard.BlizzardSC2API">
   <constructor-arg index="0" value="YOUR_API_KEY" />
</bean>
```
## Task configuration
[Cron class](src/main/java/com/nephest/battlenet/sc2/config/Cron.java) contains all scheduled tasks.
