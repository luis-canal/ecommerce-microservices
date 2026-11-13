FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN --mount=type=cache,id=cart-api-maven,target=/root/.m2,sharing=locked ./mvnw -B -DskipTests dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,id=cart-api-maven,target=/root/.m2,sharing=locked ./mvnw -B -DskipTests package \
    && cp target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app
RUN addgroup -S spring \
    && adduser -S spring -G spring
COPY --from=build --chown=spring:spring /workspace/app.jar /app/app.jar

USER spring
EXPOSE 8600
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
