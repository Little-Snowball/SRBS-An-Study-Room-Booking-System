# scripts 目录
# 只提供明确执行的构建/启动命令；不含任何自动修复系统服务的操作。

# build-frontend.ps1 —— 安装锁定依赖并构建前端（要求 Node 22.22.2，node/npm 需在 PATH）
$ErrorActionPreference = "Stop"
Push-Location "$PSScriptRoot\..\frontend"
& npm ci
& npm run build
Pop-Location
