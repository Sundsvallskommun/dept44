FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd -u 10001 -r -s /usr/sbin/nologin appuser

COPY target/*.jar /app/app.jar

RUN chown -R appuser:appuser /app
USER appuser

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=25 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
