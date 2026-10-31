FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -ntp -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError"
# Group 1000 retains access to existing Render-mounted secrets during the cutover.
RUN groupadd --gid 10001 readiq \
    && (getent group 1000 >/dev/null || groupadd --gid 1000 platform-secrets) \
    && useradd --uid 10001 --gid readiq --groups 1000 --no-create-home readiq
COPY --from=build --chown=readiq:readiq /app/target/*.jar /app/backend.jar
USER readiq
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/backend.jar"]
