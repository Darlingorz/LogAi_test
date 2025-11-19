FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /workspace/app

COPY pom.xml .
COPY libs ./libs

# 正确安装你自己的本地 jar
RUN mvn install:install-file \
    -Dfile=libs/mcp-annotations-0.6.0-SNAPSHOT.jar \
    -DgroupId=org.springaicommunity \
    -DartifactId=mcp-annotations \
    -Dversion=0.6.0-SNAPSHOT \
    -Dpackaging=jar

# 下载依赖
RUN mvn -B dependency:go-offline

# 复制源码
COPY src ./src

# 构建应用
RUN mvn -B -DskipTests clean package \
    && jar_file=$(find target/ -maxdepth 1 -name "*.jar" | grep -v 'original') \
    && mv "${jar_file}" target/app.jar

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=build /workspace/app/target/app.jar ./app.jar

EXPOSE 8080
ENV JAVA_OPTS=""

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
