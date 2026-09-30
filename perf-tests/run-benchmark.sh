#!/usr/bin/env bash
# =====================================================================
# Affiliate Platform 性能基准压测自动化执行脚本 (Linux/macOS)
# =====================================================================
set -euo pipefail

TARGET_URL="${1:-http://localhost:8080}"
REPORT_FILE="${2:-perf-tests/benchmark-report.json}"

echo "======================================================"
echo "🚀 开始执行生产级性能压测: ${TARGET_URL}"
echo "======================================================"

if ! command -v k6 &> /dev/null; then
    echo "⚠️ 未检测到系统安装 k6。"
    echo "提示: 可通过工程原生测试套件执行压力测试:"
    echo "  mvn test -pl platform-affiliate -am -Dtest=BenchmarkLoadSimulationTest -Dsurefire.failIfNoSpecifiedTests=false"
    exit 0
fi

export TARGET_URL="${TARGET_URL}"

echo "正在启动万级 QPS 压力阶梯测试..."
k6 run --summary-export "${REPORT_FILE}" perf-tests/k6-load-test.js

echo "✅ 压测执行完成，报告已保存至: ${REPORT_FILE}"
