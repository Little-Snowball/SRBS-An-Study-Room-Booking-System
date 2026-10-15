# scripts 目录使用说明

| 脚本 | 用途 | 前置条件 |
|:---|:---|:---|
| `build-backend.ps1` | `mvn clean verify`（编译 + 单测；集成测试需 studyroom_test） | JDK 21、Maven 3.9.16 |
| `build-frontend.ps1` | `npm ci` + `npm run build`（typecheck + vite build） | Node 22.22.2 |
| `run-backend.ps1` | 会话级注入环境变量并 `java -jar` 启动（local profile） | 已构建 jar；数据库账号有权限 |

约定：

- 环境变量仅当前 PowerShell 会话有效，关闭终端即失效，不修改系统全局变量，不提交 Git。
- 数据库密码只说明输入位置，绝不写入脚本或仓库；正式部署通过受控环境注入。
- `UPLOAD_DIR` 指向本机私有目录，确认目标目录并限制 ACL，不放 `frontend/public` 或 Web 根。
- 脚本不包含停止/删除/修复系统服务的操作；MySQL 双服务归属由机器所有者检查。
