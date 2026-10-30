# syntax=docker/dockerfile:1

# =========================================================
# ETAPA 1: BUILD DA APLICAÇÃO
# =========================================================
FROM maven:3.9-eclipse-temurin-25-alpine AS build

WORKDIR /workspace

# Copia os arquivos necessários para resolver dependências.
# Como o código-fonte ainda não foi copiado, alterações em
# classes Java não invalidam esta camada.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Garante a execução do Maven Wrapper.
RUN chmod +x mvnw

# Pré-carrega as dependências utilizando o cache do BuildKit.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -DskipTests dependency:go-offline

# Copia o código-fonte somente depois das dependências.
COPY src/ src/

# Compila a aplicação e gera o JAR executável.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -DskipTests package \
    && cp target/*.jar /workspace/app.jar


# =========================================================
# ETAPA 2: IMAGEM FINAL DE EXECUÇÃO
# =========================================================
FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

# Cria um usuário sem privilégios administrativos.
RUN addgroup -S spring \
    && adduser -S spring -G spring

# Copia somente o artefato gerado durante o build.
COPY --from=build --chown=spring:spring \
    /workspace/app.jar /app/app.jar

USER spring

# Porta interna padrão da aplicação.
EXPOSE 8080

# Executa a aplicação Spring Boot.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
