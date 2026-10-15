import { expect, test } from '@playwright/test'
import { expectLoginFormVisible, mockLoginFailure } from './fixtures'

/* 冒烟测试：仅覆盖登录页结构、统一错误提示与未登录路由守卫。
   完整业务流程（选座/考勤/申诉等）依赖后端与 MySQL，不在此文件中断言。 */

test.describe('登录页', () => {
  test('打开 /login 展示标题与表单元素', async ({ page }) => {
    await page.goto('/login')

    await expect(page).toHaveTitle(/自习室预约系统/)
    await expectLoginFormVisible(page)
  })

  test('学号或密码错误时展示统一提示且停留在登录页', async ({ page }) => {
    await mockLoginFailure(page)
    await page.goto('/login')

    await page.getByPlaceholder('请输入学号').fill('20230001')
    await page.getByPlaceholder('请输入密码').fill('wrong-password')
    await page.getByRole('button', { name: '登 录' }).click()

    /* http 拦截器统一弹出后端 message；账号错误与密码错误提示一致 */
    await expect(page.getByText('学号或密码错误')).toBeVisible()
    await expect(page).toHaveURL(/\/login$/)
    /* 登录失败后密码被清空，学号保留，按钮恢复可点 */
    await expect(page.getByPlaceholder('请输入密码')).toHaveValue('')
    await expect(page.getByPlaceholder('请输入学号')).toHaveValue('20230001')
    await expect(page.getByRole('button', { name: '登 录' })).toBeEnabled()
  })

  test('未登录访问 /student/home 被重定向到 /login 并携带 redirect', async ({ page }) => {
    await page.goto('/student/home')

    await page.waitForURL('**/login*')
    await expect(page).toHaveURL(/\/login\?redirect=%2Fstudent%2Fhome/)
    await expectLoginFormVisible(page)
  })

  test('未登录访问 /student/reservations 同样被守卫拦截', async ({ page }) => {
    await page.goto('/student/reservations')

    await page.waitForURL('**/login*')
    await expect(page).toHaveURL(/\/login\?redirect=%2Fstudent%2Freservations/)
  })
})
