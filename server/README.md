# server/ · 若依后端（RuoYi-Vue）

下一阶段实现。规划内容：

- 基于 RuoYi-Vue（Spring Boot 2.7 + MyBatis + Redis + MySQL）二次开发
- 业务模块 `wimo-module`：
  - `product_set_task` 套图任务表（用户、素材、参数 JSON、状态、结果集）
  - `gen_template` 出图模板字典（白底主图 / 卖点图 / 场景图 / A+ 横幅…）
  - `gen_record` 生成记录与额度扣减
- 对用户端开放接口（挂 `/api/v1/**`，走 Sa-Token/JWT 鉴权）：
  - `POST /api/v1/product-set/task` 创建任务
  - `GET  /api/v1/product-set/task/{id}` 轮询状态
  - `GET  /api/v1/product-set/records` 生成记录

> 获取官方源码：`git clone https://gitee.com/y_project/RuoYi-Vue.git`
