FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY settings.xml ./settings.xml
COPY pom.xml ./
RUN mvn -q -e -s ./settings.xml -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -s ./settings.xml -DskipTests package

FROM mcr.microsoft.com/playwright/java:v1.55.0-jammy AS run

WORKDIR /app

ARG JAR=target/*.jar
COPY --from=build /app/${JAR} app.jar
RUN java -Djarmode=layertools -jar app.jar extract && rm -f /app/app.jar

RUN useradd -r -u 10001 spring
RUN mkdir -p images && chown -R 10001:10001 images
RUN mkdir -p /ms-playwright && chown -R 10001:10001 /ms-playwright
RUN mkdir -p /home/spring && chown -R 10001:10001 /home/spring

USER 10001

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
ENV XHS_COOKIES_PATH=/ms-playwright/.xhs/cookies.json

ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError -XX:+UseG1GC -XX:MaxRAMPercentage=75"
ENV TZ=Asia/Shanghai
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

VOLUME ["/ms-playwright"]

ENTRYPOINT ["java","-cp","/app/spring-boot-loader","-Dloader.path=/app/application/BOOT-INF/classes,/app/dependencies/BOOT-INF/lib,/app/snapshot-dependencies","-Dloader.main=com.lv.McpApplication","org.springframework.boot.loader.launch.PropertiesLauncher"]


