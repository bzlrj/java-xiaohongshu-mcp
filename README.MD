
# 🧩 Java Xiaohongshu MCP

**Java 实现的小红书 Model Context Protocol (MCP) 工具服务**

为 AI Agent / MCP Client 提供标准化接口，实现：
登录、发图文、搜索、评论、推荐内容、用户主页 等核心功能。

---

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![MCP](https://img.shields.io/badge/Protocol-MCP-lightgrey?logo=openai)](https://modelcontextprotocol.io)
[![Build](https://img.shields.io/badge/Build-Maven%20%7C%20Docker-blue)](https://maven.apache.org/)

---

## 🌟 项目简介

`Java Xiaohongshu MCP` 是一个基于 **Spring Boot + Playwright + MCP 协议** 的小红书自动化工具服务，  
通过标准化接口封装，实现对小红书常用操作的自动化调用与 AI 集成。

适用于：
- 🤖 **AI Agent 工具调用**（如 MCP Inspector、LangChain、Spring AI、n8n）
- 🧩 **自动化内容发布 / 采集**

---

## 🧱 功能特性

| 功能         | 描述                      |
|------------|-------------------------|
| 🪪 登录与状态检测 | 自动化登录与 Cookie 管理，支持状态检测 |
| 🖼️ 发布图文内容 | 支持标题、描述、多图（URL / 本地路径）  |
| 🎬️ 发布视频内容 | 支持标题、描述、本地视频            |
| 🔍 搜索内容    | 根据关键词搜索帖子，支持分页          |
| 🏠 获取推荐列表  | 模拟首页推荐内容                |
| 📄 获取帖子详情  | 获取帖子+互动数据+评论+子评论        |
| 💬 自动评论    | 支持自动发表评论                |
| 👤 用户主页信息  | 获取用户资料及笔记内容             |

---

## ⚙️ 技术栈

| 技术                   | 用途 |
|----------------------|------|
| **Java 21**           | 主语言 |
| **Spring Boot 3.x**  | Web 框架 |
| **Playwright Java**  | 浏览器自动化 |
| **MCP Protocol**     | AI Agent 接口协议 |

---

## 🚀 启动方式

### ✅ 方式一：直接运行 JAR（CLI 模式）

通过 `LoginCli` 类快速启动并登录小红书。

```bash
# 构建项目
mvn clean package -DskipTests

# 启动 CLI 登录工具
java -cp target/app.jar -Dloader.main=com.lv.xhsmcp.login.LoginCli org.springframework.boot.loader.launch.PropertiesLauncher
```

CLI 模式适合快速验证或单机自动化任务。

---

### 🐳 方式二：Docker 运行

项目支持容器化部署，可在任意环境中运行。

```bash
# 构建镜像
docker buildx build -t java-xhs-mcp:1.0.0 .

# 启动容器
docker run --rm -e PLAYWRIGHT_BROWSERS_PATH=/ms-playwright -v pw-browsers:/ms-playwright java-xhs-mcp:1.0.0 /bin/bash

```

启动后访问：  
👉 `http://127.0.0.1:8081/mcp`

---
## 🧠 MCP 集成示例

```bash
{
  "mcp_server": {
    "name": "xiaohongshu-mcp",
    "url": "http://localhost:8080/mcp",
    "transport": "streamable-http"
  }
}
```
---

## ⚠️ 注意事项

- 登录与发帖均依赖有效的 Cookie / 登录态。
- 评论与发帖功能需要合法的 `xsec_token` 参数。
- 项目仅供学习用途，**严禁用于商业爬取、滥用或违反平台政策的行为**。
---
## 📬 联系方式

如需交流或问题反馈，请通过以下方式：

- GitHub Issues: [提交问题](https://github.com/bzlrj/java-xiaohongshu-mcp/issues)
- 邮箱: hack-bzlrj@163.com
- 微信(交流、交朋友、共同进步)： 
<img src="docs/qrcode.png" width="200" alt="微信二维码">
> ⚠️ 请勿用于广告、推广或商业用途
---
## 🧾 License

本项目基于 [MIT License](./LICENSE) 进行开源。  
© 2025 [bzlrj](https://github.com/bzlrj). 保留所有权利。

