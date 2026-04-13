# LangChain Spring Boot Chat Demo

基于 Spring Boot + LangChain4j 的智能对话示例项目，支持：

- 大模型对话（OpenAI 兼容接口）
- Skill 优先匹配与执行（先工具、后 AI）
- 流式输出（前端逐字显示）
- Markdown 文件生成与保存
- Python 数学计算能力调用

项目定位是“可扩展的 AI 工具编排后端 + 简洁演示前端”。

***

## 1. 项目概览

### 1.1 核心能力

- 普通聊天：`GET /api/chat`
- 流式聊天（SSE 封装）：`GET /api/chat/stream`
- 流式聊天（纯文本流）：`GET /api/chat/stream/text`
- Skill 查询与匹配测试：
  - `GET /api/skills`
  - `POST /api/skills/test`
- 健康示例接口：`GET /api/hello`

### 1.2 Skill 机制（核心设计）

请求处理流程：

1. 接收用户消息
2. 在 `SkillRegistry` 中按优先级查找可处理 Skill
3. 逐个执行匹配 Skill
4. 根据 `SkillResult` 决定：
   - `success()`：直接返回，不再调用大模型
   - `successWithAI()`：携带上下文后继续调用大模型
   - `passToAI()`：跳过 Skill，继续调用大模型
   - `failure()`：忽略当前 Skill，尝试下一个
5. 若需要 AI，则进入 LangChain4j `OpenAiChatModel`

***

## 2. 项目架构说明

### 2.1 架构分层

项目采用典型的分层架构，并按“请求入口 → 业务编排 → 能力执行 → 外部系统”组织：

- 表现层（Controller）
  - 对外提供聊天、Skill 测试、Embedding 入库、Chroma 数据查询接口
  - 对应前端页面包括聊天页、Embedding 测试页、Chroma 数据查看页
- 编排层（Service）
  - 负责对话主流程、Skill 链路调度、Embedding 生成与向量入库
  - 将业务规则与接口协议解耦
- 能力层（Skill + Model）
  - Skill 负责确定性任务（时间、计算、Python 数学、Markdown）
  - 大模型负责自然语言生成与兜底回答
- 基础设施层（Config + Util）
  - Chroma v2 客户端、启动连通性检查、Python 执行器、文件工具等
  - 统一处理认证、超时、异常与外部调用细节

### 2.2 核心调用链路

1. 聊天链路\
   前端发起请求 → `ChatController` → `ChatServiceImpl`\
   → `SkillRegistry` 按优先级匹配并执行 Skill\
   → 命中则直接返回或增强上下文后继续 AI\
   → 未命中则直接调用 LangChain4j 对话模型返回结果
2. 向量入库链路\
   前端 Embedding 页发起入库 → `EmbeddingController` → `EmbeddingServiceImpl`\
   → `EmbeddingModel` 生成向量\
   → `ChromaV2Client` 调用 Chroma 1.4.1 的 v2 API 完成 upsert
3. 向量查询链路\
   Chroma 数据页请求集合/记录 → `EmbeddingController`\
   → `EmbeddingServiceImpl` → `ChromaV2Client`\
   → 返回集合统计与分页记录（id / document / metadata）

### 2.3 外部依赖边界

- LLM 网关：通过 OpenAI 兼容协议接入（配置化 `base-url`、`api-key`、`model-name`）
- 向量库：ChromaDB（v2 API）
- 本地计算扩展：Python 子进程（用于复杂数学任务）

### 2.4 可靠性与可观测性设计

- 启动自检：`ChromaStartupCheck` 在应用启动阶段检查 Chroma 连通性（version/heartbeat）
- 容错处理：Service 层对外部调用统一捕获异常并返回可读错误信息
- 参数约束：分页、文本等输入在服务层做边界与空值校验
- 日志体系：Log4j2 统一输出，便于定位 Skill 命中、模型调用与向量库交互问题

### 2.5 框架视角的整体架构（Spring Boot + LangChain4j + ChromaDB）

从框架组合角度看，项目可概括为：

- Spring Boot（应用骨架与工程基础）
  - 提供依赖注入、配置管理、Web 容器与统一启动能力
  - 承载 Controller/Service/Config/Util 的模块协作
- LangChain4j（LLM 编排与向量能力入口）
  - 对话能力通过 `OpenAiChatModel` 接入 OpenAI 兼容模型网关
  - 向量能力通过 `EmbeddingModel` 生成文本 embedding
  - 当前向量模型依赖为 `langchain4j-embeddings-all-minilm-l6-v2:1.12.2-beta22`
  - 对应实现为 `AllMiniLmL6V2EmbeddingModel`，用于本地轻量 embedding 生成
- ChromaDB v2（向量存储与检索）
  - 由 `ChromaV2Client` 负责与 Chroma 1.4.1 的 v2 API 交互
  - 承担集合管理、向量入库、记录分页查询等数据面职责
- Web 前端（原生 HTML/CSS/JS）
  - 聊天页负责流式对话交互
  - Embedding 测试页负责向量化与入库调试
  - Chroma 数据查看页负责集合与记录可视化浏览

可以用一句话理解为：\
`Spring Boot` 负责“系统承载与接口编排”，`LangChain4j` 负责“模型与向量能力抽象”，`ChromaDB` 负责“向量数据持久化与查询”，三者通过 Service 层解耦协作。

***

## 3. 技术栈

- Java 21
- Spring Boot 2.7.15
- Spring MVC + WebFlux（用于流式响应）
- ChromaDB 1.4.1 (v2 API)
- LangChain4j 1.12.2
  - `langchain4j-core`
  - `langchain4j-open-ai`
  - `langchain4j-embeddings-all-minilm-l6-v2` (向量模型)
- Log4j2（替代默认 logging）
- 前端：原生 HTML/CSS/JS + Marked + KaTeX
- 构建工具：Maven

***

## 4. 目录结构

```text
LangChain/
├─ pom.xml
├─ scripts/
│  └─ math_utils.py
├─ src/main/java/com/example/langchain/
│  ├─ LangChainApplication.java
│  ├─ controller/
│  │  ├─ ChatController.java
│  │  ├─ HelloController.java
│  │  └─ SkillController.java
│  ├─ model/
│  │  └─ HelloResponse.java
│  ├─ service/
│  │  ├─ ChatService.java
│  │  ├─ HelloService.java
│  │  └─ impl/
│  │     ├─ ChatServiceImpl.java
│  │     └─ HelloServiceImpl.java
│  ├─ skill/
│  │  ├─ Skill.java
│  │  ├─ SkillContext.java
│  │  ├─ SkillRegistry.java
│  │  ├─ SkillResult.java
│  │  └─ impl/
│  │     ├─ TimeSkill.java
│  │     ├─ CalculatorSkill.java
│  │     ├─ PythonMathSkill.java
│  │     ├─ MarkdownSkill.java
│  │     └─ SaveAsMarkdownSkill.java
│  └─ util/
│     ├─ MarkdownFileUtil.java
│     ├─ PythonExecutor.java
│     └─ PythonResult.java
└─ src/main/resources/
   ├─ application.yml
   ├─ log4j2-spring.xml
   └─ static/
      ├─ index.html
      ├─ css/style.css
      └─ js/chat.js
```

***

## 5. 模块说明

### 4.1 Controller 层

- `ChatController`
  - 提供同步/流式聊天接口
  - 流式文本接口供前端 `fetch + ReadableStream` 消费
- `SkillController`
  - 获取已注册 Skill 列表
  - 测试某条消息会匹配哪些 Skill
- `HelloController`
  - 示例接口，验证服务启动

### 4.2 Service 层

- `ChatServiceImpl`：
  - 负责主流程编排
  - 执行 Skill 链
  - 构造增强 Prompt
  - 调用 LangChain4j 模型
  - 内置超时、重试、异常友好提示
- `HelloServiceImpl`：
  - 返回示例问候与时间戳

### 4.3 Skill 层

内置 Skill（按优先级）：

- `TimeSkill`（10）：处理时间/日期问答
- `CalculatorSkill`（20）：四则运算与中文算式
- `PythonMathSkill`（25）：阶乘、斐波那契、开方、幂运算
- `MarkdownSkill`（80）：生成 Markdown 文件
- `SaveAsMarkdownSkill`（85）：将内容保存为 Markdown 文件

### 4.4 Util 层

- `PythonExecutor`：执行 Python 代码/脚本，封装超时与输出
- `MarkdownFileUtil`：生成并保存 `.md` 文件到 `markdown/`

### 4.5 前端页面

- `index.html`：聊天页面骨架
- `chat.js`：
  - 发送消息到 `/api/chat/stream/text`
  - 逐字渲染流式响应
  - 结束后将结果按 Markdown + KaTeX 渲染

***

## 6. 配置说明

配置文件：`src/main/resources/application.yml`

### 5.1 大模型配置（OpenAI 兼容）

- `langchain4j.open-ai.chat-model.api-key`
- `langchain4j.open-ai.chat-model.model-name`
- `langchain4j.open-ai.chat-model.base-url`
- `langchain4j.open-ai.chat-model.temperature`
- `langchain4j.open-ai.chat-model.max-tokens`

### 5.2 聊天服务配置

- `app.chat.api-timeout-seconds`
- `app.chat.max-retry-attempts`
- `app.chat.retry-delay-ms`

### 5.3 日志

- 使用 `log4j2-spring.xml`
- `com.example.langchain.skill` 和 `ChatServiceImpl` 默认 debug 级别

***

## 7. 快速开始

### 6.1 环境要求

- JDK 8+
- Maven 3.6+
- Python 3.x（若需 `PythonMathSkill`）

### 6.2 推荐环境变量（Windows PowerShell）

```powershell
$env:OPENAI_API_KEY="你的API_KEY"
$env:OPENAI_MODEL_NAME="kimi-k2.5"
$env:OPENAI_BASE_URL="https://api.moonshot.cn/v1"
```

### 6.3 启动项目

```bash
mvn spring-boot:run
```

启动后访问：

- 前端页面：<http://localhost:8080/>
- Hello 接口：<http://localhost:8080/api/hello>

***

## 8. API 使用示例

### 7.1 普通聊天

```bash
curl "http://localhost:8080/api/chat?message=现在几点"
```

### 7.2 流式聊天（纯文本流）

```bash
curl "http://localhost:8080/api/chat/stream/text?message=请介绍一下Java"
```

### 7.3 查询 Skill 列表

```bash
curl "http://localhost:8080/api/skills"
```

### 7.4 测试 Skill 匹配

```bash
curl -X POST "http://localhost:8080/api/skills/test" ^
  -H "Content-Type: application/json" ^
  -d "{\"message\":\"5的阶乘\"}"
```

***

## 9. 扩展指南：新增一个 Skill

### 8.1 实现接口

实现 `com.example.langchain.skill.Skill`：

- `getName()`
- `getDescription()`
- `canHandle(String message)`
- `execute(String message, SkillContext context)`
- `getPriority()`（可选，默认 100）

### 8.2 注册方式

- 推荐：给 Skill 类加 `@Component`，自动装配到 `SkillRegistry`
- 或手动调用 `chatService.registerSkill(...)`

### 8.3 设计建议

- 高频且确定性高的能力，优先级设小（更先执行）
- `canHandle` 尽量精准，减少误触发
- `execute` 内部做好异常兜底，避免阻塞后续 Skill/AI

***

## 10. 构建与打包（Builder）

### 9.1 编译与测试

```bash
mvn clean test
```

### 9.2 打包

```bash
mvn clean package
```

产物默认位于：

```text
target/langchain-1.0-SNAPSHOT.jar
```

### 9.3 运行 Jar

```bash
java -jar target/langchain-1.0-SNAPSHOT.jar
```

***

## 11. 常见问题

### Q1：没有配置 API Key 会怎样？

服务会返回友好提示，且仍可使用部分本地 Skill（如时间、计算）。

### Q2：Python 相关能力不生效？

确认本机可直接执行 `python` 命令，或在 `PythonExecutor` 中改为绝对解释器路径。

### Q3：流式输出为什么末尾有 `[DONE]`？

后端用于标记流结束，前端读取后会停止并做最终 Markdown 渲染。

***

## 12. 后续优化建议

- 将对话上下文升级为多轮记忆（按会话持久化）
- 将 Python 执行沙箱化，增强安全隔离
- 为 Skill 增加权限控制与审计日志
- 增加自动化测试（Controller/Service/Skill）
- 增加 Dockerfile 与一键部署配置
