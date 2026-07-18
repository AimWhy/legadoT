import { describe, expect, it } from "vitest";
import { detectFormat, summarizeSources, truncateText } from "../src/format.js";

describe("detectFormat", () => {
  it("首个非空白字符为 { 或 [ 判为 json", () => {
    expect(detectFormat('{"bookSourceUrl":"x"}')).toBe("json");
    expect(detectFormat("  [1]")).toBe("json");
  });

  it("其余判为 js", () => {
    expect(detectFormat("// 源\nvar source = {}")).toBe("js");
  });
});

describe("summarizeSources", () => {
  const raw = [
    {
      bookSourceName: "甲",
      bookSourceUrl: "https://a.com",
      bookSourceGroup: "组1",
      enabled: true,
      mainJs: "var a=1",
    },
    { bookSourceName: "乙", bookSourceUrl: "https://b.com", enabled: false },
  ];

  it("裁剪为摘要字段并派生 isJsSource", () => {
    expect(summarizeSources(raw)).toEqual([
      {
        bookSourceName: "甲",
        bookSourceUrl: "https://a.com",
        bookSourceGroup: "组1",
        enabled: true,
        isJsSource: true,
      },
      {
        bookSourceName: "乙",
        bookSourceUrl: "https://b.com",
        bookSourceGroup: "",
        enabled: false,
        isJsSource: false,
      },
    ]);
  });

  it("按名称/url 子串过滤,大小写不敏感", () => {
    expect(summarizeSources(raw, "B.COM")).toHaveLength(1);
    expect(summarizeSources(raw, "甲")).toHaveLength(1);
    expect(summarizeSources(raw, "无")).toHaveLength(0);
  });

  it("非数组输入返回空数组", () => {
    expect(summarizeSources(null)).toEqual([]);
  });

  it("数组内非对象元素被跳过", () => {
    expect(summarizeSources([null, raw[0], undefined, 7])).toHaveLength(1);
  });
});

describe("truncateText", () => {
  it("短文本原样通过", () => {
    expect(truncateText("abc", 10)).toBe("abc");
  });

  it("超限截断并标注原始长度", () => {
    const t = truncateText("x".repeat(20), 10);
    expect(t.startsWith("x".repeat(10))).toBe(true);
    expect(t).toContain("…[已截断,原文 20 字符]");
  });
});
