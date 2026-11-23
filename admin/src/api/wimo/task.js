import request from '@/utils/request'

// 生成任务列表（管理端）
export function listTask(query) {
  return request({
    url: '/wimo/task/list',
    method: 'get',
    params: query
  })
}

// 任务详情（含结果文件明细）
export function getTask(taskId) {
  return request({
    url: '/wimo/task/' + taskId,
    method: 'get'
  })
}

// 删除任务（支持逗号分隔的多个 ID）
export function delTask(taskIds) {
  return request({
    url: '/wimo/task/' + taskIds,
    method: 'delete'
  })
}

// 重试任务（重置状态并重新提交生成）
export function retryTask(taskId) {
  return request({
    url: '/wimo/task/retry/' + taskId,
    method: 'post'
  })
}

// 取消任务
export function cancelTask(taskId) {
  return request({
    url: '/wimo/task/cancel/' + taskId,
    method: 'post'
  })
}

// 手动修改任务状态
export function changeTaskStatus(taskId, status) {
  return request({
    url: '/wimo/task/status/' + taskId + '/' + status,
    method: 'post'
  })
}
