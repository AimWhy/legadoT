import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { resolveConfig } from "./config.js";
import { appGet, appPost } from "./appClient.js";
import { collectDebugLogs } from "./debugSocket.js";
import { detectFormat, summarizeSources, truncateText } from "./format.js";

function ok(text: string) {
  return { content: [{ type: "text" as const, text }] };
}

function err(e: unknown) {
  return {
    content: [{ type: "text" as const, text: e instanceof Error ? e.message : String(e) }],
    isError: true,
  };
}

export function registerTools(server: McpServer): void {
  server.registerTool(
    "save_source",
    {
      description:
        "推送单个书源到运行中的阅读T。纯JS单文件源发脚本原文(App 侧校验必备函数并提取元数据,报错原样返回);" +
        "声明式源发 BookSource JSON 对象。同 bookSourceUrl 重推即覆盖,App 内的分组/启用等用户态字段保留。" +
        "返回 bookSourceUrl,即 debug_source 的 url 参数。",
      inputSchema: {
        source: z.string().min(1).describe("源文本:JS 脚本原文或 BookSource JSON 对象"),
        format: z
          .enum(["js", "json"])
          .optional()
          .describe("缺省自动识别:首个非空白字符为 { 或 [ 判为 json,否则 js"),
      },
    },
    async ({ source, format }) => {
      try {
        const fmt = format ?? detectFormat(source);
        let name: string;
        let url: string;
        if (fmt === "js") {
          const data = (await appPost(
            "/saveJsSource",
            source,
            "text/plain; charset=utf-8",
          )) as Record<string, unknown>;
          name = String(data.bookSourceName ?? "");
          url = String(data.bookSourceUrl ?? "");
        } else {
          await appPost("/saveBookSource", source);
          try {
            const parsed = JSON.parse(source) as Record<string, unknown>;
            name = String(parsed.bookSourceName ?? "");
            url = String(parsed.bookSourceUrl ?? "");
          } catch {
            name = "";
            url = "";
          }
        }
        return ok(
          url
            ? `已保存(${fmt}):${name}\nbookSourceUrl: ${url}`
            : `已保存(${fmt}),但无法从源文本解析出 bookSourceUrl(保存本身已成功)`,
        );
      } catch (e) {
        return err(e);
      }
    },
  );

  server.registerTool(
    "debug_source",
    {
      description:
        "在阅读T内真实运行书源调试管线,返回逐步日志(含每步请求与提取结果)。key 决定入口:" +
        "普通关键词=搜索→详情→目录→正文全管线;绝对URL=从详情起步;::URL=发现页;++URL=仅目录;--URL=仅正文。" +
        "日志过程中的请求细节可再用 get_http_logs 深挖。",
      inputSchema: {
        url: z.string().min(1).describe("书源 bookSourceUrl(save_source 的返回值)"),
        key: z.string().min(1).describe("调试入口:关键词 / 绝对URL / ::URL / ++URL / --URL"),
        timeoutSec: z.number().int().min(10).max(600).optional().describe("超时秒数,默认 120"),
      },
    },
    async ({ url, key, timeoutSec }) => {
      try {
        await appGet("/getBookSource", { url });
        const { wsDebugUrl } = resolveConfig();
        const timeout = timeoutSec ?? 120;
        const { log, timedOut } = await collectDebugLogs(wsDebugUrl, url, key, timeout * 1000);
        const body = log || "(调试无输出)";
        return ok(
          timedOut ? `${body}\n\n[调试超时 ${timeout}s,以上为已收到的部分日志]` : body,
        );
      } catch (e) {
        return err(e);
      }
    },
  );

  server.registerTool(
    "list_sources",
    {
      description:
        "列出阅读T内的书源(摘要:名称/url/分组/启用/是否JS源)。可按名称或 url 子串过滤。",
      inputSchema: {
        search: z.string().optional().describe("名称/url 子串过滤,大小写不敏感"),
      },
    },
    async ({ search }) => {
      try {
        let raw: unknown;
        try {
          raw = await appGet("/getBookSources");
        } catch (e) {
          if (e instanceof Error && e.message.includes("设备源列表为空")) {
            return ok("(App 内无书源)");
          }
          throw e;
        }
        const summaries = summarizeSources(raw, search);
        return ok(
          `共 ${summaries.length} 条\n` +
            truncateText(JSON.stringify(summaries, null, 1)),
        );
      } catch (e) {
        return err(e);
      }
    },
  );

  server.registerTool(
    "get_source",
    {
      description: "按 bookSourceUrl 取完整书源 JSON(JS 源脚本全文在 mainJs 字段)。",
      inputSchema: {
        url: z.string().min(1).describe("书源 bookSourceUrl"),
      },
    },
    async ({ url }) => {
      try {
        const data = await appGet("/getBookSource", { url });
        return ok(truncateText(JSON.stringify(data, null, 2), 200_000));
      } catch (e) {
        return err(e);
      }
    },
  );

  server.registerTool(
    "delete_sources",
    {
      description: "按 bookSourceUrl 删除阅读T内的书源(可多个)。",
      inputSchema: {
        urls: z.array(z.string().min(1)).min(1).describe("bookSourceUrl 列表"),
      },
    },
    async ({ urls }) => {
      try {
        await appPost(
          "/deleteBookSources",
          JSON.stringify(urls.map((u) => ({ bookSourceUrl: u }))),
        );
        return ok(`已删除 ${urls.length} 个书源`);
      } catch (e) {
        return err(e);
      }
    },
  );

  server.registerTool(
    "get_http_logs",
    {
      description:
        "拉取阅读T端 HTTP 请求日志摘要(最新在前,内存上限 50 条)。调试书源失败时用它定位 App 实发请求,再用 get_http_log 看详情。" +
        "需在 App 设置开启「记录HTTP日志」,未开启时会明确提示。",
      inputSchema: {
        limit: z.number().int().min(1).max(50).optional().describe("条数,默认 50"),
      },
    },
    async ({ limit }) => {
      try {
        const data = (await appGet("/getHttpLogs", { limit: String(limit ?? 50) })) as {
          recording: boolean;
          logs: Array<Record<string, unknown>>;
        };
        const lines = data.logs.map(
          (r) =>
            `#${r.id} ${new Date(Number(r.time)).toISOString()} ${r.method} ${r.url} → ${r.statusCode} ${r.duration}ms${r.error ? ` | ${r.error}` : ""}`,
        );
        const head = data.recording
          ? `最新 ${lines.length} 条(内存上限 50):`
          : "「记录HTTP日志」开关未开启(App 设置→记录HTTP日志),以下为开关关闭前的残留记录:";
        return ok(head + "\n" + (lines.length ? lines.join("\n") : "(空)"));
      } catch (e) {
        return err(e);
      }
    },
  );

  server.registerTool(
    "get_http_log",
    {
      description:
        "按 id 取单条 HTTP 请求完整记录(请求/响应头+体)。正文在 App 记录时已截断至 4096 字符,完整响应体以 PC 侧采集存档为准;" +
        "此记录的价值是看 App 实发请求头(Cookie/UA)与响应差异。",
      inputSchema: {
        id: z.number().int().positive().describe("get_http_logs 返回的记录 id"),
      },
    },
    async ({ id }) => {
      try {
        const r = (await appGet("/getHttpLog", { id: String(id) })) as Record<string, unknown>;
        const parts = [
          `#${r.id} ${r.method} ${r.url}`,
          `status: ${r.statusCode}  duration: ${r.duration}ms  time: ${new Date(Number(r.time)).toISOString()}`,
          "",
          "-- 请求头 --",
          String(r.requestHeaders ?? ""),
        ];
        if (r.requestBody) {
          parts.push("", "-- 请求体 --", truncateText(String(r.requestBody)));
        }
        parts.push("", "-- 响应头 --", String(r.responseHeaders ?? ""));
        if (r.responseBody) {
          parts.push("", "-- 响应体 --", truncateText(String(r.responseBody)));
        }
        if (r.error) {
          parts.push("", "-- 错误 --", String(r.error));
        }
        return ok(parts.join("\n"));
      } catch (e) {
        return err(e);
      }
    },
  );
}
