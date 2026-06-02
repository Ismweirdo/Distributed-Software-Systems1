# Distributed Software Systems — AI 即时通讯系统

> 分布式软件系统课程项目 | 6 人协作开发  
> 仓库地址：https://github.com/Ismweirdo/Distributed-Software-Systems1

## 项目简介

本项目是一个**带有 AI Bot 化身能力的即时通讯系统**，用户可导入 QQ 聊天记录自动生成具有特定人物语言风格的 AI 机器人，支持多级记忆（短期/长期/RAG 检索）与流式对话。系统采用前后端分离架构，支持 Docker 容器化部署，面向 Redis / MySQL / RabbitMQ 的分布式演进。

**核心特色：**

- 完整的 IM 功能：注册登录、好友管理、群组管理、私聊/群聊、消息已读/撤回
- WebSocket 实时通信：STOMP 协议，支持内置 Broker 与 RabbitMQ Relay 双模式
- AI Bot 化身：聊天记录导入 → 技能蒸馏 → LLM 驱动的个性化回复
- 多级记忆系统：工作记忆 → 短期缓存 → 长期存储 → RAG 向量检索
- 流式对话：SSE 协议，前端实时渲染打字效果
- 容器化部署：Docker Compose 一键启动全部服务

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 (Composition API) + Vite + Element Plus + Pinia + Vue Router |
| 后端 | Spring Boot 3 + MyBatis-Plus + Spring Security + JWT (HS256) |
| 实时通信 | WebSocket STOMP (内置 Broker + RabbitMQ STOMP Relay) |
| 数据库 | MySQL 8.4 + Redis 7 + RabbitMQ 3 |
| AI 集成 | OpenAI 兼容 API (DeepSeek / OpenAI / 自定义 LLM) |
| 部署 | Docker Compose + Nginx |
| 测试 | Python 压力测试脚本 (Locust 风格) |

---

## 项目结构

```
.
├── chatroom-client/             前端工程 (Vue 3 + Vite)
│   ├── src/
│   │   ├── api/                 API 封装 (auth/bot/friend/group/message/user)
│   │   ├── components/          Vue 组件 (16 个)
│   │   ├── router/              路由配置 + 鉴权守卫
│   │   ├── store/               Pinia 状态管理 (chat/contact/user)
│   │   ├── utils/               工具函数 (websocket/auth)
│   │   └── views/               页面 (Login/Register/Chat)
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── package.json
│   └── vite.config.js
│
├── chatroom-server/             后端工程 (Spring Boot)
│   ├── src/main/java/com/chatroom/
│   │   ├── common/              公共类 (Constants/Result)
│   │   ├── config/              配置 (WebSocket/Security/CORS/MyBatisPlus/...)
│   │   ├── controller/          控制器 (Auth/Bot/File/Friend/Group/Message/User)
│   │   ├── exception/           自定义异常
│   │   ├── file/                文件存储服务
│   │   ├── handler/             全局异常处理 + 自动填充
│   │   ├── mapper/              MyBatis-Plus Mapper (13 个)
│   │   ├── model/               数据模型 (entity/dto/vo)
│   │   ├── security/            安全模块 (JWT/登录保护/请求追踪)
│   │   ├── service/             业务服务 (18 个)
│   │   └── websocket/           WebSocket 处理器
│   ├── src/main/resources/
│   │   ├── sql/                 schema.sql (11 张表) + data.sql
│   │   └── application.yml      全量配置
│   ├── Dockerfile
│   └── pom.xml
│
├── data/skills/                 AI 角色技能文件目录
├── deploy/rabbitmq/             RabbitMQ 插件配置
├── docs/                        项目文档
│   ├── README.md                文档索引
│   ├── memory-system-design.md  记忆系统设计
│   ├── test-design.md           测试方案
│   ├── test-results.md          测试结果
│   └── 人员分工文档.md           分工文档
├── test/                        测试脚本与样本数据
│   ├── chatroom-stress-test.py  聊天室全链路压测
│   ├── bot-stress-test.py       Bot 并发压测
│   ├── load-test.py             负载测试
│   ├── max-qps-test.py          最大 QPS 测试
│   ├── test-bots.sh/.bat        Bot 集成测试
│   ├── setup-deepseek-bots.sh/.bat  DeepSeek 一键部署
│   ├── test-bots-ws.py          WebSocket 测试
│   └── sample-*                 测试样本数据
├── docker-compose.yml           五服务编排部署
└── README.md
```

---

## 功能列表

### 基础 IM 功能
- 用户注册 / 登录 / JWT 鉴权 / 个人资料管理 / 账户注销
- 好友管理：添加 / 接受 / 拒绝 / 删除 / 备注 / 在线状态
- 群组管理：创建 / 解散 / 踢人 / 转让群主 / 公告编辑
- 私聊与群聊消息收发
- 消息状态追踪：已发送 → 已送达 → 已读
- 消息撤回（2 分钟内）、永久删除、清空聊天记录
- 消息引用回复
- 聊天背景自定义

### AI Bot 化身系统
- QQ 聊天记录导入（支持 TXT / JSON / JSONL 格式）
- 技能蒸馏：自动提取语言风格 / 情绪特征 / 表达模式
- SKILL.md 技能文档导入与角色创建
- 多 AI 平台支持：DeepSeek / OpenAI / 自定义 OpenAI Compatible API
- Bot 全生命周期管理：创建 / 启动 / 停止 / 删除 / 配置
- 流式对话：SSE 逐 Token 推送，前端打字效果
- Bot 群组自动聊天（可配置间隔）
- 熔断保护：连续错误自动静默

### AI 记忆系统
- 工作记忆：最近 5 轮对话 (≤3000 字符/轮)
- 短期记忆：Redis List 滑动窗口 (最多 30 条)
- 长期记忆：MySQL 持久化，三种类型 (summary / fact / preference)，重要性评分 1-5
- RAG 检索：向量嵌入 + 余弦相似度 Top-K 检索
- 自动记忆巩固：短期记忆超阈值触发 LLM 摘要归档

### 系统特性
- 登录保护：IP 限流 + 失败计数 + 账号锁定 (Caffeine + Redis 双层降级)
- 请求追踪：全链路 traceId (MDC + 响应头)
- 文件上传：路径遍历防护 + 类型/大小校验
- 消息可靠性：Outbox Pattern 保证最终一致性
- 运行时指标：Micrometer + JVM 监控
- 定时清理：历史消息 + 过期文件 + 失败发件箱记录

---

## 团队成员与分工

| 成员 | GitHub | 邮箱 | 主要贡献 |
|------|--------|------|----------|
| eeeeeeeex1 | eeeeeeeex1 | 2629620478@qq.com | 项目脚手架、JWT 认证系统、LLM 客户端、RAG 检索、AI 提供商预设、Bot 消息队列 |
| Ismweirdo | Ismweirdo | 2074437070@qq.com | WebSocket 实时通信、聊天界面、聊天记录导入、QQ 导出解析、对话缓存 |
| Jungle-Cristo | Jungle-Cristo | jungle920@qq.com | 消息持久化、发件箱模式、登录安全保护、网关文件存储、Docker 部署 |
| Jay7982024 | Jay7982024 | 1731614640@qq.com | 社交功能后端（好友/群组/用户）、AI 技能蒸馏、角色导入、项目文档 |
| Yuanxiaelf | Yuanxiaelf | 1056606933@qq.com | 社交管理前端、Bot 核心引擎、Bot REST API、Bot 基准测试 |
| kuuzzzzzzzzzz | kuuzzzzzzzzzz | kuuzzzz@163.com | 数据库设计、公共基础设施、联系人前端、长期记忆系统、Bot 测试套件 |

> 详细分工见 [docs/人员分工文档.md](docs/人员分工文档.md)

---

## 本地开发

### 环境要求
- JDK 17+
- Maven 3.9+
- Node.js 18+
- MySQL 8.0+
- Redis 7.0+
- (可选) RabbitMQ 3.x

### 数据库初始化

```bash
mysql -u root -p < chatroom-server/src/main/resources/sql/schema.sql
mysql -u root -p < chatroom-server/src/main/resources/sql/data.sql
```

### 后端启动

```bash
cd chatroom-server
# 修改 src/main/resources/application.yml 中的数据源/Redis 连接信息
mvn spring-boot:run
```

可通过环境变量覆盖配置：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `MYSQL_HOST` | MySQL 主机 | localhost |
| `MYSQL_PORT` | MySQL 端口 | 3306 |
| `MYSQL_DATABASE` | 数据库名 | chatroom |
| `MYSQL_USERNAME` | 数据库用户 | root |
| `MYSQL_PASSWORD` | 数据库密码 | root123 |
| `REDIS_HOST` | Redis 主机 | localhost |
| `REDIS_PORT` | Redis 端口 | 6379 |
| `CHATROOM_WEBSOCKET_MODE` | WebSocket 模式 | simple (内置) / relay (RabbitMQ) |

### 前端启动

```bash
cd chatroom-client
npm install
npm run dev
```

### 构建

```bash
# 后端
cd chatroom-server && mvn -DskipTests package

# 前端
cd chatroom-client && npm run build
```

---

## Docker 部署

一条命令启动全部服务：

```bash
docker-compose up -d
```

启动 5 个容器服务：

| 服务 | 端口 | 说明 |
|------|------|------|
| mysql | 3306 | MySQL 8.4，含健康检查 |
| redis | 6379 | Redis 7 Alpine，含健康检查 |
| rabbitmq | 5672/61613/15672 | RabbitMQ 3 Management，STOMP 插件 |
| chatroom-server | 8080 | Spring Boot 后端 |
| chatroom-client | 80 | Nginx 前端，反向代理 API + WebSocket |

依赖关系：chatroom-server 等待 mysql / redis / rabbitmq 健康检查通过后启动，chatroom-client 等待 chatroom-server 后启动。

---

## API 概览

| 模块 | 路径前缀 | 端点数 | 说明 |
|------|----------|--------|------|
| 认证 | `/api/auth` | 2 | 注册 / 登录 |
| 用户 | `/api/users` | 4 | 资料查询修改 / 搜索 / 注销 |
| 好友 | `/api/friends` | 7 | 添加 / 接受 / 拒绝 / 删除 / 列表 / 备注 |
| 群组 | `/api/groups` | 7 | 创建 / 列表 / 详情 / 解散 / 踢人 / 转让 / 公告 |
| 消息 | `/api/messages` | 7 | 私聊历史 / 群聊历史 / 撤回 / 删除 / 清空 / 上下文 |
| Bot | `/api/bots` | 20+ | CRUD / 配置 / 导入 / 流式 / 基准测试 / 活跃模式 |
| 文件 | `/api/files` | 3 | 上传 / 下载 / 公开访问 |

---

## 数据库设计

共 11 张表：

| 表名 | 说明 |
|------|------|
| `users` | 用户表（含 is_bot 区分人类和AI） |
| `friends` | 好友关系表（双向唯一索引） |
| `groups` | 群组表 |
| `group_members` | 群组成员表 |
| `messages` | 消息表（私聊/群聊/引用/状态） |
| `message_outbox` | 发件箱表（事件驱动异步通知） |
| `bot_skills` | Bot 技能配置表（SystemPrompt/情绪/风格） |
| `bot_active_modes` | Bot 活跃模式表（群组自动发言） |
| `group_bot_auto_chat` | 群组-Bot 自动聊天关联 |
| `bot_long_term_memory` | Bot 长期记忆表（summary/fact/preference） |
| `conversation_embeddings` | 会话嵌入向量表（RAG 检索） |

---

## 测试

### 压力测试

```bash
# 聊天室全链路压测 (注册→登录→WebSocket→消息收发)
python test/chatroom-stress-test.py

# Bot 并发压测
python test/bot-stress-test.py

# 渐进式负载测试
python test/load-test.py

# 最大 QPS 测试
python test/max-qps-test.py
```

### Bot 集成测试

```bash
# Linux / Mac
bash test/test-bots.sh
bash test/setup-deepseek-bots.sh

# Windows
test/test-bots.bat
test/setup-deepseek-bots.bat
```

---

## 许可证

本项目为教育用途的课程项目。
