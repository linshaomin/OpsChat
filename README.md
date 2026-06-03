# OpsChat - AI Ops 智能运维助手

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 项目简介

OpsChat 是一个基于 AI 的智能运维助手，专注于通过自然语言交互提供运维支持。项目名称 **OpsChat** 取自 "Ops" (运维) 和 "Chat" (对话)，定位为 **AI Ops** 场景。

### 核心特性

- **智能意图识别**：二级路由架构（规则路由 + LLM语义分类）
- **流式对话响应**：基于 SSE 的实时流式输出
- **多工作流编排**：支持闲聊、工具调用、知识库、React分析
- **会话上下文管理**：Redis存储，支持历史记录和摘要压缩

---

## 技术选型分析

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
│                    WorkflowChatController                    │
│                      流式对话入口                            │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                      IntentRouter                            │
│                   二级意图路由编排器                          │
│  ┌─────────────────┐         ┌─────────────────┐           │
│  │   RuleRouter    │         │    LlmRouter    │           │
│  │  (规则匹配)     │         │   (语义分类)     │           │
│  └─────────────────┘         └─────────────────┘           │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                   WorkflowOrchestrator                       │
│                     工作流编排器                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐     │
│  │ ChatWorkflow│ │ToolWorkflow│ │KnowledgeWorkflow│ │ReactWorkflow│ │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘     │
└─────────────────────────────────────────────────────────────┘
                          │
┌─────────────────────────┬───────────────────────────────────┐
│                         │                                    │
┌─────────────────────────▼───────────────────────────────────┐
│                       LlmService                             │
│                    通义千问 API 调用                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 功能介绍

### 意图识别与路由

OpsChat 采用**二级路由架构**实现智能意图识别：

**第一级 - RuleRouter（规则路由）**
- 处理高确定性场景：问候语、告别语、系统命令
- 识别结构化ID：订单号、TraceID、工单ID
- 识别能力咨询：问"你能做什么"
- 优点：零延迟、零成本、准确率高

**第二级 - LlmRouter（语义路由）**
- 处理复杂语义场景：多义词、模糊表述
- 五分类体系：
  - `CHAT`：闲聊、问候、能力咨询
  - `TOOL`：单步系统查询（日志、监控、告警）
  - `RAG`：技术知识库问答（SOP、架构文档）
  - `REACT`：多步骤推理分析（根因定位）
  - `CLARIFY`：信息不足，需要澄清

### 流式响应机制

```
用户提问 → 意图识别 → 工作流执行 → SSE流式输出
                ↓
         ┌──────┴──────┐
         │  逐Token输出│ → 前端实时渲染
         └─────────────┘
```

- 使用 SSE (Server-Sent Events) 实现服务端推送
- 支持多种事件类型：`content`、`tool_call`、`search_result`、`trace`、`error`、`done`
- 前端实时渲染 Markdown 格式

### 会话管理

- 基于 Redis 的会话存储
- 支持会话过期自动清理（默认24小时）
- 消息历史自动压缩（超过阈值触发摘要）

### RAG 知识库检索

- 支持文件上传（`.txt`、`.md`）并自动向量化
- 基于 Milvus 向量数据库的语义检索
- 文档智能分片，保留上下文完整性

### 文件上传

- 支持上传 `.txt` 和 `.md` 文件
- 上传后自动进行分词、向量化并存储到 Milvus
- 支持批量索引目录中的所有文件

---

## 快速开始

### 环境要求

- Docker & Docker Compose（用于启动依赖服务）
- Java 17+ (本地运行)
- 阿里云 DashScope API Key

### 本地开发（IDEA 方式）

1. 启动依赖服务：
```bash
# 启动 Redis 和 Milvus
make deps
```

2. 在 IDEA 中直接运行主类：
   - 打开 `src/main/java/com/opschat/OpsChatApplication.java`
   - 点击运行按钮
   - 配置环境变量：`DASHSCOPE_API_KEY=your-api-key-here```

### 启动服务

#### 方式一：IDEA 开发模式

```bash
# 1. 启动依赖服务（Redis + Milvus）
make deps

# 2. 在 IDEA 中运行 OpsChatApplication
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

#### Docker 镜像加速（可选）

如果 Docker 镜像拉取缓慢或失败，可以配置国内镜像加速。创建/编辑 `/etc/docker/daemon.json`：
```json
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.baidubce.com"
  ]
}
```
然后重启 Docker：`sudo systemctl restart docker`

### 服务地址

| 服务 | 地址 | 说明 |
|------|------|------|
| API 服务 | http://localhost:8080 | OpsChat 主服务 |
| Milvus | localhost:19530 | 向量数据库 |
| Attu (Web UI) | http://localhost:8000 | Milvus Web 管理界面 |
| MinIO Console | http://localhost:9001 | 对象存储管理界面 (admin/minioadmin) |
| Redis | localhost:6379 | 会话存储 |

### 使用 Makefile 管理

| 命令 | 说明 |
|------|------|
| `make deps` | 启动依赖服务（Redis + Milvus） |
| `make up` | 启动 Docker 服务（等同于 make deps） |
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
| `make stop-all` | 停止所有服务（Spring Boot + Docker） |

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

事件类型：
- `message`: 内容事件（`{"type":"content","content":"..."}`）
- `tool`: 工具调用/结果事件
- `search`: 搜索结果事件
- `trace`: 追踪信息
- `error`: 错误事件
- `done`: 完成事件

#### 普通对话

```
POST /api/v1/chat
Content-Type: application/json

{
  "id": "session-123",    // 会话ID（可选）
  "question": "如何查看Redis内存使用情况？"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "answer": "回答内容...",
    "success": true
  }
}
```

### 文件上传

```
POST /api/upload
Content-Type: multipart/form-data

file: <文件>
```

**响应**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileName": "redis-guide.md",
    "filePath": "./uploads/redis-guide.md",
    "fileSize": 12345
  }
}
```

### 会话管理

```
GET /api/v1/sessions
```
获取所有会话ID列表

```
DELETE /api/v1/sessions/{sessionId}
```
删除指定会话

### 健康检查

```
GET /api/v1/health
```
返回 `OK` 表示服务正常

---

## 项目结构

```
OpsChat/
├── src/main/java/com/opschat/
│   ├── controller/        # 控制器层
│   │   ├── WorkflowChatController.java
│   │   └── FileUploadController.java
│   ├── service/          # 服务层
│   │   ├── SessionService.java
│   │   ├── RagService.java
│   │   ├── VectorSearchService.java    # 向量检索
│   │   ├── VectorIndexService.java     # 向量索引
│   │   └── VectorEmbeddingService.java  # 向量生成
│   ├── llm/              # LLM服务
│   │   ├── LlmService.java
│   │   └── PromptTemplateService.java
│   ├── router/           # 意图路由
│   │   ├── IntentRouter.java
│   │   ├── RuleRouter.java
│   │   └── LlmRouter.java
│   ├── workflow/         # 工作流
│   │   ├── WorkflowOrchestrator.java
│   │   ├── WorkflowStrategy.java
│   │   └── impl/
│   │       ├── ChatWorkflow.java
│   │       ├── ToolWorkflow.java
│   │       ├── KnowledgeWorkflow.java
│   │       └── ReactWorkflow.java
│   ├── config/           # 配置类
│   │   ├── MilvusConfig.java
│   │   ├── FileUploadConfig.java
│   │   └── DocumentChunkConfig.java
│   ├── dto/              # 数据传输对象
│   │   ├── SessionInfo.java
│   │   ├── DocumentChunk.java
│   │   └── FileUploadRes.java
│   ├── stream/           # 流式事件
│   │   ├── WorkflowEvent.java
│   │   ├── WorkflowEventType.java
│   │   ├── SseResponseHandler.java
│   │   └── WorkflowEventPublisher.java
│   ├── client/           # 客户端
│   │   └── MilvusClientFactory.java
│   ├── constant/         # 常量
│   │   └── MilvusConstants.java
│   └── OpsChatApplication.java
├── src/main/resources/
│   ├── application.yml   # 配置文件
│   └── static/index.html # 前端页面
├── docker-compose.yml    # 容器编排
├── Dockerfile            # 镜像构建
├── Makefile              # 项目管理
└── pom.xml
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
    max-messages: 50
    compress-threshold: 10
  router:
    llm-model: qwen-plus
    enabled: true
  llm:
    model: qwen-turbo
    temperature: 0.7
    max-tokens: 2000
  rag:
    milvus:
      host: ${MILVUS_HOST:localhost}
      port: ${MILVUS_PORT:19530}
      collection-name: opschat_docs
      dimension: 1024
    embedding:
      model: text-embedding-v2
    file:
      upload:
        path: ./uploads
        allowed-extensions: txt,md
```

---

## 贡献指南

欢迎提交 Issue 和 Pull Request！

---

## License

MIT License