/**
 * 全局会话与弹层状态（useState 保证 SSR / SSG 安全）
 * - user: 当前登录用户（邮箱）
 * - authOpen: 登录/注册弹窗开关
 * - mobileNavOpen: 移动端左侧菜单抽屉开关
 * - toast: 全局轻提示
 *
 * 账号库：静态演示环境无后端，账号存 localStorage。
 * 生产环境必须替换为后端接口（切勿在前端明文存密码）。
 */
export interface WimoUser {
  email: string
  loginAt: string
}

interface WimoAccount {
  email: string
  password: string
  createAt: string
}

const USER_KEY = 'wimo_user'
const ACCOUNTS_KEY = 'wimo_accounts'

export function useAuth() {
  const user = useState<WimoUser | null>('wimo_user', () => null)
  const authOpen = useState<boolean>('wimo_auth_open', () => false)
  const mobileNavOpen = useState<boolean>('wimo_mobile_nav', () => false)
  const toast = useState<string>('wimo_toast', () => '')

  /** 客户端挂载后恢复登录态 */
  function restore() {
    if (!import.meta.client) return
    try {
      const raw = localStorage.getItem(USER_KEY)
      if (raw) user.value = JSON.parse(raw) as WimoUser
    } catch {
      localStorage.removeItem(USER_KEY)
    }
  }

  function signIn(email: string) {
    const u: WimoUser = { email, loginAt: new Date().toISOString() }
    user.value = u
    if (import.meta.client) localStorage.setItem(USER_KEY, JSON.stringify(u))
  }

  function signOut() {
    user.value = null
    if (import.meta.client) localStorage.removeItem(USER_KEY)
  }

  /* ---------------- 演示账号库 ---------------- */
  function readAccounts(): WimoAccount[] {
    if (!import.meta.client) return []
    try {
      return JSON.parse(localStorage.getItem(ACCOUNTS_KEY) || '[]') as WimoAccount[]
    } catch {
      return []
    }
  }

  function hasAccount(email: string): boolean {
    return readAccounts().some(a => a.email.toLowerCase() === email.toLowerCase())
  }

  /** 注册：邮箱已存在则返回 false */
  function registerAccount(email: string, password: string): boolean {
    const list = readAccounts()
    if (list.some(a => a.email.toLowerCase() === email.toLowerCase())) return false
    list.push({ email, password, createAt: new Date().toISOString() })
    if (import.meta.client) localStorage.setItem(ACCOUNTS_KEY, JSON.stringify(list))
    return true
  }

  /** 密码登录校验：'ok' | 'notfound' | 'wrong' */
  function verifyPassword(email: string, password: string): 'ok' | 'notfound' | 'wrong' {
    const acc = readAccounts().find(a => a.email.toLowerCase() === email.toLowerCase())
    if (!acc) return 'notfound'
    return acc.password === password ? 'ok' : 'wrong'
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
    openAuth, closeAuth, openNav, closeNav, showToast
  }
}
