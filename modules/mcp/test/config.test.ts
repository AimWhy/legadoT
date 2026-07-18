import { describe, expect, it } from "vitest";
import { resolveConfig } from "../src/config.js";

describe("resolveConfig", () => {
  it("推导 http 基址与 ws 调试地址(端口+1)", () => {
    const cfg = resolveConfig({ LEGADO_WEB_URL: "http://192.168.1.5:1234" } as NodeJS.ProcessEnv);
    expect(cfg.httpBase).toBe("http://192.168.1.5:1234");
    expect(cfg.wsDebugUrl).toBe("ws://192.168.1.5:1235/bookSourceDebug");
  });

  it("末尾斜杠不影响基址推导", () => {
    const cfg = resolveConfig({ LEGADO_WEB_URL: "http://127.0.0.1:1234/" } as NodeJS.ProcessEnv);
    expect(cfg.httpBase).toBe("http://127.0.0.1:1234");
  });

  it("缺失环境变量时报错带排查指引", () => {
    expect(() => resolveConfig({} as NodeJS.ProcessEnv)).toThrow(/LEGADO_WEB_URL/);
  });

  it("非法 URL 报错", () => {
    expect(() => resolveConfig({ LEGADO_WEB_URL: "not a url" } as NodeJS.ProcessEnv)).toThrow(/不是合法/);
  });

  it("仅接受 http 协议", () => {
    expect(() => resolveConfig({ LEGADO_WEB_URL: "https://x:1" } as NodeJS.ProcessEnv)).toThrow(/http/);
  });
});
