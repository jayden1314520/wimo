-- ============================================================
-- 映物 VIVID · v2 → v3 增量升级脚本：能力树 / 价格节点（不清空已有数据）
-- 执行：mysql -uroot ry < wimo_upgrade_v3.sql
-- 用于已跑过 v2（wimo.sql 或 wimo_upgrade.sql）的库；全新库直接执行 wimo.sql
-- 说明：能力层/价格节点/新模型的「初始数据」由后端启动时从
--       capabilities.json / price-nodes.json / models.json 自动灌入，本脚本只建表结构 + 覆盖菜单字典
-- 可重复执行（幂等）
-- ============================================================

-- ----------------------------
-- 1. 能力层（计费规则树根）
-- ----------------------------
create table if not exists wimo_capability (
  capability_id  bigint       not null auto_increment comment '能力ID',
  capability_key varchar(30)  not null               comment '能力唯一标识',
  name_zh        varchar(60)  default '',
  name_en        varchar(60)  default '',
  icon           varchar(30)  default '',
  base_cost      bigint       default 10             comment '该能力分支的基础钻石',
  unit           varchar(20)  default 'count',
  unit_cost      decimal(8,2) default 0.00,
  unit_field     varchar(40)  default '',
  min_cost       bigint       default 1,
  max_cost       bigint       default 2000,
  default_model  varchar(60)  default '',
  enabled        char(1)      default '1',
  sort_no        int          default 0,
  remark         varchar(255) default '',
  create_time    datetime,
  update_time    datetime,
  primary key (capability_id),
  unique key uk_wimo_capability_key (capability_key)
) engine=innodb auto_increment=1 comment='能力层（计费规则树根）';

-- ----------------------------
-- 2. 价格节点（能力下的维度档位，parent_id 自关联成树）
-- ----------------------------
create table if not exists wimo_price_node (
  node_id         bigint       not null auto_increment comment '节点ID',
  parent_id       bigint       default 0,
  capability_key  varchar(30)  not null,
  node_type       varchar(20)  default 'option'       comment 'group/option/model',
  dimension       varchar(40)  default '',
  node_key        varchar(60)  default '',
  option_value    varchar(40)  default '',
  label_zh        varchar(80)  default '',
  label_en        varchar(80)  default '',
  factor          decimal(8,3) default 1.000,
  unit_cost       decimal(8,2) default null,
  min_value       bigint       default null,
  max_value       bigint       default null,
  enabled         char(1)      default '1',
  sort_no         int          default 0,
  remark          varchar(255) default '',
  create_time     datetime,
  update_time     datetime,
  primary key (node_id),
  unique key uk_wimo_price_node_key (node_key),
  key idx_price_node_cap (capability_key, enabled),
  key idx_price_node_dim (capability_key, dimension, enabled)
) engine=innodb auto_increment=1 comment='计费价格节点（能力树档位）';

-- ----------------------------
-- 3. 工作流增加 price_source；模型表无需改（capability 已存在）
-- ----------------------------
alter table wimo_workflow
  add column if not exists cost_source varchar(20) default 'override' comment '价格来源 override/inherit' after max_cost;

-- ----------------------------
-- 4. 菜单与字典（全量覆盖 2000-2099，幂等）
-- ----------------------------
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
 (2009, '模型管理',   2000, 4, 'model',    'wimo/model/index',     '', '', 1, 0, 'C', '0', '0', 'wimo:model:list',     'server',   'admin', sysdate(), '', null, '五类能力模型（含文本生成预留）'),
 (2010, '模型编辑',   2009, 1, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:model:edit',     '#',        'admin', sysdate(), '', null, ''),
 (2011, '模型删除',   2009, 2, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:model:remove',   '#',        'admin', sysdate(), '', null, ''),
 (2012, '智能体工作流',2000, 5, 'workflow','wimo/workflow/index',  '', '', 1, 0, 'C', '0', '0', 'wimo:workflow:list',  'tree',     'admin', sysdate(), '', null, '13 个菜单对应的智能体'),
 (2013, '工作流编辑', 2012, 1, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:workflow:edit',  '#',        'admin', sysdate(), '', null, ''),
 (2014, '钻石管理',   2000, 6, 'diamond',  'wimo/diamond/index',   '', '', 1, 0, 'C', '0', '0', 'wimo:diamond:list',   'money',    'admin', sysdate(), '', null, '会员余额与钻石流水'),
 (2015, '钻石调整',   2014, 1, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:diamond:edit',   '#',        'admin', sysdate(), '', null, ''),
 (2016, '计费规则树', 2000, 8, 'priceTree','wimo/priceTree/index', '', '', 1, 0, 'C', '0', '0', 'wimo:capability:list','tree',     'admin', sysdate(), '', null, '能力→档位→模型→工作流 四级钻石配置树'),
 (2017, '规则树编辑', 2016, 1, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:capability:edit','#',        'admin', sysdate(), '', null, '');

delete from sys_dict_type where dict_type in ('wimo_task_status','wimo_task_group','wimo_model_capability','wimo_diamond_change','wimo_billing_unit','wimo_price_dimension');
delete from sys_dict_data where dict_type in ('wimo_task_status','wimo_task_group','wimo_model_capability','wimo_diamond_change','wimo_billing_unit','wimo_price_dimension');
insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values
 (100, '生成任务状态', 'wimo_task_status',      '0', 'admin', sysdate(), ''),
 (101, '功能分组',     'wimo_task_group',       '0', 'admin', sysdate(), ''),
 (102, '模型能力',     'wimo_model_capability', '0', 'admin', sysdate(), ''),
 (103, '钻石流水类型', 'wimo_diamond_change',   '0', 'admin', sysdate(), ''),
 (104, '计费单位',     'wimo_billing_unit',     '0', 'admin', sysdate(), ''),
 (105, '计费维度',     'wimo_price_dimension',  '0', 'admin', sysdate(), '');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values
 (1000, 1, '待生成',   '0', 'wimo_task_status', '', 'info',    'Y', '0', 'admin', sysdate(), ''),
 (1001, 2, '生成中',   '1', 'wimo_task_status', '', 'primary', 'N', '0', 'admin', sysdate(), ''),
 (1002, 3, '已完成',   '2', 'wimo_task_status', '', 'success', 'N', '0', 'admin', sysdate(), ''),
 (1003, 4, '失败',     '3', 'wimo_task_status', '', 'danger',  'N', '0', 'admin', sysdate(), ''),
 (1004, 5, '已取消',   '4', 'wimo_task_status', '', 'warning', 'N', '0', 'admin', sysdate(), ''),
 (1010, 1, '电商套图', 'ecommerce', 'wimo_task_group', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1011, 2, '模特相关', 'model',      'wimo_task_group', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1012, 3, '视频模块', 'video',      'wimo_task_group', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1019, 0, '文本生成', 'text2text',  'wimo_model_capability', '', 'info',    'N', '0', 'admin', sysdate(), ''),
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
 (1040, 1, '固定',     'flat',     'wimo_billing_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1041, 2, '按张',     'count',    'wimo_billing_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1042, 3, '按秒',     'second',   'wimo_billing_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1043, 4, '按千字',   'kiloword', 'wimo_billing_unit', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1050, 1, '图片清晰度',   'resolution',      'wimo_price_dimension', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1051, 2, '视频清晰度',   'videoResolution', 'wimo_price_dimension', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1052, 3, '画质',         'quality',         'wimo_price_dimension', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1053, 4, '时长',         'duration',        'wimo_price_dimension', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1054, 5, '字数',         'wordCount',       'wimo_price_dimension', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1055, 6, '语言',         'language',        'wimo_price_dimension', '', '', 'N', '0', 'admin', sysdate(), ''),
 (1056, 7, '模型',         'model',           'wimo_price_dimension', '', '', 'N', '0', 'admin', sysdate(), '');
