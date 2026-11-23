-- ============================================================
-- 映物 VIVID · 完整业务库结构
-- 执行：mysql -uroot ry < wimo.sql
-- 说明：会员 / 模板配置 / 任务 / 结果文件 / 邮件日志 / 管理端菜单
-- ============================================================

-- ----------------------------
-- 1. 会员表（独立于若依 sys_user，避免污染后台账号）
-- ----------------------------
drop table if exists wimo_user;
create table wimo_user (
  user_id         bigint       not null auto_increment comment '会员ID',
  email           varchar(120) not null               comment '邮箱（登录账号）',
  password_hash   varchar(120) not null               comment 'BCrypt 密码散列',
  nickname        varchar(60)  default ''             comment '昵称',
  status          char(1)      default '0'            comment '状态（0正常 1停用）',
  agreed          char(1)      default '0'            comment '是否同意用户协议',
  last_login_time datetime                            comment '最后登录时间',
  create_time     datetime                            comment '注册时间',
  update_time     datetime                            comment '更新时间',
  primary key (user_id),
  unique key uk_wimo_user_email (email)
) engine=innodb auto_increment=1 comment='映物会员表';

-- ----------------------------
-- 2. 邮件/验证码发送日志（本地模拟与真实 SMTP 均落库，便于管理端审计）
-- ----------------------------
drop table if exists wimo_mail_log;
create table wimo_mail_log (
  id           bigint       not null auto_increment comment 'ID',
  email        varchar(120) not null               comment '收件邮箱',
  scene        varchar(30)  not null               comment '场景 register/login/reset',
  code         varchar(10)  default ''             comment '验证码（仅演示记录）',
  provider     varchar(30)  default 'mock'         comment '通道 mock/smtp',
  success      char(1)      default '1'            comment '是否成功',
  remark       varchar(500) default ''             comment '备注',
  create_time  datetime                            comment '发送时间',
  primary key (id),
  key idx_mail_email (email),
  key idx_mail_time (create_time)
) engine=innodb auto_increment=1 comment='验证码邮件日志';

-- ----------------------------
-- 3. 功能模板配置（管理端可编辑，驱动 13 个功能的后端校验）
-- ----------------------------
drop table if exists wimo_schema;
create table wimo_schema (
  id           bigint       not null auto_increment comment 'ID',
  task_key     varchar(50)  not null               comment '功能 key',
  name_zh      varchar(60)  default ''             comment '中文名',
  name_en      varchar(60)  default ''             comment '英文名',
  group_key    varchar(30)  default 'ecommerce'    comment '分组 ecommerce/model/video',
  icon         varchar(30)  default ''             comment '图标',
  sort_no      int          default 0              comment '排序',
  enabled      char(1)      default '1'            comment '是否启用 1/0',
  schema_json  longtext                            comment '完整 schema JSON',
  remark       varchar(255) default ''             comment '备注',
  create_time  datetime                            comment '创建时间',
  update_time  datetime                            comment '更新时间',
  primary key (id),
  unique key uk_wimo_schema_key (task_key)
) engine=innodb auto_increment=1 comment='功能模板配置';

-- ----------------------------
-- 4. 任务结果文件（一任务多结果，图片/视频均可）
-- ----------------------------
drop table if exists wimo_task_result;
create table wimo_task_result (
  result_id   bigint       not null auto_increment comment '结果ID',
  task_id     bigint       not null               comment '任务ID',
  file_url    varchar(500) not null               comment '结果文件 URL',
  file_type   varchar(20)  default 'image'        comment 'image/video',
  sort_no     int          default 0              comment '顺序',
  create_time datetime                            comment '创建时间',
  primary key (result_id),
  key idx_result_task (task_id)
) engine=innodb auto_increment=1 comment='任务结果文件';

-- ----------------------------
-- 5. 生成任务表（schema 驱动，保留前端原有扁平字段并扩展）
-- ----------------------------
drop table if exists wimo_task;
create table wimo_task (
  task_id        bigint       not null auto_increment comment '任务ID',
  user_id        bigint       default 0               comment '会员ID（0=匿名演示）',
  task_type      varchar(50)  default 'productSet'   comment '功能 key',
  title          varchar(200) default ''             comment '任务标题',
  product_name   varchar(200) default ''             comment '商品/店铺名称',
  selling_points text                                comment '卖点与要求（兼容旧字段）',
  ratio          varchar(20)  default '3:4'          comment '画面比例',
  platform       varchar(30)  default ''             comment '平台',
  region         varchar(30)  default ''             comment '国家/地区',
  lang           varchar(20)  default ''             comment '语言',
  resolution     varchar(20)  default ''             comment '清晰度',
  quality        varchar(20)  default ''             comment '质量',
  model          varchar(50)  default ''             comment '模型',
  result_mode    varchar(20)  default 'default'      comment '结果配置',
  images         text                                comment '素材 URL 列表（逗号分隔）',
  payload        longtext                            comment '完整参数 JSON（uploads/values/common）',
  status         char(1)      default '0'            comment '0待生成 1生成中 2完成 3失败 4已取消',
  progress       int          default 0              comment '进度 0-100',
  results        text                                comment '结果 URL（逗号分隔，兼容旧字段）',
  extra          text                                comment '扩展参数（兼容旧字段）',
  result_count   int          default 0              comment '结果数量',
  retry_count    int          default 0              comment '重试次数',
  cost_ms        bigint       default 0              comment '耗时毫秒',
  error_msg      varchar(500) default ''             comment '失败原因',
  finish_time    datetime                            comment '完成时间',
  create_time    datetime                            comment '创建时间',
  update_time    datetime                            comment '更新时间',
  primary key (task_id),
  key idx_task_user (user_id),
  key idx_task_type (task_type),
  key idx_task_status (status),
  key idx_task_create (create_time)
) engine=innodb auto_increment=1 comment='AI 生成任务表';

-- ----------------------------
-- 6. 管理端菜单（2000-2099，避免与若依内置冲突）
-- ----------------------------
delete from sys_menu where menu_id between 2000 and 2099;
delete from sys_role_menu where menu_id between 2000 and 2099;

insert into sys_menu values
 (2000, '映物业务', 0, 5, 'wimo', null, '', '', 1, 0, 'M', '0', '0', '', 'shopping', 'admin', sysdate(), '', null, '映物 VIVID 业务目录'),
 (2001, '任务管理', 2000, 1, 'task', 'wimo/task/index', '', '', 1, 0, 'C', '0', '0', 'wimo:task:list', 'list', 'admin', sysdate(), '', null, '生成任务列表'),
 (2002, '任务查询', 2001, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'wimo:task:query', '#', 'admin', sysdate(), '', null, ''),
 (2003, '任务删除', 2001, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'wimo:task:remove', '#', 'admin', sysdate(), '', null, ''),
 (2004, '任务重试', 2001, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'wimo:task:retry', '#', 'admin', sysdate(), '', null, ''),
 (2005, '数据看板', 2000, 2, 'dashboard', 'wimo/dashboard/index', '', '', 1, 0, 'C', '0', '0', 'wimo:dashboard:list', 'chart', 'admin', sysdate(), '', null, '业务数据看板'),
 (2006, '模板配置', 2000, 3, 'schema', 'wimo/schema/index', '', '', 1, 0, 'C', '0', '0', 'wimo:schema:list', 'build', 'admin', sysdate(), '', null, '13 个功能表单模板'),
 (2007, '模板编辑', 2006, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'wimo:schema:edit', '#', 'admin', sysdate(), '', null, ''),
 (2008, '邮件日志', 2000, 4, 'mail', 'wimo/mail/index', '', '', 1, 0, 'C', '0', '0', 'wimo:mail:list', 'email', 'admin', sysdate(), '', null, '验证码发送记录');

-- ----------------------------
-- 7. 字典（任务状态 / 分组），管理端下拉用
-- ----------------------------
delete from sys_dict_type where dict_type in ('wimo_task_status', 'wimo_task_group');
delete from sys_dict_data where dict_type in ('wimo_task_status', 'wimo_task_group');
insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values
 (100, '生成任务状态', 'wimo_task_status', '0', 'admin', sysdate(), '映物任务状态'),
 (101, '功能分组',     'wimo_task_group',  '0', 'admin', sysdate(), '映物功能分组');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values
 (1000, 1, '待生成',   '0', 'wimo_task_status', '', 'info',    'Y', '0', 'admin', sysdate(), ''),
 (1001, 2, '生成中',   '1', 'wimo_task_status', '', 'primary', 'N', '0', 'admin', sysdate(), ''),
 (1002, 3, '已完成',   '2', 'wimo_task_status', '', 'success', 'N', '0', 'admin', sysdate(), ''),
 (1003, 4, '失败',     '3', 'wimo_task_status', '', 'danger',  'N', '0', 'admin', sysdate(), ''),
 (1004, 5, '已取消',   '4', 'wimo_task_status', '', 'warning', 'N', '0', 'admin', sysdate(), ''),
 (1010, 1, '电商套图', 'ecommerce', 'wimo_task_group', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1011, 2, '模特相关', 'model',      'wimo_task_group', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1012, 3, '视频模块', 'video',      'wimo_task_group', '', '', 'N', '0', 'admin', sysdate(), '');
