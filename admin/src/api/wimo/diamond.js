import request from '@/utils/request'

// 钻石总览
export function diamondOverview() {
  return request({ url: '/wimo/diamond/overview', method: 'get' })
}
// 会员钱包列表
export function diamondUsers() {
  return request({ url: '/wimo/diamond/users', method: 'get' })
}
// 钻石流水分页
export function listDiamondLog(query) {
  return request({ url: '/wimo/diamond/logs', method: 'get', params: query })
}
// 人工调整 / 赠送 / 扣减
export function adjustDiamond(data) {
  return request({ url: '/wimo/diamond/adjust', method: 'post', data })
}
