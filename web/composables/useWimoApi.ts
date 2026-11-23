/**
 * 若依后端接口封装
 *
 * - 统一 R<T> = { code, msg, data } 契约
 * - 自动携带会员 token（Bearer）
 * - 网络/HTTP 异常抛 NetworkError，供上层降级为本地演示；业务错误抛普通 Error
 */

export class NetworkError extends Error {
  constructor(message = '后端不可达') {
    super(message)
    this.name = 'NetworkError'
  }
}

const TOKEN_KEY = 'wimo_token'
const USER_KEY = 'wimo_user'

export function getAuthToken(): string {
  if (!import.meta.client) return ''
  try { return localStorage.getItem(TOKEN_KEY) || '' } catch { return '' }
}
export function setAuthToken(token: string) {
  if (import.meta.client) localStorage.setItem(TOKEN_KEY, token)
}
export function clearAuthToken() {
  if (import.meta.client) localStorage.removeItem(TOKEN_KEY)
}
export function getCachedUser(): any {
  if (!import.meta.client) return null
  try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null') } catch { return null }
}
export function setCachedUser(u: any) {
  if (import.meta.client) localStorage.setItem(USER_KEY, JSON.stringify(u))
}
export function clearCachedUser() {
  if (import.meta.client) localStorage.removeItem(USER_KEY)
}

export interface WimoTaskSubmit {
  taskType: string
  title?: string
  uploads: Record<string, string[]>
  values: Record<string, any>
  common: Record<string, any>
  resultMode?: string
  extra?: string
}

export interface WimoTaskRecord {
  taskId: number
  taskType: string
  status: string
  progress: number
  results: string | null
  resultFiles?: { fileUrl: string; fileType: string }[]
  resultCount?: number
  errorMsg?: string
  costMs?: number
  costDiamonds?: number
  diamondState?: string
  billingSnapshot?: string
  modelKey?: string
  createTime: string
}

/** 管理端配置的 AI 模型 */
export interface WimoModel {
  modelKey: string
  nameZh: string
  nameEn: string
  capability: string
  costMultiplier: number
  config?: Record<string, any>
}

/** 智能体工作流（一个菜单 = 一个工作流） */
export interface WimoWorkflow {
  workflowKey: string
  nameZh: string
  nameEn: string
  group: string
  capability: string
  baseCost: number
  unit: string
  unitCost: number
  minCost: number
  maxCost: number
  defaultModel: string
}

/** 提交前预估消耗 */
export interface WimoQuote {
  cost: number
  balance: number
  enough: boolean
  anonymous: boolean
  modelKey: string
  workflowKey: string
  snapshot: Record<string, any>
}

export function useWimoApi() {
  const base = useRuntimeConfig().public.apiBase

  async function request<T>(path: string, options: any = {}): Promise<T> {
    const headers: Record<string, string> = { ...(options.headers || {}) }
    const tk = getAuthToken()
    // 独立 header：不占用 Authorization，避免若依后台 JWT 过滤器误解析会员令牌
    if (tk && !headers['X-Wimo-Token']) headers['X-Wimo-Token'] = tk
    let res: any
    try {
      res = await $fetch(`${base}${path}`, { ...options, headers })
    } catch (e: any) {
      throw new NetworkError(e?.message || '网络请求失败')
    }
    if (res?.code !== 200) throw new Error(res?.msg || '请求失败')
    return res.data as T
  }

  function uploadOne(file: File) {
    const fd = new FormData()
    fd.append('file', file)
    return request<{ url: string; name: string; size: number }>('/api/wimo/task/upload', { method: 'POST', body: fd })
  }

  function uploadMany(files: File[]) {
    const fd = new FormData()
    files.forEach(f => fd.append('files', f))
    return request<{ url: string; name: string }[]>('/api/wimo/task/uploads', { method: 'POST', body: fd })
  }

  return {
    /** 后端连通性 + 当前适配器状态 */
    health: () => request<Record<string, any>>('/api/wimo/task/health'),

    /** 功能模板（管理端可后台启停） */
    schemas: () => request<any[]>('/api/wimo/schema/list'),

    /** 智能体工作流目录（菜单 = 工作流，含起步价与计费单位） */
    workflows: () => request<WimoWorkflow[]>('/api/wimo/workflow/list'),

    /** 可选模型：按智能体 key 拉取（管理端配置，web 端不写死） */
    models: (workflowKey?: string, capability?: string) => {
      const q = new URLSearchParams()
      if (workflowKey) q.set('workflow', workflowKey)
      if (capability) q.set('capability', capability)
      return request<WimoModel[]>(`/api/wimo/model/list?${q.toString()}`)
    },

    /** 提交前预估本次消耗钻石（随清晰度/时长/模型实时变化） */
    quote: (submit: WimoTaskSubmit) =>
      request<WimoQuote>('/api/wimo/billing/quote', { method: 'POST', body: submit }),

    /** 当前会员钻石钱包 */
    diamondBalance: () => request<Record<string, any>>('/api/wimo/diamond/balance'),

    /** 钻石流水 */
    diamondLogs: (limit = 20) => request<any[]>(`/api/wimo/diamond/logs?limit=${limit}`),

    /** 首屏一次性配置（工作流 + 模型 + 余额） */
    marketConfig: () => request<Record<string, any>>('/api/wimo/market/config'),

    /** AI 帮写 */
    aiWrite: (body: Record<string, any>) =>
      request<{ text: string }>('/api/wimo/ai/write', { method: 'POST', body }),

    auth: {
      sendCode: (email: string, scene: string) =>
        request<{ sent: boolean; provider: string; expiresIn: number; demoCode?: string }>(
          '/api/wimo/auth/code', { method: 'POST', body: { email, scene } }),
      register: (body: Record<string, any>) =>
        request<{ token: string; email: string; nickname?: string; userId?: number }>(
          '/api/wimo/auth/register', { method: 'POST', body }),
      loginPassword: (email: string, password: string) =>
        request<{ token: string; email: string; nickname?: string; userId?: number }>(
          '/api/wimo/auth/login/password', { method: 'POST', body: { email, password } }),
      loginCode: (email: string, code: string) =>
        request<{ token: string; email: string; nickname?: string; userId?: number }>(
          '/api/wimo/auth/login/code', { method: 'POST', body: { email, code } }),
      reset: (email: string, code: string, password: string) =>
        request<any>('/api/wimo/auth/password/reset', {
          method: 'POST', body: { email, code, password }
        }),
      profile: () => request<Record<string, any>>('/api/wimo/auth/profile'),
      logout: () => request<any>('/api/wimo/auth/logout', { method: 'POST' })
    },

    createTask: (submit: WimoTaskSubmit) =>
      request<{ taskId: number; status: string; taskType: string }>('/api/wimo/task', {
        method: 'POST', body: submit
      }),

    getTask: (taskId: number) => request<WimoTaskRecord>(`/api/wimo/task/${taskId}`),

    listMyTasks: (params: { status?: string; page?: number; size?: number } = {}) => {
      const q = new URLSearchParams()
      if (params.status) q.set('status', params.status)
      q.set('page', String(params.page || 1))
      q.set('size', String(params.size || 10))
      return request<{ total: number; list: WimoTaskRecord[] }>(`/api/wimo/task/list?${q.toString()}`)
    },

    upload: uploadOne,
    uploads: uploadMany,

    retryTask: (taskId: number) =>
      request<WimoTaskRecord>(`/api/wimo/task/${taskId}/retry`, { method: 'POST' }),
    cancelTask: (taskId: number) =>
      request<WimoTaskRecord>(`/api/wimo/task/${taskId}/cancel`, { method: 'POST' }),
    deleteTask: (taskId: number) =>
      request<any>(`/api/wimo/task/${taskId}`, { method: 'DELETE' }),

    /** 兼容旧调用：生成记录列表 */
    listTasks: (limit = 10) =>
      request<{ total: number; list: WimoTaskRecord[] }>(`/api/wimo/task/list?page=1&size=${limit}`)
        .then(r => r.list || [])
  }
}
