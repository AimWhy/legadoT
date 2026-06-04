# 搜索结果卡片重设计（无边框列表）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把搜索结果列表项从带阴影的 `MaterialCardView` 改为无边框列表项（极淡分隔线 + 文字三级灰度），同时保持发现页（共用布局）正常。

**Architecture:** 单一原子改动——重写 `item_search.xml` 的根容器（`MaterialCardView` → `ConstraintLayout`）并调整文字颜色/间距/加分隔线；同步删除 `SearchAdapter` 与 `ExploreShowAdapter` 中对 `rootCard.setCardBackgroundColor(...)` 的调用（容器不再是 card，否则编译失败）。三处必须一起落地才能编译通过。

**Tech Stack:** Android ViewBinding、ConstraintLayout、Material 主题色变量（`primaryText` / `secondaryText` / `tv_text_summary` / `divider`，明暗双主题已定义）。

**关于测试：** 这是 XML 布局 + adapter 改动，无既有 UI 截图/单元测试设施。验收门槛是 `./gradlew assembleAppDebug` 编译通过（会捕获 `rootCard` 类型错误）+ 人工视觉检查（由用户在 App 内完成）。不编造 JUnit 测试。

---

## File Structure

- `app/src/main/res/layout/item_search.xml` — **重写**。根从 `MaterialCardView` 换成 `ConstraintLayout`；文字三级灰度；行间距加大；底部加 inset 分隔线。搜索页与发现页共用此布局。
- `app/src/main/java/io/legado/app/ui/book/search/SearchAdapter.kt` — **改 1 行**。删除 `rootCard.setCardBackgroundColor(context.cardBackgroundColor)`。
- `app/src/main/java/io/legado/app/ui/book/explore/ExploreShowAdapter.kt` — **改 1 行**。删除同样的调用。

---

## Task 1: 列表项去卡片化（无边框 + 分隔线 + 文字层次）

**Files:**
- Modify: `app/src/main/res/layout/item_search.xml`（整文件替换）
- Modify: `app/src/main/java/io/legado/app/ui/book/search/SearchAdapter.kt:85`
- Modify: `app/src/main/java/io/legado/app/ui/book/explore/ExploreShowAdapter.kt:44`

**说明：** 三处改动必须一起提交。只改布局会让 `binding.rootCard`（现为 `ConstraintLayout`）失去 `setCardBackgroundColor` 方法，两个 adapter 编译失败。保留根 `id="@+id/root_card"` 以最小化引用改动。

- [ ] **Step 1: 整文件替换 `item_search.xml`**

把 `app/src/main/res/layout/item_search.xml` 全部内容替换为：

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/root_card"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?android:attr/selectableItemBackground"
    android:scrollbars="none">

    <io.legado.app.ui.widget.image.CoverImageView
        android:id="@+id/iv_cover"
        android:layout_width="80dp"
        android:layout_height="110dp"
        android:layout_marginStart="12dp"
        android:layout_marginTop="12dp"
        android:layout_marginBottom="12dp"
        android:contentDescription="@string/img_cover"
        android:scaleType="centerCrop"
        android:scrollbars="none"
        android:src="@drawable/image_cover_default"
        android:transitionName="img_cover"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        tools:ignore="UnusedAttribute" />

    <io.legado.app.ui.widget.image.CircleImageView
        android:id="@+id/iv_in_bookshelf"
        android:layout_width="8dp"
        android:layout_height="8dp"
        android:layout_marginStart="12dp"
        android:scaleType="centerCrop"
        android:scrollbars="none"
        android:src="@color/md_green_600"
        android:visibility="invisible"
        app:layout_constraintLeft_toRightOf="@id/iv_cover"
        app:layout_constraintTop_toTopOf="@id/tv_name"
        app:layout_constraintBottom_toBottomOf="@id/tv_name" />

    <io.legado.app.ui.widget.text.BadgeView
        android:id="@+id/bv_originCount"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginEnd="12dp"
        android:scrollbars="none"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="@id/tv_name"
        app:layout_constraintBottom_toBottomOf="@id/tv_name" />

    <TextView
        android:id="@+id/tv_name"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="6dp"
        android:layout_marginTop="12dp"
        android:scrollbars="none"
        android:singleLine="true"
        android:text="@string/app_name"
        android:textColor="@color/primaryText"
        android:textSize="16sp"
        android:textStyle="bold"
        app:layout_constraintEnd_toStartOf="@id/bv_originCount"
        app:layout_constraintStart_toEndOf="@id/iv_in_bookshelf"
        app:layout_constraintTop_toTopOf="parent" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginStart="12dp"
        android:layout_marginTop="8dp"
        android:orientation="vertical"
        android:scrollbars="none"
        app:layout_constraintBottom_toBottomOf="@id/iv_cover"
        app:layout_constraintLeft_toRightOf="@id/iv_cover"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toBottomOf="@id/tv_name">

        <TextView
            android:id="@+id/tv_author"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:ellipsize="end"
            android:lines="1"
            android:scrollbars="none"
            android:text="@string/author"
            android:textColor="@color/secondaryText"
            android:textSize="12sp" />

        <io.legado.app.ui.widget.LabelsBar
            android:id="@+id/ll_kind"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:orientation="horizontal"
            android:scrollbars="none" />

        <TextView
            android:id="@+id/tv_lasted"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:ellipsize="end"
            android:lines="1"
            android:scrollbars="none"
            android:text="@string/last_read"
            android:textColor="@color/secondaryText"
            android:textSize="12sp" />

        <io.legado.app.ui.widget.text.MultilineTextView
            android:id="@+id/tv_introduce"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:layout_marginTop="6dp"
            android:ellipsize="end"
            android:scrollbars="none"
            android:text="@string/book_intro"
            android:textColor="@color/tv_text_summary"
            android:textSize="12sp" />

    </LinearLayout>

    <View
        android:id="@+id/v_divider"
        android:layout_width="0dp"
        android:layout_height="1dp"
        android:layout_marginStart="104dp"
        android:background="@color/divider"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

变更要点：根 `MaterialCardView`→`ConstraintLayout`（去边框/阴影）；`tv_name` 加 `textStyle="bold"`；`tv_author`/`tv_lasted` 颜色 `primaryText`→`secondaryText`；`tv_introduce` 颜色→`tv_text_summary`；行间距由紧凑改为 6-8dp；底部新增 `v_divider`（1dp，`@color/divider`，左缩进 104dp 对齐文字起点）。

- [ ] **Step 2: 删除 `SearchAdapter` 的 card 背景调用**

在 `app/src/main/java/io/legado/app/ui/book/search/SearchAdapter.kt` 的 `bind` 方法中删除这一行（约第 85 行）：

```kotlin
            rootCard.setCardBackgroundColor(context.cardBackgroundColor)
```

删除后 `bind` 开头变为：

```kotlin
    private fun bind(binding: ItemSearchBinding, searchBook: SearchBook) {
        binding.run {
            tvName.text = searchBook.name
            tvAuthor.text = context.getString(R.string.author_show, searchBook.author)
```

若 IDE 提示 `import io.legado.app.lib.theme.cardBackgroundColor` 变为未使用，一并删除该 import（第 14 行）。

- [ ] **Step 3: 删除 `ExploreShowAdapter` 的 card 背景调用**

在 `app/src/main/java/io/legado/app/ui/book/explore/ExploreShowAdapter.kt` 的 `bind` 方法中删除这一行（约第 44 行）：

```kotlin
            rootCard.setCardBackgroundColor(context.cardBackgroundColor)
```

删除后 `bind` 开头变为：

```kotlin
    private fun bind(binding: ItemSearchBinding, item: SearchBook) {
        binding.run {
            tvName.text = item.name
            tvAuthor.text = context.getString(R.string.author_show, item.author)
```

若 `import io.legado.app.lib.theme.cardBackgroundColor`（第 13 行）变为未使用，一并删除。

- [ ] **Step 4: 编译验证**

Run: `./gradlew assembleAppDebug`
Expected: BUILD SUCCESSFUL。若报错 `unresolved reference: setCardBackgroundColor` 或 `cardBackgroundColor`，说明 Step 2/3 未删干净——回查两个 adapter。

- [ ] **Step 5: 人工视觉验证（由用户在 App 内完成）**

清单：
- 搜索页：列表项无边框/无阴影；item 间为极淡分隔线且左侧对齐书名起点（不画到封面下方）。
- 文字层次：书名最重（加粗），作者/最新章节中等灰，简介最淡。
- 发现页（共用布局）：正常显示，无 card 残留（无双重背景/错位）。
- 暗色主题：分隔线、文字层次自动适配，无突兀硬编码色。
- 不回归：封面 12dp 圆角、默认封面文字绘制、点击涟漪、来源数徽标 `bv_originCount` 与在架绿点 `iv_in_bookshelf` 位置正确。
- 长列表滚动流畅。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/res/layout/item_search.xml \
        app/src/main/java/io/legado/app/ui/book/search/SearchAdapter.kt \
        app/src/main/java/io/legado/app/ui/book/explore/ExploreShowAdapter.kt
git commit -m "feat: 搜索结果改为无边框列表（分隔线+文字分级）"
```

---

## Task 2（可选增强）：封面贴合圆角的极淡阴影

仅在 Task 1 视觉验证后、用户希望封面更有立体感时做。`CoverImageView` 已是 12dp 圆角但用 `clipPath` 裁剪、无阴影。加 `ViewOutlineProvider` 让 `elevation` 投影贴合圆角。

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/widget/image/CoverImageView.kt`

- [ ] **Step 1: 在 `onLayout` 末尾设置 outlineProvider 与 elevation**

在 `CoverImageView.kt` 的 `onLayout`（构建 `filletPath` 之后）追加：

```kotlin
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        clipToOutline = false
        if (elevation == 0f) {
            elevation = 2f.dpToPx()
        }
```

说明：`clipToOutline=false` 保留原 `clipPath` 绘制逻辑不变，outline 仅用于投影形状；`elevation` 仅在未设置时给 2dp。

- [ ] **Step 2: 编译验证**

Run: `./gradlew assembleAppDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 人工验证**

封面出现贴合圆角的极淡投影；列表滚动无明显性能下降；默认封面与网络封面均正常。
注意：`CoverImageView` 全局复用（书架、详情页等），确认其他页面封面阴影可接受；若不可接受，改为不在控件内设 elevation，而在 `item_search.xml` 的 `iv_cover` 上单独加 `android:elevation`。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/io/legado/app/ui/widget/image/CoverImageView.kt
git commit -m "feat: 封面增加贴合圆角的极淡阴影"
```

---

## Self-Review 记录

- **Spec coverage：** 去卡片化（Task1 Step1 根容器）、文字三级灰度（Step1 颜色）、极淡分隔线对齐文字（Step1 `v_divider`，marginStart 104dp）、不动共享 `LabelsBar`（未触碰）、共用布局影响（Step2/3 两 adapter）、封面阴影可选（Task2）——全部覆盖。
- **Placeholder：** 无 TBD/TODO；XML 与 Kotlin 改动均为完整内容。
- **类型一致：** 根 id 保持 `root_card`；删除的方法名 `setCardBackgroundColor` 两处一致；新增 id `v_divider` 不被 adapter 引用，无需绑定逻辑。
- **已核实风险：** `ExploreShowAdapter` 仅在 line 44 通过 `setCardBackgroundColor` 引用 `rootCard`，无其他 card 类型依赖（已读全文件确认）。
