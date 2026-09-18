/**
 * 管理后台 JWT 登录状态管理
 * 模块级单例持有 accessToken 与用户档案，并持久化到 localStorage；
 * 登录请求使用原生 $fetch 直连 /api/v1/auth/login，避免与 useApi 形成循环依赖。
 */
export interface AuthUser {
  id: string
  username: string
  displayName?: string
  tenantId?: string
  avatar?: string
  roles?: string[]
  status?: string
}

const TOKEN_KEY = 'aff_token'
const USER_KEY = 'aff_user'

let sharedToken: Ref<string> | null = null
let sharedUser: Ref<AuthUser | null> | null = null

export function useAuth() {
  const config = useRuntimeConfig()
  if (!sharedToken) {
    sharedToken = ref('')
    sharedUser = ref<AuthUser | null>(null)
    try {
      sharedToken.value = localStorage.getItem(TOKEN_KEY) || ''
      const raw = localStorage.getItem(USER_KEY)
      sharedUser.value = raw ? JSON.parse(raw) : null
    } catch {
      sharedToken.value = ''
      sharedUser.value = null
    }
  }
  const token = sharedToken
  const user = sharedUser

  const isLoggedIn = computed(() => !!token.value)

  const setAuth = (accessToken: string, profile: AuthUser | null) => {
    token.value = accessToken
    user.value = profile
    try {
      if (accessToken) {
        localStorage.setItem(TOKEN_KEY, accessToken)
        localStorage.setItem(USER_KEY, JSON.stringify(profile || null))
      } else {
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem(USER_KEY)
      }
    } catch { /* 隐私模式下忽略持久化失败 */ }
  }

  const clearAuth = () => setAuth('', null)

  /** 登录成功后跳转目标（仅允许站内相对路径，防开放重定向） */
  const safeRedirect = (target: unknown): string => {
    const t = typeof target === 'string' ? target : ''
    return t.startsWith('/') && !t.startsWith('//') ? t : '/'
  }

  const login = async (username: string, password: string): Promise<AuthUser | null> => {
    try {
      const data = await $fetch<{ accessToken: string; user: AuthUser }>('/api/v1/auth/login', {
        method: 'POST',
        baseURL: config.public.apiBase || undefined,
        retry: 0,
        headers: { 'Content-Type': 'application/json' },
        body: { username, password }
      })
      setAuth(data.accessToken, data.user || null)
      return data.user || null
    } catch (err: any) {
      const detail = err?.data?.message || err?.data?.error || err?.message || '登录请求失败'
      throw new Error(String(detail))
    }
  }

  const logout = async () => {
    try {
      await $fetch('/api/v1/auth/logout', {
        method: 'POST',
        baseURL: config.public.apiBase || undefined,
        retry: 0,
        headers: { 'Content-Type': 'application/json', ...(token.value ? { Authorization: `Bearer ${token.value}` } : {}) }
      })
    } catch { /* 无状态令牌，登出失败也直接本地清除 */ }
    clearAuth()
  }

  return { token, user, isLoggedIn, login, logout, setAuth, clearAuth, safeRedirect }
}
