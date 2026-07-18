import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { createServer, type Server } from "node:http";
import { appGet, appPost } from "../src/appClient.js";

let server: Server;

beforeAll(async () => {
  server = createServer((req, res) => {
    if (req.url?.startsWith("/ok")) {
      res.setHeader("content-type", "application/json");
      res.end(
        JSON.stringify({ isSuccess: true, errorMsg: "", data: { url: req.url } }),
      );
    } else if (req.url?.startsWith("/fail")) {
      res.setHeader("content-type", "application/json");
      res.end(
        JSON.stringify({ isSuccess: false, errorMsg: "源名称和URL不能为空", data: null }),
      );
    } else if (req.url?.startsWith("/text")) {
      res.end("java.lang.Exception: boom");
    } else if (req.url?.startsWith("/echo")) {
      let body = "";
      req.on("data", (c) => (body += c));
      req.on("end", () => {
        res.setHeader("content-type", "application/json");
        res.end(
          JSON.stringify({
            isSuccess: true,
            errorMsg: "",
            data: {
              method: req.method,
              contentType: req.headers["content-type"],
              body,
            },
          }),
        );
      });
    }
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  const port = (server.address() as { port: number }).port;
  process.env.LEGADO_WEB_URL = `http://127.0.0.1:${port}`;
});

afterAll(() => new Promise<void>((r) => server.close(() => r())));

describe("appClient", () => {
  it("解包 isSuccess=true 的 data", async () => {
    expect(await appGet("/ok")).toEqual({ url: "/ok" });
  });

  it("query 参数序列化进 URL", async () => {
    expect(await appGet("/ok", { url: "https://a.com" })).toEqual({
      url: "/ok?url=https%3A%2F%2Fa.com",
    });
  });

  it("isSuccess=false 时抛 errorMsg", async () => {
    await expect(appGet("/fail")).rejects.toThrow("源名称和URL不能为空");
  });

  it("非 JSON 响应报状态码与片段", async () => {
    await expect(appGet("/text")).rejects.toThrow(/非 JSON/);
  });

  it("POST 透传 body 与 content-type", async () => {
    const data = (await appPost(
      "/echo",
      "var a = 1",
      "text/plain; charset=utf-8",
    )) as Record<string, unknown>;
    expect(data.method).toBe("POST");
    expect(data.contentType).toBe("text/plain; charset=utf-8");
    expect(data.body).toBe("var a = 1");
  });

  it("连接失败时报排查指引", async () => {
    process.env.LEGADO_WEB_URL = "http://127.0.0.1:1";
    await expect(appGet("/ok")).rejects.toThrow(/排查/);
  });
});
