import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

/* 独立的 vitest 配置：复用 vite.config.ts 的插件与 @ 别名，
   仅把测试范围限制在 tests/ 目录，避免 vitest 误抓 e2e/ 下的 Playwright 用例。 */
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      include: ['tests/**/*.test.ts'],
      environment: 'node'
    }
  })
)
