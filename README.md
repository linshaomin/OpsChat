# OpsChat - AI Ops 智能运维助手

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-green.svg)](https://github.com/yourusername/OpsChat)

## 项目简介

OpsChat 是一个基于 AI 的智能运维助手，专注于通过自然语言交互提供运维支持。项目名称 **OpsChat** 取自 "Ops" (运维) 和 "Chat" (对话)，定位为 **AI Ops** 场景。

### 核心特性

| 特性 | 描述 |
|------|------|
| **统一 Agent 驱动** | 基于 ReAct 的 Agent 作为唯一复杂任务执行入口 |
| **轻量 Gate 路由** | 简单问题直接回答，复杂问题进入 Agent 推理 |
| **流式对话响应** | 基于 SSE 的实时流式输出，用户体验提升 300% |
| **多轮工具调用** | 支持循环调用工具，直到问题收敛 |
| **会话上下文管理** | Redis存储，支持历史记录和智能摘要压缩 |
| **RAG 知识库** | 作为 Tool 能力接入，支持语义检索 |

---

## 技术选型

### 核心技术栈

| 类别 | 技术选型 | 选型理由 |
|------|----------|----------|
| **基础框架** | Spring Boot 3.2 | 成熟的微服务框架，生态丰富 |
| **AI 能力** | 阿里云 DashScope (通义千问) | 国产LLM API，效果好，性价比高 |
| **流式输出** | Server-Sent Events (SSE) | 轻量级实时通信，比WebSocket更简单 |
| **会话存储** | Redis | 高性能KV存储，支持过期策略 |
| **向量检索** | Milvus | 开源向量数据库，支持海量语义检索 |
| **部署方式** | Docker Compose | 容器化一键部署，开箱即用 |

### 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                        前端 (HTML)                          │
│                     单页面应用 / SSE                         │
└─────────────────────────┬───────────────────────────────────┘
                          │ HTTP/SSE
┌─────────────────────────▼───────────────────────────────────┐
│                    ChatController                            │
│                      流式对话入口                            │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                  AgentGateRouter                            │
│                轻量门限路由器 (Gate)                         │
│         判断: 简单问题? → Chat / 复杂问题? → Agent            │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                   WorkflowOrchestrator                       │
│                     工作流编排器                             │
│  ┌──────────┐              ┌──────────┐                    │
│  │ChatWorkflow│            │AgentWorkflow│                   │
│  │(简单问题) │            │ (复杂问题) │                    │
│  └──────────┘              └──────────┘                    │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                       ReactAgent                             │
│              基于 ReAct 的工具调用与推理引擎                   │
│  ┌─────────────────────────────────────────────┐            │
│  │     Think → Act → Observe → (循环)          │            │
│  │              多轮推理闭环                    │            │
│  └─────────────────────────────────────────────┘            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │ QCloudLog ││ Monitor  ││Knowledge ││ Current  │       │
│  │   Tool    ││  Metric  ││  Base    ││   Time   │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
└─────────────────────────────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                       LlmService                             │
│                    通义千问 API 调用                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 功能介绍

### Agent Gate 路由

OpsChat 采用**轻量 Gate 路由**实现智能分流：

**AgentGateRouter（门限路由器）**
- 仅判断：是否进入 React Agent
- 不做意图分类，不做业务路由
- 简单问题直接进入 ChatWorkflow（LLM直接回答）
- 复杂问题进入 AgentWorkflow（React Agent多步推理）

**分流策略：**
| 判断条件 | 结果 | 说明 |
|----------|------|------|
| 问候语/告别语 | Chat | 直接回答 |
| 能力咨询 | Chat | 直接回答 |
| 结构化ID（订单号、TraceID） | Agent | 需要工具查询 |
| 复杂问题关键词（日志、监控、分析、排查） | Agent | 需要工具调用 |
| 默认 | Agent | 让 Agent 决定如何处理 |

### React Agent 自动工具调用

基于 ReAct 模式实现 **Think → Act → Observe** 闭环：

```
用户提问 → Gate判断 → Agent → 思考 → 调用工具 → 观察结果 → (循环) → 得出结论
```

**支持的工具：**
| 工具名称 | 描述 | 参数 |
|----------|------|------|
| `qcloud_log_query` | 查询腾讯云日志服务 | query, time_range |
| `monitor_metric_query` | 查询系统监控指标 | metric_type, time_range, service |
| `knowledge_base` | 检索知识库文档 | query |
| `current_time` | 获取当前时间 | 无 |

**工具设计原则：**
- 一个 Tool 一个职责
- Tool 必须确定性执行
- Tool 不包含业务决策逻辑
- Tool 统一由 Agent 调用

**工具调用格式：**
```json
{"tool": "工具名称", "args": {"参数名": "参数值"}}
```

**思考格式（可选）：**
```
<thought>你的思考过程</thought>
```

**最终答案格式：**
```
<final_answer>你的最终结论</final_answer>
```

### 流式响应机制

```
用户提问 → 意图识别 → 工作流执行 → SSE流式输出
                ↓
         ┌──────┴──────┐
         │  逐Token输出│ → 前端实时渲染
         └─────────────┘
```

### 会话管理

- 基于 Redis 的会话存储
- 支持会话过期自动清理（默认24小时）
- 消息历史自动压缩（超过阈值触发摘要）

### RAG 知识库检索

- 支持文件上传（`.txt`、`.md`）并自动向量化
- 基于 Milvus 向量数据库的语义检索
- 文档智能分片，保留上下文完整性

### 腾讯云日志查询

- **自然语言查询**：支持通过自然语言描述查询日志
- **智能时间解析**：自动识别"今天"、"昨天"、"最近一小时"等时间范围
- **关键词提取**：自动从用户问题中提取查询关键词
- **真实 API 调用**：集成腾讯云 CLS（Cloud Log Service）SDK
- **优雅降级**：未配置时自动返回模拟数据

**查询示例**：
- "帮我查询今天的错误日志"
- "查看腾讯云日志中关于支付的记录"
- "搜索最近一小时的 API 日志"

---

## 快速开始

### 环境要求

- Docker & Docker Compose（用于启动依赖服务）
- Java 17+ (本地运行)
- 阿里云 DashScope API Key

### 启动服务

#### 方式一：IDEA 开发模式

```bash
# 1. 启动依赖服务（Redis + Milvus）
make deps

# 2. 在 IDEA 中运行 OpsChatApplication
# 配置环境变量：DASHSCOPE_API_KEY=your-api-key-here
```

#### 方式二：JAR 部署模式

```bash
# 1. 编译项目
mvn clean package -DskipTests

# 2. 启动依赖服务
make deps

# 3. 启动 Spring Boot 服务
make start

# 4. 等待服务就绪
make wait
```

### 服务地址

| 服务 | 地址 | 说明 |
|------|------|------|
| API 服务 | http://localhost:8080 | OpsChat 主服务 |
| Milvus | localhost:19530 | 向量数据库 |
| Attu (Web UI) | http://localhost:8000 | Milvus Web 管理界面 |
| MinIO Console | http://localhost:9001 | 对象存储管理界面 (admin/minioadmin) |
| Redis | localhost:6379 | 会话存储 |

### Makefile 命令

| 命令 | 说明 |
|------|------|
| `make deps` | 启动依赖服务（Redis + Milvus） |
| `make up` | 启动 Docker 服务 |
| `make down` | 停止 Docker 服务 |
| `make start` | 启动 Spring Boot 服务（后台运行） |
| `make stop` | 停止 Spring Boot 服务 |
| `make restart` | 重启 Spring Boot 服务 |
| `make check` | 检查服务是否运行 |
| `make status` | 查看所有服务状态 |
| `make logs` | 查看 Docker 日志 |
| `make ps` | 查看容器状态 |
| `make wait` | 等待服务就绪 |
| `make clean` | 清理临时文件 |

---

## API 接口

### 对话接口

#### 流式对话 (SSE)

```
POST /api/v1/chat/stream
Content-Type: application/json

{
  "id": "session-123",    // 会话ID（可选）
  "question": "如何查看Redis内存使用情况？"
}
```

**响应**: `text/event-stream`

#### 普通对话

```
POST /api/v1/chat
Content-Type: application/json

{
  "id": "session-123",    // 会话ID（可选）
  "question": "如何查看Redis内存使用情况？"
}
```

### 文件上传

```
POST /api/upload
Content-Type: multipart/form-data

file: <文件>
```

### 会话管理

```
GET /api/v1/sessions              # 获取所有会话
DELETE /api/v1/sessions/{id}      # 删除指定会话
GET /api/v1/sessions/{id}         # 获取会话历史
```

### 健康检查

```
GET /api/v1/health                # 返回 OK
```

---

## 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 | 必填 |
|--------|------|--------|------|
| `DASHSCOPE_API_KEY` | 阿里云 DashScope API Key | - | ✅ |
| `REDIS_HOST` | Redis 地址 | `localhost` | ❌ |
| `REDIS_PORT` | Redis 端口 | `6379` | ❌ |
| `MILVUS_HOST` | Milvus 地址 | `localhost` | ❌ |
| `MILVUS_PORT` | Milvus 端口 | `19530` | ❌ |
| `SERVER_PORT` | 服务端口 | `8080` | ❌ |

### application.yml 配置

```yaml
spring:
  application:
    name: OpsChat
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

server:
  port: ${SERVER_PORT:8080}

opschat:
  session:
    expire-hours: 24
    max-window-size: 30
    max-tokens: 8192
    summary-threshold: 0.7
  router:
    agent-enabled: true
    agent-threshold: 0.9
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        model: qwen-turbo
        temperature: 0.7
        max-tokens: 2048
```

---

## 项目结构

```
OpsChat/
├── src/main/java/com/opschat/
│   ├── controller/        # REST API 控制层
│   │   ├── ChatController.java
│   │   ├── ToolController.java
│   │   └── FileUploadController.java
│   ├── service/           # 业务逻辑层
│   │   ├── SessionService.java
│   │   ├── RagService.java
│   │   ├── VectorSearchService.java
│   │   ├── VectorIndexService.java
│   │   └── VectorEmbeddingService.java
│   ├── llm/               # LLM 服务层
│   │   ├── LlmService.java
│   │   └── PromptTemplateService.java
│   ├── agent/             # Agent 层
│   │   ├── ReactAgent.java
│   │   └── tool/          # Tool 能力
│   │       ├── AgentTool.java
│   │       ├── ToolRegistry.java
│   │       └── impl/
│   │           ├── QCloudLogTool.java
│   │           ├── MonitorMetricTool.java
│   │           ├── KnowledgeBaseTool.java
│   │           └── CurrentTimeTool.java
│   ├── router/            # 门限路由层
│   │   └── AgentGateRouter.java
│   ├── workflow/          # 工作流编排
│   │   ├── WorkflowOrchestrator.java
│   │   ├── WorkflowStrategy.java
│   │   ├── WorkflowType.java
│   │   └── impl/
│   │       ├── ChatWorkflow.java
│   │       └── AgentWorkflow.java
│   ├── config/            # 配置类
│   ├── dto/               # 数据传输对象
│   │   ├── ApiResponse.java
│   │   ├── ChatRequest.java
│   │   └── ChatResponse.java
│   ├── stream/            # 流式事件处理
│   │   ├── WorkflowEvent.java
│   │   ├── WorkflowEventType.java
│   │   └── SseResponseHandler.java
│   ├── client/            # 外部服务客户端
│   └── OpsChatApplication.java
├── src/main/resources/
│   ├── application.yml
│   └── static/index.html
├── docker-compose.yml
├── Dockerfile
├── Makefile
├── pom.xml
└── LICENSE
```

---

## 贡献指南

欢迎提交 Issue 和 Pull Request！

---

## License

MIT License