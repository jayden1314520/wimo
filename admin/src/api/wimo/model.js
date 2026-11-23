import request from '@/utils/request'

// 模型列表（四类能力）
export function listModel() {
  return request({ url: '/wimo/model/list', method: 'get' })
}
export function getModel(id) {
  return request({ url: '/wimo/model/' + id, method: 'get' })
}
export function addModel(data) {
  return request({ url: '/wimo/model', method: 'post', data })
}
export function updateModel(data) {
  return request({ url: '/wimo/model', method: 'put', data })
}
export function delModel(id) {
  return request({ url: '/wimo/model/' + id, method: 'delete' })
}
