import request from '@/utils/request'

// 验证码邮件日志
export function listMail(query) {
  return request({
    url: '/wimo/mail/list',
    method: 'get',
    params: query
  })
}
