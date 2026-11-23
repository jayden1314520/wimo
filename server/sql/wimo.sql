-- ----------------------------
-- AI 套图任务表
-- ----------------------------
drop table if exists wimo_task;
create table wimo_task (
  task_id        bigint        not null auto_increment  comment '任务ID',
  user_id        bigint        default 0                comment '用户ID（未登录为0）',
  product_name   varchar(200)  default ''               comment '商品名称',
  task_type      varchar(50)  default 'productSet'     comment '任务类型',
  selling_points text                                   comment '卖点与要求',
  ratio          varchar(20)   default '3:4'            comment '画面比例',
  platform       varchar(30)   default ''               comment '平台',
  region         varchar(30)   default ''               comment '国家/地区',
  lang           varchar(20)   default ''               comment '语言',
  resolution     varchar(20)   default ''               comment '清晰度',
  quality        varchar(20)   default ''               comment '质量',
  model          varchar(50)   default ''               comment '模型',
  result_mode    varchar(20)   default 'default'        comment '结果配置（default/custom）',
  images         text                                   comment '素材列表（逗号分隔）',
  status         char(1)       default '0'              comment '状态（0待生成 1生成中 2完成 3失败）',
  progress       int           default 0                comment '进度（0-8）',
  results        text                                   comment '结果图（逗号分隔的图片标识）',
  extra          text                                   comment '扩展参数(JSON: uploads/values)',
  error_msg      varchar(500)  default ''               comment '失败原因',
  create_time    datetime                               comment '创建时间',
  update_time    datetime                               comment '更新时间',
  primary key (task_id),
  key idx_status (status),
  key idx_create_time (create_time)
) engine=innodb auto_increment=1 comment = 'AI套图任务表';

-- ----------------------------
-- 套图管理菜单（menu_id 从 2000 起，避免与若依内置冲突）
-- ----------------------------
insert into sys_menu values (2000, '套图管理', 0, 5, 'wimo', null, '', '', 1, 0, 'M', '0', '0', '', 'shopping', 'admin', sysdate(), '', null, 'AI 套图业务目录');

insert into sys_menu values (2001, '任务管理', 2000, 1, 'task', 'wimo/task/index', '', '', 1, 0, 'C', '0', '0', 'wimo:task:list', 'list', 'admin', sysdate(), '', null, 'AI 套图任务列表');

insert into sys_menu values (2002, '任务查询', 2001, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'wimo:task:query', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values (2003, '任务删除', 2001, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'wimo:task:remove', '#', 'admin', sysdate(), '', null, '');

-- 超管角色（role_id=1）自动拥有全部权限，此处同步普通角色模板可按需追加
