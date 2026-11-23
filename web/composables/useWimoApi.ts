/**
 * 若依后端接口封装
 * 统一处理 R<T> 返回结构（{ code, msg, data }）与错误提示
 */

export interface WimoTaskPayload {
  productName?: string
  sellingPoints: string
  ratio: string
  platform: string
  region: string
  lang: string
  resolution: string
  quality: string
  model: string
  resultMode: string
  images: string[]
}

export interface WimoTaskRecord {
  taskId: number
  status: string
  progress: number
  results: string | null
  sellingPoints: string
  createTime: string
  errorMsg?: string
}

export function useWimoApi() {
  const base = useRuntimeConfig().public.apiBase

  async function request<T>(path: string, options: any = {}): Promise<T> {
    const res: any = await $fetch(`${base}${path}`, options)
    if (res?.code !== 200) {
      throw new Error(res?.msg || '请求失败')
    }
    return res.data as T
  }

  return {
    /** 创建套图任务 */
    createTask: (payload: WimoTaskPayload) =>
      request<{ taskId: number; status: string }>('/api/wimo/task', {
        method: 'POST',
        body: { ...payload, images: payload.images.join(',') }
      }),

    /** 查询任务状态 */
    getTask: (taskId: number) => request<WimoTaskRecord>(`/api/wimo/task/${taskId}`),

    /** 生成记录列表 */
    listTasks: (limit = 10) =>
      request<WimoTaskRecord[]>(`/api/wimo/task/list?limit=${limit}`),

    /** 素材上传 */
    upload: (file: File) => {
      const fd = new FormData()
      fd.append('file', file)
      return request<{ url: string; name: string }>('/api/wimo/task/upload', {
        method: 'POST',
        body: fd
      })
    }
  }
}
