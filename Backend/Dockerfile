FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:21-jre-jammy
RUN addgroup --system apex && adduser --system --ingroup apex apex
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN chown -R apex:apex /app
USER apex
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
