# syntax=docker/dockerfile:1

# ---- Estagio de build ----
# Imagem com Maven + JDK so existe aqui; nao vai para a imagem final.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copia so o pom.xml primeiro para cachear as dependencias em uma layer
# separada - so baixa tudo de novo se o pom.xml mudar, nao a cada mudanca de
# codigo.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B -DskipTests package && \
    mv target/invest-renda-api-*.jar target/app.jar

# ---- Estagio de runtime ----
# Alpine + so o JRE (nao o JDK completo) deixa a imagem final bem menor.
FROM eclipse-temurin:21-jre-alpine

# Nao roda como root dentro do container.
RUN addgroup -S investrenda && adduser -S investrenda -G investrenda
USER investrenda

WORKDIR /app
COPY --from=build /build/target/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
