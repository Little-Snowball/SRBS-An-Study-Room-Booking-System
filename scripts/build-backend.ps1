# scripts 目录
# 只提供明确执行的构建/启动命令；不含任何自动修复系统服务的操作。
# 所有命令在仓库根目录或指定子目录执行；路径含中文时加引号。

# build-backend.ps1 —— 编译并运行后端单测
$ErrorActionPreference = "Stop"
$mvn = "D:\Maven\apache-maven-3.9.16\bin\mvn.cmd"
Push-Location "$PSScriptRoot\..\backend"
& $mvn clean verify
Pop-Location
