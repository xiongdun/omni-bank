# 尽调 Agent 服务镜像：需先在仓库根执行 mvn 构建，再以 boot 模块为上下文构建镜像
# 构建示例（仓库根目录）：
#   mvn -ntp -pl omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot -am package -DskipTests
#   docker build -f omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot/Dockerfile \
#     -t omni-bank-agent:1.0.0 omni-bank-service/omni-bank-service-agent/omni-bank-service-agent-boot
FROM eclipse-temurin:21-jre

LABEL maintainer="omni-bank" \
      description="omni-bank 对公信贷智能尽调 Agent 服务（HTTP 8109 / Dubbo triple 20889）"

WORKDIR /app

# 健康检查依赖 curl（基础镜像未内置）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 时区与字符集：与审计字段、报表时间口径保持一致
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

COPY target/omni-bank-service-agent-boot-*.jar /app/app.jar

EXPOSE 8109 20889

# 探活统一走 /actuator/health（与网关、K8s 探针一致）
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["/bin/sh", "-c", "curl -fsS http://127.0.0.1:8109/actuator/health || exit 1"]

ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
