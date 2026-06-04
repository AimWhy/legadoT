# 搜索结果卡片重设计 — 无边框列表

日期：2026-06-04
状态：已与用户确认，待实现

## 背景与目标

当前搜索结果（`item_search.xml`）是带边框/阴影的 `MaterialCardView`，每条 item “浮”在背景上。对照微信读书、番茄、起点、QQ阅读等主流阅读 App，搜索结果普遍是**无边框列表项**：item 与背景融为一体，靠留白 + 极淡分隔线区分，唯一的立体元素是封面本身。

目标：把搜索结果从“卡片”改为**无边框列表**，提升现代感与质感。**信息内容与横向结构保持不变**，只调整容器样式、文字层次、分隔方式。

非目标：
- 不增删字段，不改信息顺序
- 不改横向布局结构（封面在左、信息在右）
- 不修改共享控件 `LabelsBar` / `AccentBgTextView` 的样式（避免外溢到“发现”页）

## 关键约束（来自代码核实）

1. **`item_search.xml`（`ItemSearchBinding`）被两处共用**：
   - `SearchAdapter`（搜索结果页）
   - `ExploreShowAdapter`（发现展示页）
   两个 adapter 都调用 `rootCard.setCardBackgroundColor(context.cardBackgroundColor)`。改布局必须同步处理这两处对 `rootCard` 的引用。

2. **分类标签是共享控件**：`LabelsBar` 内部用 `AccentBgTextView`（实心强调色块，蓝底白字），发现页也在用。本次**不动**它，分类标签维持实心蓝块。

3. **封面 `CoverImageView` 已是 12dp 圆角**（硬编码，`clipPath` 裁剪），但**无阴影**。加贴合圆角的阴影需要 outlineProvider，列为可选增强，默认不依赖。

4. **现有可复用主题变量**（明/暗双主题均已定义，自动适配暗色）：
   - `@color/primaryText`：书名（87% 黑 / 全白）
   - `@color/secondaryText`：作者、最新章节（54% 黑 / 70% 白）
   - `@color/tv_text_summary`：简介（#8A2C2C2C / #B3B3B3）
   - `@color/divider`：分隔线（#33666666，半透明，本身适配暗色）

5. **RecyclerView 当前无 ItemDecoration**（`SearchActivity` 第 219-231 行）。分隔线放在 item 布局内部最简单，可同时作用于搜索页与发现页。

## 设计方案

### 容器：去卡片化

根节点从 `MaterialCardView` 换成普通 `ConstraintLayout`（当前 card 内已嵌一层 ConstraintLayout，直接合并为根）：

- 移除 `cardElevation`、`cardCornerRadius`、`cardBackgroundColor`、`strokeWidth`、`app:strokeWidth` 等 card 属性
- 根布局 `android:background="?android:attr/selectableItemBackground"`（保留点击涟漪）
- 背景透明，继承 RecyclerView 背景
- `id` 仍保留为 `root_card`（或改名），以最小化 adapter 改动；若改名需同步更新引用

**Adapter 改动**：`SearchAdapter` 与 `ExploreShowAdapter` 中的 `rootCard.setCardBackgroundColor(...)` 调用需删除（根不再是 card）。需确认 `ExploreShowAdapter` 其他逻辑不依赖 `rootCard` 类型。

### 文字三级灰度（质感核心）

当前所有文字都是 `primaryText`（87% 黑），糊成一团。改为三级：

| 元素 | 颜色变量 | 效果 |
|------|----------|------|
| 书名 `tv_name` | `primaryText` | 87% 黑 / 全白，最重 |
| 作者 `tv_author` | `secondaryText` | 54% 黑 / 70% 白 |
| 最新章节 `tv_lasted` | `secondaryText` | 54% 黑 / 70% 白 |
| 简介 `tv_introduce` | `tv_text_summary` | 最淡 |

### 分隔线（item 之间）

极淡分隔线，**对齐文字起点**（左侧缩进到封面右缘，不画在封面下方）：

- 颜色 `@color/divider`
- 高度 1dp（`@dimen` 或直接 0.5dp/1dp）
- 左缩进 = 封面宽度 + 边距（约 96dp，对齐 `tv_name` 起点）
- 实现方式二选一（实现阶段定）：
  - **A**：在 item 布局底部加一个 1dp 的 `View`，`marginStart` 对齐文字
  - **B**：给 RecyclerView 加 `ItemDecoration` 画 inset 分隔线
  - 倾向 A（随布局走，发现页自动生效，不必改两个 Activity）

### 封面阴影（可选增强）

封面已有 12dp 圆角。可选给 `CoverImageView` 设 `ViewOutlineProvider` + `elevation`，得到贴合圆角的极淡投影，强化“唯一立体元素”。默认不做，作为后续可选项。

### 间距

- item 内padding：上下 12dp、左右 12dp
- 封面与信息间距：12dp
- 信息内各行垂直间距：约 7-8dp（比当前 3dp 更舒展）

## 受影响文件

- `app/src/main/res/layout/item_search.xml` — 根容器去卡片化、文字颜色分级、加分隔线 View、调间距
- `app/src/main/java/io/legado/app/ui/book/search/SearchAdapter.kt` — 删 `rootCard.setCardBackgroundColor`
- `app/src/main/java/io/legado/app/ui/book/explore/ExploreShowAdapter.kt` — 删 `rootCard.setCardBackgroundColor`，确认无其他 card 依赖
- （可选）`CoverImageView.kt` — outlineProvider 阴影

## 测试与验收

- 搜索页：列表项无边框、分隔线极淡且对齐文字、文字三级层次清晰
- 发现页（共用布局）：同样正常显示，无 card 残留样式
- 暗色主题：分隔线、文字层次自动适配，无硬编码色突兀
- 长列表滚动：分隔线渲染正确，无性能问题
- 封面圆角、默认封面绘制、点击涟漪、徽标/在架绿点位置不回归

## 风险

- `ExploreShowAdapter` 可能依赖 `rootCard` 做其他设置 —— 实现前需通读该 adapter 完整 `convert` 逻辑
- 去掉 `MaterialCardView` 后若有地方按 card 类型引用 binding.rootCard，会编译错误 —— 已知仅 `setCardBackgroundColor`，需逐一处理
