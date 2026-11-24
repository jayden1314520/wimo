import request from '@/utils/request'

// 计费规则树（能力 → 维度档位 / 模型 / 工作流）
export function capabilityTree() {
  return request({ url: '/wimo/capability/tree', method: 'get' })
}
export function listCapability() {
  return request({ url: '/wimo/capability/list', method: 'get' })
}
export function getCapability(id) {
  return request({ url: '/wimo/capability/' + id, method: 'get' })
}
export function updateCapability(data) {
  return request({ url: '/wimo/capability', method: 'put', data })
}
export function reloadCapability() {
  return request({ url: '/wimo/capability/reload', method: 'post' })
}

// 档位节点
export function listPriceNode(capabilityKey) {
  return request({ url: '/wimo/capability/node/list', method: 'get', params: { capabilityKey } })
}
export function addPriceNode(data) {
  return request({ url: '/wimo/capability/node', method: 'post', data })
}
export function updatePriceNode(data) {
  return request({ url: '/wimo/capability/node', method: 'put', data })
}
export function togglePriceNode(nodeId, enabled) {
  return request({ url: '/wimo/capability/node/toggle', method: 'put', data: { nodeId, enabled } })
}
export function delPriceNode(id) {
  return request({ url: '/wimo/capability/node/' + id, method: 'delete' })
}
