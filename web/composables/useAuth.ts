/**
 * 全局会话与弹层状态（useState 保证 SSR / SSG 安全）
 * - user: 当前登录用户（邮箱）—— 登录即注册，静态演示态存 localStorage
 * - authOpen: 登录弹窗开关
 * - mobileNavOpen: 移动端左侧菜单抽屉开关
 * - toast: 全局轻提示
 */
export interface WimoUser {
  email: string
  loginAt: string
}

const USER_KEY = 'wimo_user'

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
    openAuth, closeAuth, openNav, closeNav, showToast
  }
}
