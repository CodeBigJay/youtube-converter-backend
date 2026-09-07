# ---- Build stage: compile the jar with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Runtime stage: slim JRE + ffmpeg + yt-dlp ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# ffmpeg for audio conversion, python3 to run yt-dlp, curl to fetch the yt-dlp binary
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg python3 curl ca-certificates \
    && curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp \
    && apt-get purge -y curl \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/mediaconverter-0.0.1-SNAPSHOT.jar app.jar

# Railway/Render inject $PORT at runtime; application.properties reads it via ${PORT:8080}
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
