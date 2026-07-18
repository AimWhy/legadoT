import { describe, expect, it } from "vitest";
import { fileURLToPath } from "node:url";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";

const serverPath = fileURLToPath(new URL("../dist/index.js", import.meta.url));

async function connect(): Promise<Client> {
  const client = new Client({ name: "itest", version: "0.0.0" });
  await client.connect(
    new StdioClientTransport({
      command: process.execPath,
      args: [serverPath],
      env: {
        ...(process.env as Record<string, string>),
        LEGADO_WEB_URL: "http://127.0.0.1:1",
      },
    }),
  );
  return client;
}

describe("legado mcp server", () => {
  it("stdio 起服并暴露 7 个工具", async () => {
    const client = await connect();
    try {
      const { tools } = await client.listTools();
      expect(tools.map((t) => t.name).sort()).toEqual([
        "debug_source",
        "delete_sources",
        "get_http_log",
        "get_http_logs",
        "get_source",
        "list_sources",
        "save_source",
      ]);
    } finally {
      await client.close();
    }
  }, 20000);

  it("App 不可达时工具返回 isError 带排查指引", async () => {
    const client = await connect();
    try {
      const res = await client.callTool({ name: "list_sources", arguments: {} });
      expect(res.isError).toBe(true);
      expect(JSON.stringify(res.content)).toContain("排查");
    } finally {
      await client.close();
    }
  }, 20000);
});
