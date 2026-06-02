# Class-QQ-Instant-Messaging-AI-Avatar

`integration-six-layer` 是当前仓库的主集成分支。

这个分支已经从最初的“六层协作拆分视图”重新整理回标准的可运行工程结构，同时在 Git 历史中保留了 6 位成员各自负责层的提交记录，便于后续继续开发、展示分工以及查看 contribution。

## 项目简介

本项目是一个带有 AI 角色对话能力的即时通讯系统，整体包含以下核心内容：

- Vue 3 前端聊天客户端
- Spring Boot 后端服务
- 基于 WebSocket 的实时通信能力
- Bot 编排与 AI 对话能力
- 长短期记忆与 RAG 相关能力
- 面向 Redis / MySQL / RabbitMQ 的分布式演进支持
- Docker 化部署文件与测试脚本

当前仓库适合用于：

- 小组协作开发
- 展示分支分工和 contribution
- 本地构建与运行验证
- 后续继续向更稳定的主线分支演进

## 当前分支定位

`integration-six-layer` 是当前仓库的主工作分支，也是六个成员分支汇总后的集成分支。

这个分支主要用于：

- 合并六个成员各自负责的层分支
- 将六层拆分结构恢复成标准可运行结构
- 作为后续统一开发与联调的基础分支
- 作为后续查看整体 contribution 的主要分支

此前临时创建过 `main` 分支用于同步集成结果，但现在远程 `main` 已经删除，仓库当前应以 `integration-six-layer` 为主。

## 功能概览

当前集成后的代码已经包含以下主要功能：

- 用户注册、登录、JWT 鉴权、个人资料管理
- 好友管理与群组管理
- 私聊与群聊消息收发
- 消息持久化与消息状态同步
- WebSocket 实时通信
- 在线状态管理
- Bot 会话、主动模式、自动回复编排
- 记忆缓存、长期记忆、检索增强相关能力
- Docker、Nginx、Redis、MySQL、RabbitMQ 相关部署支持
- 后端运行指标与可观测性相关配置

## 仓库结构

当前仓库已经恢复为标准可运行结构：

```text
.
├─ chatroom-client/     前端工程（Vue 3 + Vite）
├─ chatroom-server/     后端工程（Spring Boot）
├─ data/                技能与记忆相关种子数据
├─ deploy/              部署与中间件辅助文件
├─ docs/                设计文档与测试文档
├─ test/                压测脚本与示例测试数据
├─ docker-compose.yml   一体化部署入口
└─ README.md
```

### 目录说明

- `chatroom-client`
  前端界面、路由、聊天页面、消息显示、状态管理以及 WebSocket 客户端逻辑。

- `chatroom-server`
  后端 API、认证授权、业务服务、数据持久化、WebSocket 处理、Bot 服务、记忆服务以及分布式演进相关实现。

- `data`
  AI 角色 / 技能相关的数据与参考资料。

- `deploy`
  部署中间件相关辅助文件，例如 RabbitMQ 插件配置。

- `docs`
  项目设计、记忆系统设计、测试说明与测试结果文档。

- `test`
  压测脚本、Bot 测试脚本、初始化脚本以及示例导入数据。

## 六层协作历史

本仓库最初按六层协作方式拆分，每一层对应一个成员分支。虽然当前工作区已经恢复成标准可运行结构，但这些分支历史仍然保留，方便后续解释小组分工、展示每个人负责的代码范围。

### 六个层分支对应关系

- `layer-01-access-boundary`
  接入与边界层

- `layer-02-realtime-session-routing`
  实时通信与会话路由层

- `layer-03-message-persistence`
  消息域与会话持久化层

- `layer-04-social-organization`
  社交关系与组织域

- `layer-05-bot-ai-orchestration`
  Bot 编排与 AI 执行层

- `layer-06-memory-infra-governance`
  记忆检索与基础设施治理层

### 成员与层分工对应

- `eeeeeeex1 <2629620478@qq.com>`
  第 1 层，同时承担集成、README 重写、结构恢复等工作

- `Ismweirdo <2074437070@qq.com>`
  第 2 层

- `Jungle-Cristo <jungle920@qq.com>`
  第 3 层

- `Jay7982024 <1731614640@qq.com>`
  第 4 层

- `Yuanxiaelf <1056606933@qq.com>`
  第 5 层

- `kuuzzzzzzzzzz <kuuzzzz@163.com>`
  第 6 层

## 架构说明

从工程结构来看，当前项目主要由以下部分组成：

- 前端应用：`chatroom-client`
- 后端应用：`chatroom-server`
- 持久化存储：MySQL
- 缓存 / 队列支持：Redis
- 可选 STOMP Relay 支持：RabbitMQ
- 部署集成方式：Docker Compose + Nginx

从协作视角来看，项目依然保留了原来的六层分工历史。

从分布式演进视角来看，项目已经完成第一轮关键改造，例如：

- 可配置的 WebSocket Broker 模式
- 基于 Redis 的在线状态管理
- 基于 outbox 的消息事件处理
- 消息送达 / 已读状态链路
- Bot 状态持久化支持
- 后端运行指标与可观测性配置

## 本地开发

### 前端启动

安装依赖：

```bash
cd chatroom-client
npm install
```

启动开发环境：

```bash
npm run dev
```

构建前端：

```bash
npm run build
```

### 后端启动

后端打包：

```bash
cd chatroom-server
mvn -DskipTests package
```

本地运行后端：

```bash
mvn spring-boot:run
```

根据运行环境不同，你也可以通过环境变量覆盖以下配置：

- MySQL 主机、端口、库名、用户名、密码
- Redis 主机与端口
- WebSocket Broker 模式
- STOMP Relay 主机与端口

## Docker 部署

仓库中已经包含 `docker-compose.yml` 以及相关部署文件，可以用于一体化启动。

涉及的主要服务与配置包括：

- 前端容器
- 后端容器
- MySQL
- Redis
- RabbitMQ 相关配置
- Nginx 前端服务配置

因此，当前 `integration-six-layer` 分支既可以用于本地手动运行，也适合用于容器化的联合部署实验。

## 构建验证状态

当前 `integration-six-layer` 分支已经在本地完成过以下验证：

后端构建：

```bash
cd chatroom-server
mvn -DskipTests package
```

前端构建：

```bash
cd chatroom-client
npm install
npm run build
```

这说明当前分支不是单纯的分层展示分支，而是已经恢复成了可构建、可继续开发的集成工程分支。

## 文档与测试资源

仓库中还保留了较完整的设计文档与测试脚本，主要位于：

- `docs/README.md`
- `docs/memory-system-design.md`
- `docs/test-design.md`
- `docs/test-results.md`
- `test/`

这些内容可以用于：

- 项目介绍
- 架构说明
- 记忆系统说明
- 压测与测试展示
- 课程答辩或小组汇报材料准备

## 后续分支使用建议

后续建议采用如下方式继续协作：

- 将 `integration-six-layer` 作为默认集成分支
- 新需求或优化先在各自功能分支开发
- 验证通过后再合并回 `integration-six-layer`
- 六个 `layer-*` 分支继续保留，用于展示成员责任边界和 contribution 来源

如果后续项目需要更正式的发布流程，也可以再从 `integration-six-layer` 单独切出发布分支。

## 重要说明

原始的“六个顶层文件夹协作视图”已经从当前工作区移除，因为这个分支已经被恢复成标准可运行结构。

但是以下内容仍然保留：

- 六个协作层分支仍然存在
- 每个人的历史提交记录仍然保留在 Git 中
- 后续查看 contribution 时，应优先以 `integration-six-layer` 为主

## 总结

`integration-six-layer` 现在是仓库中最适合继续开发和展示的小组主分支。

它同时保留了：

- 六人协作分工历史
- 分层分支责任结构
- 已恢复的可运行工程布局
- 后续继续开发、联调、测试与展示的基础
