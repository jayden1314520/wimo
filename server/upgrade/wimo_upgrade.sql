-- ============================================================
-- 映物 VIVID · v1 → v2 增量升级脚本（不清空已有数据）
-- 执行：mysql -uroot ry < wimo_upgrade.sql
-- 用于已经跑过旧版 wimo.sql 的库；全新库直接执行 wimo.sql 即可
-- ============================================================

-- 会员表：钻石钱包
alter table wimo_user
  add column if not exists diamond_balance bigint default 0 comment '可用钻石余额' after agreed,
  add column if not exists diamond_frozen  bigint default 0 comment '冻结中钻石' after diamond_balance,
  add column if not exists diamond_total   bigint default 0 comment '累计获得钻石' after diamond_frozen,
  add column if not exists diamond_used    bigint default 0 comment '累计消耗钻石' after diamond_total;

-- 已注册用户补发体验钻石（可重复执行：仅补余额为 0 且无赠送流水的用户）
update wimo_user set diamond_balance = 100, diamond_total = 100
 where diamond_balance = 0 and diamond_total = 0;

-- 任务表：计费字段
alter table wimo_task
  add column if not exists workflow_id      bigint default 0    comment '关联工作流/智能体' after task_type,
  add column if not exists model_key        varchar(60) default '' comment '计费采用的模型 key' after model,
  add column if not exists cost_diamonds    bigint default 0    comment '本单消耗钻石' after model_key,
  add column if not exists billing_snapshot longtext            comment '计费明细快照 JSON' after cost_diamonds,
  add column if not exists diamond_state    char(1) default '9' comment '0冻结 1已扣 2已退 9不计费' after billing_snapshot;

alter table wimo_task
  add index if not exists idx_task_billing (diamond_state);

-- 模型注册表
create table if not exists wimo_model (
  model_id         bigint       not null auto_increment comment '模型ID',
  model_key        varchar(60)  not null               comment '模型唯一标识',
  name_zh          varchar(80)  default '',
  name_en          varchar(80)  default '',
  capability       varchar(30)  not null               comment 'text2img/img2img/img2video/text2video',
  provider         varchar(30)  default 'mock',
  endpoint         varchar(300) default '',
  credential_ref   varchar(120) default '',
  cost_multiplier  decimal(6,2) default 1.00,
  config_json      longtext,
  enabled          char(1)      default '1',
  sort_no          int          default 0,
  remark           varchar(255) default '',
  create_time      datetime,
  update_time      datetime,
  primary key (model_id),
  unique key uk_wimo_model_key (model_key),
  key idx_model_cap (capability, enabled)
) engine=innodb auto_increment=1 comment='AI 模型注册表';

-- 工作流 / 智能体
create table if not exists wimo_workflow (
  workflow_id    bigint       not null auto_increment,
  workflow_key   varchar(50)  not null,
  name_zh        varchar(60)  default '',
  name_en        varchar(60)  default '',
  group_key      varchar(30)  default 'ecommerce',
  capability     varchar(30)  default 'img2img',
  allowed_models varchar(500) default '',
  default_model  varchar(60)  default '',
  base_cost      bigint       default 10,
  unit           varchar(20)  default 'count',
  unit_cost      decimal(8,2) default 0.00,
  unit_field     varchar(40)  default '',
  multipliers    longtext,
  min_cost       bigint       default 1,
  max_cost       bigint       default 2000,
  steps_json     longtext,
  enabled        char(1)      default '1',
  sort_no        int          default 0,
  remark         varchar(255) default '',
  create_time    datetime,
  update_time    datetime,
  primary key (workflow_id),
  unique key uk_wimo_workflow_key (workflow_key)
) engine=innodb auto_increment=1 comment='工作流 / 智能体定义';

-- 钻石流水
create table if not exists wimo_diamond_log (
  id             bigint       not null auto_increment,
  user_id        bigint       not null,
  change_type    varchar(30)  not null,
  amount         bigint       not null,
  balance_before bigint       default 0,
  balance_after  bigint       default 0,
  frozen_before  bigint       default 0,
  frozen_after   bigint       default 0,
  task_id        bigint       default null,
  workflow_key   varchar(50)  default '',
  operator       varchar(60)  default 'system',
  remark         varchar(300) default '',
  create_time    datetime,
  primary key (id),
  key idx_diamond_user (user_id, create_time),
  key idx_diamond_type (change_type),
  key idx_diamond_task (task_id)
) engine=innodb auto_increment=1 comment='钻石账户流水';

-- 菜单与字典（全量覆盖 2000-2099，幂等）
delete from sys_menu where menu_id between 2000 and 2099;
delete from sys_role_menu where menu_id between 2000 and 2099;
insert into sys_menu values
 (2000, '映物业务',   0,    5, 'wimo',     null,                    '', '', 1, 0, 'M', '0', '0', '',                    'shopping', 'admin', sysdate(), '', null, '映物 VIVID 业务目录'),
 (2001, '任务管理',   2000, 1, 'task',     'wimo/task/index',      '', '', 1, 0, 'C', '0', '0', 'wimo:task:list',      'list',     'admin', sysdate(), '', null, '生成任务列表与计费快照'),
 (2002, '任务查询',   2001, 1, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:task:query',     '#',        'admin', sysdate(), '', null, ''),
 (2003, '任务删除',   2001, 2, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:task:remove',    '#',        'admin', sysdate(), '', null, ''),
 (2004, '任务重试',   2001, 3, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:task:retry',     '#',        'admin', sysdate(), '', null, ''),
 (2005, '数据看板',   2000, 2, 'dashboard','wimo/dashboard/index', '', '', 1, 0, 'C', '0', '0', 'wimo:dashboard:list', 'chart',    'admin', sysdate(), '', null, '业务与钻石消耗看板'),
 (2006, '模板配置',   2000, 3, 'schema',   'wimo/schema/index',    '', '', 1, 0, 'C', '0', '0', 'wimo:schema:list',    'build',    'admin', sysdate(), '', null, '13 个功能表单模板'),
 (2007, '模板编辑',   2006, 1, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:schema:edit',    '#',        'admin', sysdate(), '', null, ''),
 (2008, '邮件日志',   2000, 7, 'mail',     'wimo/mail/index',      '', '', 1, 0, 'C', '0', '0', 'wimo:mail:list',      'email',    'admin', sysdate(), '', null, '验证码发送记录'),
 (2009, '模型管理',   2000, 4, 'model',    'wimo/model/index',     '', '', 1, 0, 'C', '0', '0', 'wimo:model:list',     'server',   'admin', sysdate(), '', null, '文生图/图生图/图生视频/文生视频模型'),
 (2010, '模型编辑',   2009, 1, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:model:edit',     '#',        'admin', sysdate(), '', null, ''),
 (2011, '模型删除',   2009, 2, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:model:remove',   '#',        'admin', sysdate(), '', null, ''),
 (2012, '智能体工作流',2000, 5, 'workflow','wimo/workflow/index',  '', '', 1, 0, 'C', '0', '0', 'wimo:workflow:list',  'tree',     'admin', sysdate(), '', null, '13 个菜单对应的智能体与计费规则'),
 (2013, '工作流编辑', 2012, 1, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:workflow:edit',  '#',        'admin', sysdate(), '', null, ''),
 (2014, '钻石管理',   2000, 6, 'diamond',  'wimo/diamond/index',   '', '', 1, 0, 'C', '0', '0', 'wimo:diamond:list',   'money',    'admin', sysdate(), '', null, '会员余额与钻石流水'),
 (2015, '钻石调整',   2014, 1, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:diamond:edit',   '#',        'admin', sysdate(), '', null, '');

delete from sys_dict_type where dict_type in ('wimo_task_status','wimo_task_group','wimo_model_capability','wimo_diamond_change','wimo_billing_unit');
delete from sys_dict_data where dict_type in ('wimo_task_status','wimo_task_group','wimo_model_capability','wimo_diamond_change','wimo_billing_unit');
insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values
 (100, '生成任务状态', 'wimo_task_status',      '0', 'admin', sysdate(), ''),
 (101, '功能分组',     'wimo_task_group',       '0', 'admin', sysdate(), ''),
 (102, '模型能力',     'wimo_model_capability', '0', 'admin', sysdate(), ''),
 (103, '钻石流水类型', 'wimo_diamond_change',   '0', 'admin', sysdate(), ''),
 (104, '计费单位',     'wimo_billing_unit',     '0', 'admin', sysdate(), '');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values
 (1000, 1, '待生成',   '0', 'wimo_task_status', '', 'info',    'Y', '0', 'admin', sysdate(), ''),
 (1001, 2, '生成中',   '1', 'wimo_task_status', '', 'primary', 'N', '0', 'admin', sysdate(), ''),
 (1002, 3, '已完成',   '2', 'wimo_task_status', '', 'success', 'N', '0', 'admin', sysdate(), ''),
 (1003, 4, '失败',     '3', 'wimo_task_status', '', 'danger',  'N', '0', 'admin', sysdate(), ''),
 (1004, 5, '已取消',   '4', 'wimo_task_status', '', 'warning', 'N', '0', 'admin', sysdate(), ''),
 (1010, 1, '电商套图', 'ecommerce', 'wimo_task_group', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1011, 2, '模特相关', 'model',      'wimo_task_group', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1012, 3, '视频模块', 'video',      'wimo_task_group', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1020, 1, '文生图',   'text2img',   'wimo_model_capability', '', 'primary', 'N', '0', 'admin', sysdate(), ''),
 (1021, 2, '图生图',   'img2img',    'wimo_model_capability', '', 'success', 'N', '0', 'admin', sysdate(), ''),
 (1022, 3, '图生视频', 'img2video',  'wimo_model_capability', '', 'warning', 'N', '0', 'admin', sysdate(), ''),
 (1023, 4, '文生视频', 'text2video', 'wimo_model_capability', '', 'danger',  'N', '0', 'admin', sysdate(), ''),
 (1030, 1, '注册赠送', 'gift',     'wimo_diamond_change', '', 'success', 'N', '0', 'admin', sysdate(), ''),
 (1031, 2, '充值',     'recharge', 'wimo_diamond_change', '', 'success', 'N', '0', 'admin', sysdate(), ''),
 (1032, 3, '生成预扣', 'freeze',   'wimo_diamond_change', '', 'info',    'N', '0', 'admin', sysdate(), ''),
 (1033, 4, '生成扣减', 'consume',  'wimo_diamond_change', '', 'danger',  'N', '0', 'admin', sysdate(), ''),
 (1034, 5, '失败退还', 'refund',   'wimo_diamond_change', '', 'warning', 'N', '0', 'admin', sysdate(), ''),
 (1035, 6, '人工调整', 'adjust',   'wimo_diamond_change', '', 'primary', 'N', '0', 'admin', sysdate(), ''),
 (1040, 1, '固定',     'flat',   'wimo_billing_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1041, 2, '按张',     'count',  'wimo_billing_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1042, 3, '按秒',     'second', 'wimo_billing_unit', '', '', 'N', '0', 'admin', sysdate(), '');
