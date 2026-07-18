import { resolveConfig } from "./config.js";

interface ReturnData {
  isSuccess: boolean;
  errorMsg: string;
  data: unknown;
}

function connectHint(httpBase: string, cause: string): string {
  return [
    `连接阅读T失败(${httpBase}): ${cause}`,
    "排查:",
    "1. 手机是否已开启「Web 服务」",
    `2. 手机 IP 是否变化(LEGADO_WEB_URL 当前指向 ${httpBase})`,
    "3. 手机与本机是否在同一局域网",
  ].join("\n");
}

async function request(path: string, init?: RequestInit): Promise<unknown> {
  const { httpBase } = resolveConfig();
  let res: Response;
  try {
    res = await fetch(httpBase + path, init);
  } catch (e) {
    throw new Error(connectHint(httpBase, e instanceof Error ? e.message : String(e)));
  }
  const text = await res.text();
  let body: ReturnData;
  try {
    body = JSON.parse(text) as ReturnData;
  } catch {
    throw new Error(`阅读T返回了非 JSON 响应(HTTP ${res.status}): ${text.slice(0, 500)}`);
  }
  if (!body.isSuccess) {
    throw new Error(body.errorMsg);
  }
  return body.data;
}

export function appGet(path: string, params?: Record<string, string>): Promise<unknown> {
  const qs = params ? "?" + new URLSearchParams(params).toString() : "";
  return request(path + qs);
}

export function appPost(
  path: string,
  body: string,
  contentType = "application/json",
): Promise<unknown> {
  return request(path, {
    method: "POST",
    headers: { "content-type": contentType },
    body,
  });
}
