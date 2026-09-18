/**
 * 网盟后台统一 API 请求客户端封装
 * 自动注入当前租户空间与后端地址，统一解析后端错误体并向外抛出可展示的错误信息。
 * 调用失败时始终抛出异常，由页面决定提示与空态展示（不存在演示数据兜底）。
 */
let sharedTenantRef: Ref<string> | null = null

export function useApi() {
  const config = useRuntimeConfig()
  if (!sharedTenantRef) {
    // 模块级单例：布局中切换租户后，所有页面的请求头同步生效
    sharedTenantRef = ref(config.public.tenantId || 'tenant-1')
  }
  const currentTenant = sharedTenantRef

  const fetchApi = async <T>(url: string, options: Parameters<typeof $fetch>[1] = {}): Promise<T> => {
    const { token, clearAuth } = useAuth()
    const headers = {
      'X-Tenant-ID': currentTenant.value,
      'Content-Type': 'application/json',
      ...(token.value ? { Authorization: `Bearer ${token.value}` } : {}),
      ...((options.headers as Record<string, string>) || {})
    }

    try {
      return await $fetch<T>(url, {
        baseURL: config.public.apiBase || undefined,
        retry: 0,
        ...options,
        headers
      })
    } catch (err: any) {
      const status = err?.statusCode || err?.status
      if (status === 401 && !url.startsWith('/api/v1/auth/')) {
        // 令牌失效：清理本地登录态并跳转登录页（保留当前路径供登录后回跳）
        clearAuth()
        if (import.meta.client && window.location.pathname !== '/login') {
          const redirect = encodeURIComponent(window.location.pathname + window.location.search)
          window.location.href = `/login?redirect=${redirect}`
        }
      }
      const detail = err?.data?.message || err?.data?.error || err?.message || '服务请求失败'
      const error = new Error(status ? `[${status}] ${detail}` : String(detail)) as Error & { status?: number }
      error.status = status
      console.error(`API Error [${url}]:`, err)
      throw error
    }
  }

  return {
    fetchApi,
    currentTenant
  }
}
