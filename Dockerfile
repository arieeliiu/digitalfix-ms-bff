# Compilar desde un clon limpio, sin depender de target en el host.
FROM eclipse-temurin:21-jdk AS compilacion
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system digitalfix \
    && useradd --system --gid digitalfix digitalfix

COPY --from=compilacion /app/target/*.jar app.jar

EXPOSE 8080

USER digitalfix
ENTRYPOINT ["java", "-jar", "app.jar"]
