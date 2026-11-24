-- ============================================================
-- 映物 VIVID · 完整业务库结构（v3：能力树 / 价格节点）
-- 执行：mysql -uroot ry < wimo.sql
-- 警告：全量重建，会清空已有演示数据；已有库请改用 server/upgrade/wimo_upgrade_v3.sql
-- 表：会员(钻石) / 邮件日志 / 模板 / 能力层 / 价格节点 / 模型 / 工作流 / 任务 / 结果 / 钻石流水
-- 计费树：能力(wimo_capability) → 档位(wimo_price_node) / 模型(wimo_model) / 工作流(wimo_workflow)
-- ============================================================

-- ----------------------------
-- 1. 会员表（独立于若依 sys_user；钻石钱包直接内嵌，便于原子扣减）
--    diamond_balance 可用余额 / diamond_frozen 生成中冻结 / 累计字段用于看板
-- ----------------------------
drop table if exists wimo_user;
create table wimo_user (
  user_id          bigint       not null auto_increment comment '会员ID',
  email            varchar(120) not null               comment '邮箱（登录账号）',
  password_hash    varchar(120) not null               comment 'BCrypt 密码散列',
  nickname         varchar(60)  default ''             comment '昵称',
  status           char(1)      default '0'            comment '状态（0正常 1停用）',
  agreed           char(1)      default '0'            comment '是否同意用户协议',
  diamond_balance  bigint       default 0              comment '可用钻石余额',
  diamond_frozen   bigint       default 0              comment '冻结中钻石（任务进行中）',
  diamond_total    bigint       default 0              comment '累计获得钻石',
  diamond_used     bigint       default 0              comment '累计消耗钻石',
  last_login_time  datetime                            comment '最后登录时间',
  create_time      datetime                            comment '注册时间',
  update_time      datetime                            comment '更新时间',
  primary key (user_id),
  unique key uk_wimo_user_email (email)
) engine=innodb auto_increment=1 comment='映物会员表';

-- ----------------------------
-- 2. 邮件 / 验证码发送日志（邮件服务由用户自行配置，此处仅留审计）
-- ----------------------------
drop table if exists wimo_mail_log;
create table wimo_mail_log (
  id           bigint       not null auto_increment comment 'ID',
  email        varchar(120) not null               comment '收件邮箱',
  scene        varchar(30)  not null               comment '场景 register/login/reset',
  code         varchar(10)  default ''             comment '验证码（仅演示记录）',
  provider     varchar(30)  default 'mock'         comment '通道 mock/http/smtp',
  success      char(1)      default '1'            comment '是否成功',
  remark       varchar(500) default ''             comment '备注',
  create_time  datetime                            comment '发送时间',
  primary key (id),
  key idx_mail_email (email),
  key idx_mail_time (create_time)
) engine=innodb auto_increment=1 comment='验证码邮件日志';

-- ----------------------------
-- 3. 功能模板配置（13 个功能的表单 schema，管理端可编辑）
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
-- 3A. 能力层（计费规则树的树根：文本生成/文生图/图生图/图生视频/文生视频）
--     每个能力分支有独立的基础价/计费单位/单价/价格区间，工作流可继承或覆盖
--     capability_key 与 wimo_model.capability、wimo_workflow.capability 对齐
-- ----------------------------
drop table if exists wimo_capability;
create table wimo_capability (
  capability_id  bigint       not null auto_increment comment '能力ID',
  capability_key varchar(30)  not null               comment '能力唯一标识 text2text/text2img/img2img/img2video/text2video',
  name_zh        varchar(60)  default ''             comment '中文名',
  name_en        varchar(60)  default ''             comment '英文名',
  icon           varchar(30)  default ''             comment '图标',
  base_cost      bigint       default 10             comment '该能力分支的基础钻石（每一支不同）',
  unit           varchar(20)  default 'count'        comment '计费单位 count/second/kiloword/flat',
  unit_cost      decimal(8,2) default 0.00           comment '每单位钻石（视频按秒/文案按千字）',
  unit_field     varchar(40)  default ''             comment '取单位数的字段 duration/resultCount/wordCount',
  min_cost       bigint       default 1              comment '单次最低消耗',
  max_cost       bigint       default 2000           comment '单次最高消耗',
  default_model  varchar(60)  default ''             comment '默认模型 key',
  enabled        char(1)      default '1'            comment '是否启用 1/0',
  sort_no        int          default 0              comment '排序',
  remark         varchar(255) default ''             comment '备注',
  create_time    datetime                            comment '创建时间',
  update_time    datetime                            comment '更新时间',
  primary key (capability_id),
  unique key uk_wimo_capability_key (capability_key)
) engine=innodb auto_increment=1 comment='能力层（计费规则树根）';

-- ----------------------------
-- 3B. 价格节点（计费规则树：能力下的维度档位，parent_id 自关联成树）
--     node_type: group 维度分组 / option 具体档位
--     dimension: resolution/videoResolution/quality/duration/wordCount/language/model...
--     option 节点带 factor 倍率；数值型维度(字数)用 min_value/max_value 区间匹配
--     管理端逐节点调价即时生效；改一个 4K 档即可影响该能力全部工作流
-- ----------------------------
drop table if exists wimo_price_node;
create table wimo_price_node (
  node_id         bigint       not null auto_increment comment '节点ID',
  parent_id       bigint       default 0               comment '父节点ID（0=能力根下的维度分组）',
  capability_key  varchar(30)  not null               comment '所属能力',
  node_type       varchar(20)  default 'option'       comment 'group 维度分组 / option 档位 / model 模型倍率',
  dimension       varchar(40)  default ''             comment '维度 key',
  node_key        varchar(60)  default ''             comment '节点唯一 key（能力:维度:档位）',
  option_value    varchar(40)  default ''             comment '档位值（提交参数匹配此值）',
  label_zh        varchar(80)  default ''             comment '中文标签',
  label_en        varchar(80)  default ''             comment '英文标签',
  factor          decimal(8,3) default 1.000          comment '钻石倍率',
  unit_cost       decimal(8,2) default null           comment '档位级单价覆盖（可选，优先于能力单价）',
  min_value       bigint       default null           comment '数值维度区间下界（含）',
  max_value       bigint       default null           comment '数值维度区间上界（含）',
  enabled         char(1)      default '1'            comment '是否启用 1/0',
  sort_no         int          default 0              comment '排序',
  remark          varchar(255) default ''             comment '备注',
  create_time     datetime                            comment '创建时间',
  update_time     datetime                            comment '更新时间',
  primary key (node_id),
  unique key uk_wimo_price_node_key (node_key),
  key idx_price_node_cap (capability_key, enabled),
  key idx_price_node_dim (capability_key, dimension, enabled)
) engine=innodb auto_increment=1 comment='计费价格节点（能力树档位）';

-- ----------------------------
-- 4. AI 模型注册表（管理端配置，web 端按能力动态拉取可选模型）
--    capability: text2text 文本生成 / text2img 文生图 / img2img 图生图 / img2video 图生视频 / text2video 文生视频
-- ----------------------------
drop table if exists wimo_model;
create table wimo_model (
  model_id         bigint       not null auto_increment comment '模型ID',
  model_key        varchar(60)  not null               comment '模型唯一标识（提交任务时传）',
  name_zh          varchar(80)  default ''             comment '中文名',
  name_en          varchar(80)  default ''             comment '英文名',
  capability       varchar(30)  not null               comment '能力 text2img/img2img/img2video/text2video',
  provider         varchar(30)  default 'mock'         comment '供应商 mock/remote/qiniu/...',
  endpoint         varchar(300) default ''             comment '模型网关 endpoint',
  credential_ref   varchar(120) default ''             comment '密钥引用名（不明文存库）',
  cost_multiplier  decimal(6,2) default 1.00           comment '钻石计费倍率',
  config_json      longtext                            comment '默认参数 JSON（尺寸/时长上限/支持清晰度等）',
  enabled          char(1)      default '1'            comment '是否启用 1/0',
  sort_no          int          default 0              comment '排序',
  remark           varchar(255) default ''             comment '备注',
  create_time      datetime                            comment '创建时间',
  update_time      datetime                            comment '更新时间',
  primary key (model_id),
  unique key uk_wimo_model_key (model_key),
  key idx_model_cap (capability, enabled)
) engine=innodb auto_increment=1 comment='AI 模型注册表';

-- ----------------------------
-- 5. 工作流 / 智能体（一个菜单 = 一个智能体 = 一份计费规则）
--    所有任务共用统一引擎，差异全部由本表驱动：能力、可用模型、基础价、倍率、步骤
--    billing_rule JSON 结构见 docs/LOCAL_RUN.md「计费规则」章节
-- ----------------------------
drop table if exists wimo_workflow;
create table wimo_workflow (
  workflow_id   bigint       not null auto_increment comment '工作流ID',
  workflow_key  varchar(50)  not null               comment '= wimo_schema.task_key',
  name_zh       varchar(60)  default ''             comment '中文名',
  name_en       varchar(60)  default ''             comment '英文名',
  group_key     varchar(30)  default 'ecommerce'    comment '分组',
  capability    varchar(30)  default 'img2img'      comment '主能力（决定可选模型池）',
  allowed_models varchar(500) default ''            comment '允许的模型 key，逗号分隔；空=该能力下全部启用模型',
  default_model varchar(60)  default ''             comment '默认模型 key',
  base_cost     bigint       default 10             comment '基础钻石',
  unit          varchar(20)  default 'count'        comment '计费单位 count(张)/second(秒)/flat(固定)',
  unit_cost     decimal(8,2) default 0.00           comment '每单位钻石',
  unit_field    varchar(40)  default ''             comment '取单位数的字段（视频=duration，图片=resultCount）',
  multipliers   longtext                            comment '维度倍率 JSON：{videoResolution:{720p:1.4,...},quality:{...}}',
  min_cost      bigint       default 1              comment '单次最低消耗',
  max_cost      bigint       default 2000           comment '单次最高消耗',
  cost_source   varchar(20)  default 'override'     comment '价格来源 override 用工作流自身 / inherit 继承能力层',
  steps_json    longtext                            comment '智能体执行步骤 JSON 数组（展示与编排用）',
  enabled       char(1)      default '1'            comment '是否启用 1/0',
  sort_no       int          default 0              comment '排序',
  remark        varchar(255) default ''             comment '备注',
  create_time   datetime                            comment '创建时间',
  update_time   datetime                            comment '更新时间',
  primary key (workflow_id),
  unique key uk_wimo_workflow_key (workflow_key)
) engine=innodb auto_increment=1 comment='工作流 / 智能体定义';

-- ----------------------------
-- 6. 任务结果文件（一任务多结果，图片/视频均可）
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
-- 7. 生成任务表（含钻石计费快照，规则改动不影响历史账单）
--    diamond_state: 0 已冻结待结算 / 1 已扣减 / 2 已退还 / 9 不计费（匿名演示）
-- ----------------------------
drop table if exists wimo_task;
create table wimo_task (
  task_id           bigint       not null auto_increment comment '任务ID',
  user_id           bigint       default 0               comment '会员ID（0=匿名演示，不扣钻石）',
  task_type         varchar(50)  default 'productSet'   comment '功能 key',
  workflow_id       bigint       default 0              comment '关联工作流/智能体',
  title             varchar(200) default ''             comment '任务标题',
  product_name      varchar(200) default ''             comment '商品/店铺名称',
  selling_points    text                                comment '卖点与要求（兼容旧字段）',
  ratio             varchar(20)  default '3:4'          comment '画面比例',
  platform          varchar(30)  default ''             comment '平台',
  region            varchar(30)  default ''             comment '国家/地区',
  lang              varchar(20)  default ''             comment '语言',
  resolution        varchar(20)  default ''             comment '清晰度',
  quality           varchar(20)  default ''             comment '质量',
  model             varchar(60)  default ''             comment '模型 key',
  model_key         varchar(60)  default ''             comment '计费采用的模型 key（快照）',
  cost_diamonds     bigint       default 0              comment '本单消耗钻石',
  billing_snapshot  longtext                            comment '计费明细快照 JSON（可追溯）',
  diamond_state     char(1)      default '9'            comment '0冻结 1已扣 2已退 9不计费',
  result_mode       varchar(20)  default 'default'      comment '结果配置',
  images            text                                comment '素材 URL 列表（逗号分隔）',
  payload           longtext                            comment '完整参数 JSON（uploads/values/common）',
  status            char(1)      default '0'            comment '0待生成 1生成中 2完成 3失败 4已取消',
  progress          int          default 0              comment '进度 0-100',
  results           text                                comment '结果 URL（逗号分隔，兼容旧字段）',
  extra             text                                comment '扩展参数（兼容旧字段）',
  result_count      int          default 0              comment '结果数量',
  retry_count       int          default 0              comment '重试次数',
  cost_ms           bigint       default 0              comment '耗时毫秒',
  error_msg         varchar(500) default ''             comment '失败原因',
  finish_time       datetime                            comment '完成时间',
  create_time       datetime                            comment '创建时间',
  update_time       datetime                            comment '更新时间',
  primary key (task_id),
  key idx_task_user (user_id),
  key idx_task_type (task_type),
  key idx_task_status (status),
  key idx_task_billing (diamond_state),
  key idx_task_create (create_time)
) engine=innodb auto_increment=1 comment='AI 生成任务表';

-- ----------------------------
-- 8. 钻石流水（每一次余额/冻结变动都留痕，管理端可审计）
--    change_type: gift 注册赠送 / recharge 充值 / freeze 预扣 / consume 扣减 / refund 退还 / adjust 管理端调整
-- ----------------------------
drop table if exists wimo_diamond_log;
create table wimo_diamond_log (
  id              bigint       not null auto_increment comment '流水ID',
  user_id         bigint       not null               comment '会员ID',
  change_type     varchar(30)  not null               comment 'gift/recharge/freeze/consume/refund/adjust',
  amount          bigint       not null               comment '变动钻石（带符号）',
  balance_before  bigint       default 0              comment '变动前可用余额',
  balance_after   bigint       default 0              comment '变动后可用余额',
  frozen_before   bigint       default 0              comment '变动前冻结额',
  frozen_after    bigint       default 0              comment '变动后冻结额',
  task_id         bigint       default null           comment '关联任务',
  workflow_key    varchar(50)  default ''            comment '关联智能体',
  operator        varchar(60)  default 'system'      comment '操作人',
  remark          varchar(300) default ''            comment '备注',
  create_time     datetime                            comment '发生时间',
  primary key (id),
  key idx_diamond_user (user_id, create_time),
  key idx_diamond_type (change_type),
  key idx_diamond_task (task_id)
) engine=innodb auto_increment=1 comment='钻石账户流水';

-- ----------------------------
-- 9. 管理端菜单（2000-2099，避免与若依内置冲突）
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
 (2009, '模型管理',   2000, 4, 'model',    'wimo/model/index',     '', '', 1, 0, 'C', '0', '0', 'wimo:model:list',     'server',   'admin', sysdate(), '', null, '文生图/图生图/图生视频/文生视频模型'),
 (2010, '模型编辑',   2009, 1, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:model:edit',     '#',        'admin', sysdate(), '', null, ''),
 (2011, '模型删除',   2009, 2, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:model:remove',   '#',        'admin', sysdate(), '', null, ''),
 (2012, '智能体工作流',2000, 5, 'workflow','wimo/workflow/index',  '', '', 1, 0, 'C', '0', '0', 'wimo:workflow:list',  'tree',     'admin', sysdate(), '', null, '13 个菜单对应的智能体与计费规则'),
 (2013, '工作流编辑', 2012, 1, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:workflow:edit',  '#',        'admin', sysdate(), '', null, ''),
 (2014, '钻石管理',   2000, 6, 'diamond',  'wimo/diamond/index',   '', '', 1, 0, 'C', '0', '0', 'wimo:diamond:list',   'money',    'admin', sysdate(), '', null, '会员余额与钻石流水'),
 (2015, '钻石调整',   2014, 1, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:diamond:edit',   '#',        'admin', sysdate(), '', null, ''),
 (2016, '计费规则树', 2000, 8, 'priceTree','wimo/priceTree/index', '', '', 1, 0, 'C', '0', '0', 'wimo:capability:list','tree',     'admin', sysdate(), '', null, '能力→档位→模型→工作流 四级钻石配置树'),
 (2017, '规则树编辑', 2016, 1, '#',        '',                      '', '', 1, 0, 'F', '0', '0', 'wimo:capability:edit','#',        'admin', sysdate(), '', null, '');

-- ----------------------------
-- 10. 字典
-- ----------------------------
delete from sys_dict_type where dict_type in ('wimo_task_status','wimo_task_group','wimo_model_capability','wimo_diamond_change','wimo_billing_unit','wimo_price_dimension');
delete from sys_dict_data where dict_type in ('wimo_task_status','wimo_task_group','wimo_model_capability','wimo_diamond_change','wimo_billing_unit','wimo_price_dimension');
insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values
 (100, '生成任务状态', 'wimo_task_status',      '0', 'admin', sysdate(), '映物任务状态'),
 (101, '功能分组',     'wimo_task_group',       '0', 'admin', sysdate(), '映物功能分组'),
 (102, '模型能力',     'wimo_model_capability', '0', 'admin', sysdate(), '文本生成/文生图/图生图/图生视频/文生视频'),
 (103, '钻石流水类型', 'wimo_diamond_change',   '0', 'admin', sysdate(), '钻石账户变动类型'),
 (104, '计费单位',     'wimo_billing_unit',     '0', 'admin', sysdate(), '工作流计费单位'),
 (105, '计费维度',     'wimo_price_dimension',  '0', 'admin', sysdate(), '清晰度/时长/画质/字数/语言/模型');
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
