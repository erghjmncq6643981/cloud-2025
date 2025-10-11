# 多阶段构建：减小镜像体积 [7,8](@ref)
FROM maven:3.8.6-openjdk-11 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline    # 缓存依赖加速构建
COPY src ./src
RUN mvn package -DskipTests      # 打包应用

# 运行阶段
FROM openjdk:21-jre-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 18061
ENTRYPOINT ["java", "-jar", "app.jar"]