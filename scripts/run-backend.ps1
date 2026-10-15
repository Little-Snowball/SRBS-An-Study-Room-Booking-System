# scripts 目录
# 只提供明确执行的构建/启动命令；不含任何自动修复系统服务的操作。
#
# run-backend.ps1 —— 会话级环境变量 + 启动后端（local profile）
# 密码不写进脚本：DB_PASSWORD 由操作者交互输入；JWT 密钥每次随机生成（会话级）。
# 正式部署改用受控环境注入并稳定保存同一密钥，否则旧会话全部失效。

$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "D:\JDK_21"
$env:DB_USER = "studyroom_app"
$secure = Read-Host "请输入数据库运行账号密码" -AsSecureString
$env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
$env:UPLOAD_DIR = "D:\studyroom-data\uploads"
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String(
    [Security.Cryptography.RandomNumberGenerator]::GetBytes(32))

Push-Location "$PSScriptRoot\..\backend"
& "$env:JAVA_HOME\bin\java.exe" -jar "target\studyroom-1.0.0.jar" --spring.profiles.active=local
Pop-Location
