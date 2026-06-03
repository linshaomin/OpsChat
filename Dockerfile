# 多阶段构建
# 第一阶段：构建
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

COPY pom.xml .
RUN sed -i 's|https://repo.maven.apache.org/maven2|https://maven.aliyun.com/repository/public|g' /usr/share/maven/conf/settings.xml || true

COPY src ./src
RUN mvn clean package -DskipTests

# 第二阶段：运行
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "-Xms512m", "-Xmx1g", "app.jar"]