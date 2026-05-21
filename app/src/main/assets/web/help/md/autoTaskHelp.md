## 定时任务帮助

### Cron 表达式
格式：`分 时 日 月 周`

| 表达式 | 说明 |
|--------|------|
| `*/5 * * * *` | 每 5 分钟 |
| `0 9 * * *` | 每天 09:00 |
| `0 9 * * 1` | 每周一 09:00 |
| `0 0 1 * *` | 每月 1 日 00:00 |
| `0 8,20 * * *` | 每天 08:00 和 20:00 |
| `*/30 6-23 * * *` | 06:00～23:00 之间每 30 分钟 |
| `30 9 1-7 * 1` | 每月前 7 天中是周一的时刻 09:30 |

符号：
- `*` 任意值
- `,` 列表（多个值）
- `-` 范围
- `/` 步长

---

### 返回协议

脚本需要返回 **JSON**，支持三种格式：

**1. 多个动作（数组）**
```json
[
  { "type": "refreshToc", "bookUrl": "https://..." },
  { "type": "notify", "title": "完成", "content": "全部更新完毕" }
]
```

**2. 单个动作对象**
```json
{ "type": "notify", "title": "签到", "content": "已签到 {time}" }
```

**3. 包裹在 actions 中**
```json
{
  "actions": [
    { "type": "refreshToc", "bookUrl": "https://..." }
  ]
}
```

---

### 支持的动作

#### type: refreshToc — 更新书籍目录

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `type` | string | ✓ | `"refreshToc"` |
| `bookUrl` | string | ✓ | 书架中书籍的 bookUrl |
| `notify` | object | | 更新通知配置 |
| `cache` | object | | 缓存新章节配置 |

**notify 子对象：**

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `enable` | boolean | true | 是否发通知 |
| `minCount` | number | 1 | 至少新增多少章才通知 |
| `title` | string | "《{book}》更新提醒" | 通知标题 |
| `content` | string | "新增 {newCount} 章，最新：{chapter}" | 通知内容 |

**cache 子对象：**

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `enable` | boolean | false | 是否自动缓存新章节 |

**完整示例：**
```json
{
  "type": "refreshToc",
  "bookUrl": "https://example.com/book/123",
  "notify": {
    "enable": true,
    "minCount": 5,
    "title": "《{book}》更新了！",
    "content": "作者 {author} 新增 {newCount} 章\n最新：{chapter}\n时间：{time}"
  },
  "cache": {
    "enable": true
  }
}
```

---

#### type: notify — 发送通知

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `type` | string | ✓ | `"notify"` |
| `title` | string | | 通知标题，默认"定时任务通知" |
| `content` | string | | 通知内容 |
| `level` | string | | 优先级：`high`/`error`/`fail`/`low`，默认普通 |
| `id` | number | | 通知分组 id，相同 id 的通知会互相替换 |

```json
{
  "type": "notify",
  "title": "每日签到",
  "content": "已完成\n时间：{time}",
  "level": "high",
  "id": 1
}
```

---

### 占位符

**refreshToc 通知中可用：**

| 占位符 | 替换为 |
|--------|--------|
| `{book}` | 书名 |
| `{author}` | 作者 |
| `{newCount}` | 新增章节数 |
| `{chapter}` | 最新一章标题 |
| `{time}` | 当前时间（MM-dd HH:mm） |

**notify 通知中可用：**

| 占位符 | 替换为 |
|--------|--------|
| `{task}` | 任务名称 |
| `{time}` | 当前时间 |

---

### 调试

编辑定时任务页面右上角菜单 → "调试"，可单次运行当前脚本并在对话框查看执行日志。
