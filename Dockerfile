# ---------- build stage ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar /app.jar

# ---------- run stage ----------
FROM eclipse-temurin:17-jre
RUN useradd --system --create-home appuser
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER appuser
EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx768m -XX:+UseSerialGC"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
