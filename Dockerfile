FROM bellsoft/liberica-openjdk-debian:25 AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew bootJar -x test --no-daemon

FROM bellsoft/liberica-openjre-debian:25
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
VOLUME /app/data
EXPOSE 8080
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
