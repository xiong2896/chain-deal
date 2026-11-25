# 链享团购 ChainDeal · 本地生活到店服务平台

<p align="center">
  <b>Spring Boot + MyBatis-Plus + Redis + RabbitMQ + MySQL</b><br/>
  按距离发现门店 · 限量套餐抢购 · 到店核销 · 会员成长
</p>

---

## 项目简介

面向**本地生活到店消费**场景的后端服务：用户在附近发现门店、抢购限量套餐、到店核销并获得会员成长值，商家侧管理门店与套餐库存、处理核销与评价。

系统的技术重点不在 CRUD，而在**高并发场景下的一致性保障**：

- 门店详情在峰值流量下的缓存穿透、击穿与雪崩防护
- 限量套餐抢购的超卖与超卖后的幂等处理
- Nginx 水平扩展后，登录态与业务一致性如何保持

---

## 技术栈

| 层次 | 选型 |
|---|---|
| 语言 / 运行时 | Java 8 |
| 框架 | Spring Boot 2.x、Spring MVC、MyBatis-Plus |
| 存储 | MySQL 8.0（InnoDB）、Redis 6（Lettuce 连接池） |
| 消息 | RabbitMQ（异步削峰、死信队列） |
| 前端 | 原生 HTML / CSS / JavaScript（无框架，Nginx 反向代理） |
| 其他 | Redisson（分布式锁）、Hutool、Lombok、Nginx |

---

## 核心功能

### 用户侧

| 功能 | 说明 |
|---|---|
| 短信验证码登录 | 验证码存 Redis 并设置 TTL，登录后返回随机 token，token 存 Redis 实现无状态会话 |
| 门店发现 | 按分类分页查询门店；按经纬度做距离排序（Redis GEO） |
| 门店详情 | 高并发查询，Cache-Aside + 逻辑过期 + 空值缓存 |
| 限量套餐抢购 | Lua 脚本原子校验并扣减库存，异步下单，一人一单 |
| 内容笔记 | 发布 / 查看笔记，点赞基于 Redis ZSet |
| 关注与推送 | 关注用户，收件箱推送（写扩散），滚动分页 |
| 签到 | Redis BitMap 实现按月签到统计 |

### 技术要点（面试常问）

- **缓存三件套**：穿透用空值缓存 + 短 TTL；击穿用逻辑过期 + 互斥重建；雪崩用过期时间加随机扰动
- **秒杀链路**：Lua 原子扣减 → 判断库存与一人一单 → RabbitMQ 异步创建订单 → 唯一索引兜底幂等
- **分布式锁**：Redisson `tryLock` 带看门狗续期，避免锁超时后业务未完成
- **登录态**：token 存 Redis，拦截器解析并刷新 TTL，支持多实例水平扩展

---

## 目录结构

```
chain-deal
├── src/main/java/chaindeal
│   ├── config        # 配置类（MVC、MyBatis-Plus、Redisson）
│   ├── controller    # 接口层
│   ├── dto           # 传输对象
│   ├── entity        # 实体
│   ├── mapper        # 数据访问（含 XML）
│   ├── service       # 业务层
│   │   └── impl
│   └── utils         # 工具（RedisIdWorker、拦截器、正则、常量）
├── src/main/resources
│   ├── db            # 建库建表脚本
│   ├── mapper        # MyBatis XML
│   └── seckkill.lua  # 秒杀原子脚本
├── frontend          # 前端静态页面（原生 HTML/CSS/JS）
│   ├── index.html    # 门店列表
│   ├── shop.html     # 门店详情与套餐抢购
│   ├── login.html    # 登录
│   ├── profile.html  # 个人中心与签到
│   ├── imgs          # 门店封面（程序生成的 SVG）
│   └── assets        # 公共样式与请求封装
└── deploy            # 部署示例（Nginx 反向代理配置）
```

---

## 接口概览

| 模块 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 用户 | POST | `/user/code` | 发送验证码 |
| 用户 | POST | `/user/login` | 登录并返回 token |
| 用户 | POST | `/user/logout` | 退出登录 |
| 用户 | GET | `/user/me` | 当前登录用户 |
| 用户 | GET | `/user/info/{id}` | 查看他人主页 |
| 用户 | PUT | `/user/sign` | 签到 |
| 用户 | GET | `/user/sign/count` | 本月连续签到天数 |
| 门店 | GET | `/shop-type/list` | 门店分类 |
| 门店 | GET | `/shop/of/type` | 按分类分页查询 |
| 门店 | GET | `/shop/{id}` | 门店详情 |
| 门店 | GET | `/shop/of/name` | 按名称搜索 |
| 门店 | POST | `/shop` | 新增门店 |
| 门店 | PUT | `/shop` | 更新门店 |
| 套餐 | GET | `/voucher/list/{shopId}` | 门店套餐列表 |
| 套餐 | POST | `/voucher` | 新增普通套餐 |
| 套餐 | POST | `/voucher/seckill` | 新增秒杀套餐（带库存） |
| 抢购 | POST | `/voucher-order/seckill/{id}` | 抢购套餐 |
| 笔记 | GET | `/blog/hot` | 热门笔记 |
| 笔记 | GET | `/blog/{id}` | 笔记详情 |
| 笔记 | POST | `/blog` | 发布笔记 |
| 笔记 | PUT | `/blog/like/{id}` | 点赞 / 取消点赞 |
| 笔记 | GET | `/blog/of/me` | 我的笔记 |
| 笔记 | GET | `/blog/likes/{id}` | 点赞该笔记的用户 |
| 上传 | POST | `/upload/blog` | 上传笔记图片 |
| 关注 | PUT | `/follow/{id}/{isFollow}` | 关注 / 取关 |
| 关注 | GET | `/follow/or/not/{id}` | 是否关注 |
| 关注 | GET | `/follow/common/{id}` | 共同关注 |

> 统一响应结构：`{ "success": true, "errorMsg": null, "data": ..., "total": ... }`

---

## 快速开始

### 1. 环境要求

- JDK 8+
- Maven 3.6+
- MySQL 8.0
- Redis 6+
- RabbitMQ 3.8+（可选，仅抢购异步链路需要）

### 2. 初始化数据库

```bash
mysql -u root -p -e "CREATE DATABASE chaindeal DEFAULT CHARSET utf8mb4;"
mysql -u root -p chaindeal < src/main/resources/db/chaindeal.sql
```

### 3. 配置本地参数

仓库中的 `application.yaml` 已脱敏，密码通过环境变量注入。本地开发请复制模板：

```bash
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
```

然后填入自己的 MySQL / Redis 密码（该文件已被 `.gitignore` 排除，不会提交）：

```yaml
spring:
  datasource:
    username: root
    password: 你的密码
  redis:
    password: 你的密码
```

也可以直接用环境变量启动：

```bash
export MYSQL_PASSWORD=你的密码
export REDIS_PASSWORD=你的密码
mvn spring-boot:run
```

### 4. 启动

```bash
mvn spring-boot:run
```

访问 `http://localhost:8081/shop-type/list` 应返回分类列表。

### 5. 启动前端（可选）

前端是零依赖的静态页面，用任意静态服务器 + 反向代理即可：

```bash
# 1. 把 frontend 目录交给 Nginx（示例配置见 deploy/nginx.conf.example）
# 2. 修改其中的 root 为你本机 frontend 目录路径
nginx -c /你的路径/nginx.conf
```

打开 `http://localhost:8080`，页面里的 `/api/*` 请求会被代理到 `8081`。

> 登录验证码不发短信，由 `UserServiceImpl.sendCode` 打印到后端控制台，登录时填入即可。

### 6. 抢购前的库存预热

秒杀库存放在 Redis（`seckill:stock:{voucherId}`），需要与 `tb_seckill_voucher.stock` 保持一致，否则 Lua 脚本会在 `get` 到空值时抛错：

```bash
# 示例：为 11 号券预热 100 份库存
redis-cli -a 你的密码 SET seckill:stock:11 100
```

---

## 后续规划

- [ ] 门店评价 AI 摘要：对近 30 天评价按「口味 / 服务 / 环境」三维度生成摘要，按门店维度缓存 24 小时并异步刷新
- [ ] 自然语言找店：结构化条件抽取 + 向量召回 + 距离排序
- [ ] 差评预警：语义判别命中风险标签后生成处置建议

---

## License

MIT
