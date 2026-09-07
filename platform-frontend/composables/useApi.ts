/**
 * 网盟后台统一 API 请求客户端封装
 * 自动注入当前租户空间、统一错误响应拦截与数据映射
 */
export function useApi() {
  const defaultTenant = ref('tenant-1')

  const fetchApi = async <T>(url: string, options: Parameters<typeof $fetch>[1] = {}): Promise<T> => {
    const headers = {
      'X-Tenant-ID': defaultTenant.value,
      'Content-Type': 'application/json',
      ...(options.headers || {})
    }

    try {
      return await $fetch<T>(url, {
        ...options,
        headers
      })
    } catch (err: any) {
      console.error(`API Error [${url}]:`, err)
      throw err
    }
  }

  return {
    fetchApi,
    currentTenant: defaultTenant
  }
}
