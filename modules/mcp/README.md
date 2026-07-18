# legado-mcp

阅读T书源开发闭环 MCP server:推送书源、跑 App 内调试管线、读 HTTP 请求日志。
薄代理形态——每个工具是一次对 App WebService 的调用,App 是源格式唯一真理源。

## 前置条件

1. 手机安装本仓构建的阅读T(含 `/saveJsSource`、`/getHttpLogs` 端点)
2. App 内开启「Web 服务」,记下端口(默认 1234)
3. 调试请求细节需开「记录HTTP日志」开关(可由 set_http_log_recording 工具远程开)
4. PC 与手机同一局域网;或 `adb forward tcp:1234 tcp:1234 && adb forward tcp:1235 tcp:1235`

## 构建与注册

```bash
cd modules/mcp
pnpm install
pnpm build
claude mcp add legado --scope user \
  --env LEGADO_WEB_URL=http://<手机IP>:1234 \
  -- node <本目录绝对路径>/dist/index.js
```

adb forward 场景 `LEGADO_WEB_URL=http://127.0.0.1:1234`。
WebSocket 调试端口自动按 HTTP 端口+1 派生,无需配置。

## 工具

| 工具 | 用途 |
| --- | --- |
| save_source | 推送 JS/JSON 书源(自动识别格式,同 url 覆盖) |
| debug_source | App 内调试管线;key:关键词/URL/::URL/++URL/--URL |
| list_sources | 书源摘要列表,可过滤 |
| get_source | 完整书源 JSON(JS 脚本在 mainJs) |
| delete_sources | 按 url 批量删除 |
| get_http_logs | HTTP 日志摘要(最新 50 条,带开关状态) |
| get_http_log | 单条完整记录(头+体,体存储上限 4096 字符) |
| set_http_log_recording | 远程开关「记录HTTP日志」,与设置页同步 |

## 开发

```bash
pnpm test   # 构建 + vitest(单测 + stdio 集成测试)
```
