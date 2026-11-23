import request from '@/utils/request'

// 查询套图任务列表（管理端接口）
export function listTask(query) {
  return request({
    url: '/wimo/task/list',
    method: 'get',
    params: query
  })
}

// 查询套图任务详情
export function getTask(taskId) {
  return request({
    url: '/wimo/task/' + taskId,
    method: 'get'
  })
}

// 删除套图任务
export function delTask(taskId) {
  return request({
    url: '/wimo/task/' + taskId,
    method: 'delete'
  })
}
