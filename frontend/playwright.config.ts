import { defineConfig, devices } from '@playwright/test'

/* E2E 冒烟配置。
   注意：默认只启动前端 Vite 开发服务器（http://127.0.0.1:5173），
   不会自动启动后端（http://127.0.0.1:8080）与 MySQL。
   当前冒烟用例通过 page.route mock 登录接口，无需后端即可运行；
   若要执行完整业务流程，请先手动启动后端并准备数据库。 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: 'list',
  timeout: 30_000,
  use: {
    baseURL: 'http://127.0.0.1:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure'
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000
  }
})
