import { expect, type Page } from '@playwright/test'

/* 冒烟骨架：不依赖真实后端与数据库。
   所有需要后端的接口均通过 page.route 以“后端风格错误信封” mock，
   断言重点为页面结构、统一错误提示与路由守卫行为。 */

/** mock 登录接口返回 422 + 后端统一信封（学号或密码错误） */
export async function mockLoginFailure(page: Page, message = '学号或密码错误'): Promise<void> {
  await page.route('**/api/v1/auth/login', async (route) => {
    await route.fulfill({
      status: 422,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 'INVALID_CREDENTIALS',
        message,
        data: null,
        traceId: 'e2e-smoke',
        serverTime: new Date().toISOString()
      })
    })
  })
}

/** 断言登录页关键结构：标题、品牌标题、学号/密码输入框与登录按钮 */
export async function expectLoginFormVisible(page: Page): Promise<void> {
  await expect(page.getByRole('heading', { name: '高校自习室预约与考勤管理系统' })).toBeVisible()
  await expect(page.getByPlaceholder('请输入学号')).toBeVisible()
  await expect(page.getByPlaceholder('请输入密码')).toBeVisible()
  const submit = page.getByRole('button', { name: '登 录' })
  await expect(submit).toBeVisible()
  await expect(submit).toBeEnabled()
}
