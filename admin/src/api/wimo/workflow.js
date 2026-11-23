import request from '@/utils/request'

// 智能体工作流列表（含模型池，供下拉选择）
export function listWorkflow() {
  return request({ url: '/wimo/workflow/list', method: 'get' })
}
export function getWorkflow(id) {
  return request({ url: '/wimo/workflow/' + id, method: 'get' })
}
export function updateWorkflow(data) {
  return request({ url: '/wimo/workflow', method: 'put', data })
}
