FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -ntp -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system readiq && useradd --system --gid readiq readiq
COPY --from=build /app/target/backend-1.0.0.jar /app/backend.jar
USER readiq
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/backend.jar"]
