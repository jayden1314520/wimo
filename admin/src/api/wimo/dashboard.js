import request from '@/utils/request'

// 业务数据看板（总览 / 状态分布 / 功能分布 / 趋势 / 最近任务 / 适配器状态）
export function getDashboard(days = 7) {
  return request({
    url: '/wimo/dashboard',
    method: 'get',
    params: { days }
  })
}
