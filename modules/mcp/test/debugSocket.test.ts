import { afterEach, describe, expect, it } from "vitest";
import { WebSocketServer, type WebSocket } from "ws";
import { collectDebugLogs } from "../src/debugSocket.js";

let wss: WebSocketServer | undefined;

function startServer(onConn: (ws: WebSocket) => void): Promise<string> {
  return new Promise((resolve) => {
    wss = new WebSocketServer({ port: 0, host: "127.0.0.1" }, () => {
      const { port } = wss!.address() as { port: number };
      resolve(`ws://127.0.0.1:${port}`);
    });
    wss.on("connection", onConn);
  });
}

afterEach(
  () => new Promise<void>((r) => (wss ? wss.close(() => r()) : r())),
);

describe("collectDebugLogs", () => {
  it("发送 tag/key 并收集日志至对端关闭", async () => {
    const url = await startServer((ws) => {
      ws.on("message", (data) => {
        const { tag, key } = JSON.parse(data.toString()) as { tag: string; key: string };
        ws.send(`⇒开始调试 ${tag} ${key}`);
        ws.send("├搜索成功");
        ws.send("⇒调试结束");
        ws.close();
      });
    });
    const res = await collectDebugLogs(url, "https://a.com", "魔道", 5000);
    expect(res.timedOut).toBe(false);
    expect(res.log).toContain("开始调试 https://a.com 魔道");
    expect(res.log.split("\n")).toHaveLength(3);
  });

  it("超时返回已收部分并标记 timedOut", async () => {
    const url = await startServer((ws) => {
      ws.on("message", () => ws.send("├只有一行,对端不关闭"));
    });
    const res = await collectDebugLogs(url, "t", "k", 300);
    expect(res.timedOut).toBe(true);
    expect(res.log).toContain("只有一行");
  });

  it("连接失败 reject 带指引", async () => {
    await expect(
      collectDebugLogs("ws://127.0.0.1:1/bookSourceDebug", "t", "k", 1000),
    ).rejects.toThrow(/Web 服务/);
  });
});
