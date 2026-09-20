# ===================================================================
# Stage 1: Build application with Maven
# ===================================================================
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# 预先拷贝根 POM 与子模块 POM，充分利用 Docker 缓存层
COPY pom.xml ./
COPY platform-common/pom.xml platform-common/
COPY platform-infrastructure/pom.xml platform-infrastructure/
COPY platform-auth/pom.xml platform-auth/
COPY platform-tenant/pom.xml platform-tenant/
COPY platform-event/pom.xml platform-event/
COPY platform-budget/pom.xml platform-budget/
COPY platform-creative/pom.xml platform-creative/
COPY platform-ssp/pom.xml platform-ssp/
COPY platform-dsp/pom.xml platform-dsp/
COPY platform-dmp/pom.xml platform-dmp/
COPY platform-cdp/pom.xml platform-cdp/
COPY platform-adx/pom.xml platform-adx/
COPY platform-google-ads/pom.xml platform-google-ads/
COPY platform-google-gam/pom.xml platform-google-gam/
COPY platform-billing/pom.xml platform-billing/
COPY platform-reporting/pom.xml platform-reporting/
COPY platform-affiliate/pom.xml platform-affiliate/
COPY platform-api/pom.xml platform-api/

# 拷贝各子模块源码
COPY platform-common/src platform-common/src
COPY platform-infrastructure/src platform-infrastructure/src
COPY platform-auth/src platform-auth/src
COPY platform-tenant/src platform-tenant/src
COPY platform-event/src platform-event/src
COPY platform-budget/src platform-budget/src
COPY platform-creative/src platform-creative/src
COPY platform-ssp/src platform-ssp/src
COPY platform-dsp/src platform-dsp/src
COPY platform-dmp/src platform-dmp/src
COPY platform-cdp/src platform-cdp/src
COPY platform-adx/src platform-adx/src
COPY platform-google-ads/src platform-google-ads/src
COPY platform-google-gam/src platform-google-gam/src
COPY platform-billing/src platform-billing/src
COPY platform-reporting/src platform-reporting/src
COPY platform-affiliate/src platform-affiliate/src
COPY platform-api/src platform-api/src

# 执行独立构建打包
RUN mvn clean package -pl platform-api -am -DskipTests

# ===================================================================
# Stage 2: Minimal & Secure Production Runtime
# ===================================================================
FROM eclipse-temurin:21-jre-alpine

# 安装 curl 工具并创建非 root 用户运行
RUN apk add --no-cache curl && \
    addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# 从构建阶段提取最终 jar 包
COPY --from=builder /workspace/platform-api/target/platform-api-*.jar /app/app.jar

# 赋予用户权限并切换
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

# 生产 JVM 参数与容器自适应内存配置
ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# 健康检查探针
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
