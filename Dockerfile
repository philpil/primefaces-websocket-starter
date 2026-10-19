FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY . .
RUN chmod +x mvnw && ./mvnw -B -ntp clean package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN chown 10001:10001 /app
COPY --from=build --chown=10001:10001 /build/target/websocket-lab.war /app/websocket-lab.war
COPY --from=build --chown=10001:10001 /build/target/payara-micro.jar /app/payara-micro.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/payara-micro.jar", "--deploy", "/app/websocket-lab.war", "--contextroot", "/", "--port", "8080", "--nocluster"]
