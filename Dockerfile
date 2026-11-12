# Matching server image for Render (render.yaml). Builds :core and :server only.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY core core
COPY server server
# The demo users drive these commute scenarios.
COPY tools/scenarios tools/scenarios
ENV DRIVEMATE_SERVER_ONLY=1
RUN ./gradlew --no-daemon :server:installDist

FROM eclipse-temurin:17-jre
COPY --from=build /src/server/build/install/server /app
# Render routes traffic to $PORT on all interfaces.
ENV HOST=0.0.0.0
CMD ["/app/bin/server"]
