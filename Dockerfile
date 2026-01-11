# Dependency stage
FROM maven:3.9.11-eclipse-temurin-17-noble AS dependency
WORKDIR /usr/local/src/sc2pulse

# Pull and cache maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline
RUN mvn -P prod dependency:go-offline

# Install node, pull and cache node dependencies
COPY package.json package-lock.json .
RUN mvn -P prod com.github.eirslett:frontend-maven-plugin:install-node-and-npm@install-node-npm
RUN mvn -P prod com.github.eirslett:frontend-maven-plugin:npm@npm-install

# Build stage
FROM dependency AS build
COPY src src
COPY babel.config.json minify-script.js version.properties .
RUN mvn -P prod clean package -DskipTests -Drevision=$(cat version.properties | grep revision | cut -d'=' -f2)

# Run stage
FROM eclipse-temurin:17-jre-noble AS run
LABEL org.opencontainers.image.source="https://github.com/sc2-pulse/sc2-pulse"
LABEL org.opencontainers.image.title="SC2 Pulse"
LABEL org.opencontainers.image.description="The fastest and most reliable ranked ladder tracker for StarCraft2"
LABEL org.opencontainers.image.licenses="AGPL-3.0-or-later"
WORKDIR /opt/sc2pulse
ENV JAVA_BASE_OPTIONS="-Dsun.net.client.defaultConnectTimeout=30000 -Dsun.net.client.defaultReadTimeout=30000"
ENV JAVA_TOOL_OPTIONS="${JAVA_BASE_OPTIONS}"
ENV USER_NAME=sc2pulse
RUN useradd --system --user-group ${USER_NAME}
COPY --from=build --chown=${USER_NAME}:${USER_NAME} /usr/local/src/sc2pulse/target/sc2-webapp.jar /opt/sc2pulse/sc2pulse.jar
COPY containers/entrypoint.sh /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
CMD ["java", "-jar", "/opt/sc2pulse/sc2pulse.jar"]
