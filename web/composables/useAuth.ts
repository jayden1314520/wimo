/**
 * 全局会话与弹层状态（useState 保证 SSR / SSG 安全）
 *
 * - 后端可达时：走真实注册/登录/验证码/找回密码接口，token 存 localStorage
 * - 后端不可达时：自动降级为本地演示账号（GitHub Pages 静态演示仍可用）
 * - 密码只提交给后端，本地演示账号仅用于无后端环境，生产部署务必启用后端
 */
import {
  NetworkError, useWimoApi,
  getAuthToken, setAuthToken, clearAuthToken,
  getCachedUser, setCachedUser, clearCachedUser
} from './useWimoApi'

export interface WimoUser {
  email: string
  userId?: number
  nickname?: string
  loginAt: string
}

interface WimoAccount {
  email: string
  password: string
  createAt: string
}

const ACCOUNTS_KEY = 'wimo_accounts'

export function useAuth() {
  const user = useState<WimoUser | null>('wimo_user', () => null)
  const authOpen = useState<boolean>('wimo_auth_open', () => false)
  const mobileNavOpen = useState<boolean>('wimo_mobile_nav', () => false)
  const toast = useState<string>('wimo_toast', () => '')
  const api = useWimoApi()

  /** 客户端挂载后恢复登录态；有 token 时向后端校验一次 */
  function restore() {
    if (!import.meta.client) return
    const cached = getCachedUser()
    if (cached) user.value = cached as WimoUser
    if (getAuthToken()) {
      api.auth.profile()
        .then(p => {
          const u: WimoUser = {
            email: p.email, userId: p.userId, nickname: p.nickname,
            loginAt: new Date().toISOString()
          }
          user.value = u
          setCachedUser(u)
        })
        .catch(e => {
          if (!(e instanceof NetworkError)) clearSession()
        })
    }
  }

  function signIn(u: WimoUser, token?: string) {
    user.value = u
    setCachedUser(u)
    if (token) setAuthToken(token)
  }

  function clearSession() {
    user.value = null
    clearCachedUser()
    clearAuthToken()
  }

  function signOut() {
    api.auth.logout().catch(() => {})
    clearSession()
  }

  /* ---------------- 本地演示账号库（仅降级用） ---------------- */
  function readAccounts(): WimoAccount[] {
    if (!import.meta.client) return []
    try { return JSON.parse(localStorage.getItem(ACCOUNTS_KEY) || '[]') as WimoAccount[] }
    catch { return [] }
  }
  function hasAccount(email: string): boolean {
    return readAccounts().some(a => a.email.toLowerCase() === email.toLowerCase())
  }
  function registerAccount(email: string, password: string): boolean {
    const list = readAccounts()
    if (list.some(a => a.email.toLowerCase() === email.toLowerCase())) return false
    list.push({ email, password, createAt: new Date().toISOString() })
    if (import.meta.client) localStorage.setItem(ACCOUNTS_KEY, JSON.stringify(list))
    return true
  }
  function verifyPassword(email: string, password: string): 'ok' | 'notfound' | 'wrong' {
    const acc = readAccounts().find(a => a.email.toLowerCase() === email.toLowerCase())
    if (!acc) return 'notfound'
    return acc.password === password ? 'ok' : 'wrong'
  }
  function resetLocalPassword(email: string, password: string): boolean {
    const list = readAccounts()
    const acc = list.find(a => a.email.toLowerCase() === email.toLowerCase())
    if (!acc) return false
    acc.password = password
    if (import.meta.client) localStorage.setItem(ACCOUNTS_KEY, JSON.stringify(list))
    return true
  }

  /* ---------------- 账号操作（后端优先，网络不可达降级） ---------------- */
  async function withFallback<T>(fn: () => Promise<T>, fallback: () => T): Promise<T> {
    try { return await fn() }
    catch (e) { if (e instanceof NetworkError) return fallback(); throw e }
  }

  function toUser(data: any, email: string): WimoUser {
    return {
      email: data?.email || email,
      userId: data?.userId,
      nickname: data?.nickname,
      loginAt: new Date().toISOString()
    }
  }

  /** 发送验证码；mock 邮件通道返回演示码用于提示 */
  async function sendCode(email: string, scene: string): Promise<{ demoCode: string; expiresIn: number }> {
    return withFallback(
      async () => {
        const r = await api.auth.sendCode(email, scene)
        return { demoCode: r.demoCode || '', expiresIn: r.expiresIn || 300 }
      },
      () => ({ demoCode: String(Math.floor(100000 + Math.random() * 900000)), expiresIn: 300 })
    )
  }

  async function register(p: { email: string; code: string; password: string; confirmPassword: string; agreed: boolean }): Promise<WimoUser> {
    return withFallback(
      async () => {
        const d = await api.auth.register(p)
        const u = toUser(d, p.email)
        signIn(u, d.token)
        return u
      },
      () => {
        if (!registerAccount(p.email, p.password)) throw new Error('该邮箱已注册，请直接登录')
        const u: WimoUser = { email: p.email, loginAt: new Date().toISOString() }
        signIn(u)
        return u
      }
    )
  }

  async function loginPassword(email: string, password: string): Promise<WimoUser> {
    return withFallback(
      async () => {
        const d = await api.auth.loginPassword(email, password)
        const u = toUser(d, email)
        signIn(u, d.token)
        return u
      },
      () => {
        const r = verifyPassword(email, password)
        if (r === 'notfound') throw new Error('账号不存在，请先注册')
        if (r === 'wrong') throw new Error('密码不正确')
        const u: WimoUser = { email, loginAt: new Date().toISOString() }
        signIn(u)
        return u
      }
    )
  }

  async function loginCode(email: string, code: string): Promise<WimoUser> {
    return withFallback(
      async () => {
        const d = await api.auth.loginCode(email, code)
        const u = toUser(d, email)
        signIn(u, d.token)
        return u
      },
      () => {
        if (!hasAccount(email)) throw new Error('账号不存在，请先注册')
        const u: WimoUser = { email, loginAt: new Date().toISOString() }
        signIn(u)
        return u
      }
    )
  }

  async function resetPassword(email: string, code: string, password: string): Promise<void> {
    return withFallback(
      async () => { await api.auth.reset(email, code, password) },
      () => { if (!resetLocalPassword(email, password)) throw new Error('账号不存在') }
    )
  }

  const openAuth = () => { authOpen.value = true }
  const closeAuth = () => { authOpen.value = false }
  const openNav = () => { mobileNavOpen.value = true }
  const closeNav = () => { mobileNavOpen.value = false }

  let timer: ReturnType<typeof setTimeout> | null = null
  function showToast(msg: string) {
    toast.value = msg
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => { toast.value = '' }, 3200)
  }

  return {
    user, authOpen, mobileNavOpen, toast,
    restore, signIn, signOut,
    hasAccount, registerAccount, verifyPassword,
    sendCode, register, loginPassword, loginCode, resetPassword,
    openAuth, closeAuth, openNav, closeNav, showToast
  }
}
