export interface AppConfig {
  httpBase: string;
  wsDebugUrl: string;
}

const SETUP_HINT = [
  "无法确定阅读T的地址。排查:",
  "1. 环境变量 LEGADO_WEB_URL 是否已设置(如 http://192.168.1.5:1234)",
  "2. 手机是否已开启「Web 服务」",
  "3. 手机与本机是否在同一局域网;adb forward 场景填 http://127.0.0.1:<端口>",
].join("\n");

export function resolveConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  const raw = env.LEGADO_WEB_URL;
  if (!raw) {
    throw new Error(SETUP_HINT);
  }
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    throw new Error(`LEGADO_WEB_URL 不是合法 URL: ${raw}\n${SETUP_HINT}`);
  }
  if (url.protocol !== "http:") {
    throw new Error(`LEGADO_WEB_URL 仅支持 http://(当前 ${url.protocol})`);
  }
  const port = url.port ? parseInt(url.port, 10) : 80;
  return {
    httpBase: `http://${url.hostname}:${port}`,
    wsDebugUrl: `ws://${url.hostname}:${port + 1}/bookSourceDebug`,
  };
}
