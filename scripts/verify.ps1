param(
    [switch]$SkipSmoke,
    [string]$BaseUrl = "http://127.0.0.1:8080"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$NodeDirectory = "C:\Program Files\nodejs"
$JavaDirectory = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"

if (-not (Test-Path "$NodeDirectory\node.exe")) { throw "未找到当前工程需要的 Node.js 24" }
if (-not (Test-Path "$JavaDirectory\bin\java.exe")) { throw "未找到 Java 21" }

$env:Path = "$NodeDirectory;$env:Path"
$env:JAVA_HOME = $JavaDirectory
$env:Path = "$JavaDirectory\bin;$env:Path"

Push-Location $ProjectRoot
try {
    Write-Host "[1/6] 前端静态检查"
    pnpm lint

    Write-Host "[2/6] 共享模型与学生端生产构建"
    pnpm build:shared
    pnpm --filter @study-time/student-pwa build

    Write-Host "[3/6] 家长端 H5 与微信小程序构建"
    pnpm --filter @study-time/parent-miniapp build:h5
    pnpm --filter @study-time/parent-miniapp build:weapp

    Write-Host "[4/6] 演示站生产构建"
    pnpm build

    Write-Host "[5/6] Java 后端自动化测试"
    Push-Location "$ProjectRoot\server"
    try { .\mvnw.cmd test } finally { Pop-Location }

    if ($SkipSmoke) {
        Write-Host "[6/6] 已按参数跳过运行态冒烟测试"
    } else {
        Write-Host "[6/6] 运行态业务与通知冒烟测试"
        & "$ProjectRoot\scripts\smoke.ps1" -BaseUrl $BaseUrl
    }
    Write-Host "阶段 7 质量门禁通过。" -ForegroundColor Green
} finally {
    Pop-Location
}
