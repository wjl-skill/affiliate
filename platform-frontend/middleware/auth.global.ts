/**
 * 全局路由守卫：未登录用户强制跳转登录页，已登录用户访问 /login 时回跳主控制台
 */
export default defineNuxtRouteMiddleware((to) => {
  const { isLoggedIn } = useAuth()

  if (to.path === '/login') {
    if (isLoggedIn.value) {
      return navigateTo('/')
    }
    return
  }

  if (!isLoggedIn.value) {
    return navigateTo(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
  }
})
