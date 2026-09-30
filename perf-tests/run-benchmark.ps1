# =====================================================================
# Affiliate Platform 性能基准压测自动化执行脚本 (PowerShell)
# =====================================================================

param (
    [string]$TargetUrl = "http://localhost:8080",
    [string]$OutReport = "perf-tests/benchmark-report.json"
)

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "🚀 开始执行生产级性能压测: $TargetUrl" -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan

# 检查本地是否安装 k6
$k6Installed = Get-Command k6 -ErrorAction SilentlyContinue

if (-not $k6Installed) {
    Write-Warning "未检测到系统安装 k6，建议安装: winget install k6 / choco install k6"
    Write-Host "提示: 您也可以直接运行工程内基于 Java 21 虚拟线程的高并发性能基准测试:" -ForegroundColor Yellow
    Write-Host "  mvn test -pl platform-affiliate -am -Dtest=BenchmarkLoadSimulationTest -Dsurefire.failIfNoSpecifiedTests=false" -ForegroundColor Green
    exit 0
}

$env:TARGET_URL = $TargetUrl

Write-Host "正在启动万级 QPS 压力阶梯测试..." -ForegroundColor Green
k6 run --summary-export $OutReport perf-tests/k6-load-test.js

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✅ 压测成功完成！所有性能指标与 P99 SLA 均满足生产要求。" -ForegroundColor Green
} else {
    Write-Host "`n⚠️ 压测完成，存在部分指标未达阈值或产生异常，请查看报告。" -ForegroundColor Red
}
