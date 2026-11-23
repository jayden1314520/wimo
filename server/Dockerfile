# 前置：先在 server/ 目录执行 `mvn -DskipTests package` 生成 ruoyi-admin/target/ruoyi-admin.jar
# 采用「复制已构建 jar」而非容器内 Maven 构建，启动快、离线可复现。
FROM eclipse-temurin:17-jre

WORKDIR /app
COPY ruoyi-admin/target/ruoyi-admin.jar app.jar
RUN mkdir -p /app/uploadPath /app/samples

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar", \
            "--ruoyi.profile=/app/uploadPath", \
            "--wimo.mock.sample-dir=/app/samples"]
