import request from '@/utils/request'

// 模板配置列表（13 个功能）
export function listSchema() {
  return request({
    url: '/wimo/schema/list',
    method: 'get'
  })
}

// 模板详情
export function getSchema(id) {
  return request({
    url: '/wimo/schema/' + id,
    method: 'get'
  })
}

// 更新模板（启停 / 改名 / 排序 / schema JSON）
export function updateSchema(data) {
  return request({
    url: '/wimo/schema',
    method: 'put',
    data
  })
}

// 适配器切换提示
export function schemaProviders() {
  return request({
    url: '/wimo/schema/providers',
    method: 'get'
  })
}
