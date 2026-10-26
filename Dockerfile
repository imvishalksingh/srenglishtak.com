FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -ntp -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"
RUN groupadd --system readiq \
    && (getent group 1000 >/dev/null || groupadd --gid 1000 render-secrets) \
    && useradd --system --gid readiq --groups 1000 readiq
COPY --from=build /app/target/backend-1.0.0.jar /app/backend.jar
USER readiq
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/backend.jar"]
