import { useLoginUserStore } from '@/stores/loginUser'
import { message } from 'ant-design-vue'
import router from '@/router'

// 是否为首次获取登录用户
let firstFetchLoginUser = true

/**
 * 全局权限校验
 */
router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser
  const toUrl = to.fullPath
  const isAdminPage = toUrl.startsWith('/admin')

  // 首页、登录、注册等公开页面不等待后端登录态接口，避免后端异常时整页空白
  if (firstFetchLoginUser && !isAdminPage) {
    firstFetchLoginUser = false
    loginUserStore.fetchLoginUser().catch((error) => {
      console.error('获取登录用户失败：', error)
    })
  }

  // 后台页面需要先确认登录态和权限
  if (firstFetchLoginUser && isAdminPage) {
    try {
      await loginUserStore.fetchLoginUser()
      loginUser = loginUserStore.loginUser
    } catch (error) {
      console.error('获取登录用户失败：', error)
    }
    firstFetchLoginUser = false
  }
  if (isAdminPage) {
    if (!loginUser || loginUser.userRole !== 'admin') {
      message.error('没有权限')
      next(`/user/login?redirect=${to.fullPath}`)
      return
    }
  }
  next()
})
