import WebSocket from "ws";

export interface DebugResult {
  log: string;
  timedOut: boolean;
}

export function collectDebugLogs(
  wsUrl: string,
  tag: string,
  key: string,
  timeoutMs: number,
): Promise<DebugResult> {
  return new Promise((resolve, reject) => {
    const lines: string[] = [];
    const ws = new WebSocket(wsUrl);
    const timer = setTimeout(() => {
      ws.terminate();
      resolve({ log: lines.join("\n"), timedOut: true });
    }, timeoutMs);
    ws.on("open", () => {
      ws.send(JSON.stringify({ tag, key }));
    });
    ws.on("message", (data) => {
      lines.push(data.toString());
    });
    ws.on("close", () => {
      clearTimeout(timer);
      resolve({ log: lines.join("\n"), timedOut: false });
    });
    ws.on("error", (err) => {
      clearTimeout(timer);
      reject(
        new Error(
          `调试连接失败(${wsUrl}): ${err.message}。确认手机 Web 服务已开启,调试端口为 HTTP 端口+1。`,
        ),
      );
    });
  });
}
