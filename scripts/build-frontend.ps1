# scripts 目录
# 只提供明确执行的构建/启动命令；不含任何自动修复系统服务的操作。

# build-frontend.ps1 —— 安装锁定依赖并构建前端（要求 Node 22.22.2）
$ErrorActionPreference = "Stop"
$node = "C:\Users\Little_Snowball\.workbuddy\binaries\node\versions\22.22.2-6\node.exe"
$npm = "C:\Users\Little_Snowball\.workbuddy\binaries\node\versions\22.22.2-6\node_modules\npm\bin\npm-cli.js"
Push-Location "$PSScriptRoot\..\frontend"
& $node $npm ci
& $node $npm run build
Pop-Location
