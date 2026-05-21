package io.legado.app.ui.autoTask

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityAutoTaskEditBinding
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.AutoTask
import io.legado.app.model.AutoTaskRule
import io.legado.app.ui.book.source.edit.BookSourceEditAdapter
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.keyboard.KeyboardToolPop
import io.legado.app.ui.widget.recycler.NoChildScrollLinearLayoutManager
import io.legado.app.ui.widget.text.EditEntity
import io.legado.app.ui.widget.dialog.WebCodeDialog
import io.legado.app.utils.CronSchedule
import io.legado.app.utils.GSON
import io.legado.app.utils.imeHeight
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class AutoTaskEditActivity :
    VMBaseActivity<ActivityAutoTaskEditBinding, AutoTaskEditViewModel>(),
    KeyboardToolPop.CallBack,
    WebCodeDialog.Callback {

    companion object {
        fun startIntent(context: Context, id: String? = null): Intent {
            return Intent(context, AutoTaskEditActivity::class.java).apply {
                if (!id.isNullOrBlank()) {
                    putExtra("id", id)
                }
            }
        }
    }

    override val binding by viewBinding(ActivityAutoTaskEditBinding::inflate)
    override val viewModel by viewModels<AutoTaskEditViewModel>()

    private val webEditRequests = linkedMapOf<String, EditEntity>()
    private val adapter = BookSourceEditAdapter { entity ->
        val requestId = java.util.UUID.randomUUID().toString()
        if (
            WebCodeDialog.show(
                supportFragmentManager,
                entity.value.orEmpty(),
                requestId = requestId,
                title = entity.hint
            )
        ) {
            webEditRequests[requestId] = entity
        }
    }
    private val fieldMap = linkedMapOf<String, EditEntity>()
    private val entities: ArrayList<EditEntity> = ArrayList()
    private var task: AutoTaskRule? = null
    private var originTask: AutoTaskRule? = null
    private var selectedNavIndex = 0
    private var navClickScrolling = false
    private val softKeyboardTool by lazy {
        KeyboardToolPop(this, lifecycleScope, binding.root, this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        softKeyboardTool.attachToWindow(window)
        initView()
        viewModel.initData(intent) {
            task = it
            upView(it)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.auto_task_edit, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        val loginUrl = getFieldValue("loginUrl")
        menu.findItem(R.id.menu_login)?.let {
            it.isVisible = true
            it.isEnabled = loginUrl.isNotBlank()
        }
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> {
                val rule = buildTask() ?: return true
                viewModel.save(rule) {
                    originTask = rule.copy()
                    setResult(RESULT_OK)
                    finish()
                }
            }
            R.id.menu_debug_task -> {
                val rule = buildTask() ?: return true
                viewModel.save(rule) {
                    originTask = rule.copy()
                    startActivity(AutoTaskDebugActivity.startIntent(this, rule.id))
                }
            }
            R.id.menu_login -> openLogin()
            R.id.menu_copy_source -> sendToClip(GSON.toJson(buildTaskDraft()))
            R.id.menu_paste_source -> viewModel.pasteSource { upView(it) }
            R.id.menu_help -> showHelpDialog()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = NoChildScrollLinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.root.setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
            softKeyboardTool.initialPadding = windowInsets.imeHeight
            windowInsets
        }
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    navClickScrolling = false
                }
            }

            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (navClickScrolling) return
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val firstVisible = lm.findFirstVisibleItemPosition()
                if (firstVisible >= 0 && firstVisible != selectedNavIndex) {
                    highlightNavItem(firstVisible)
                }
            }
        })
    }

    private fun upView(rule: AutoTaskRule) {
        originTask = rule.copy()
        binding.cbEnable.isChecked = rule.enable
        binding.cbCookie.isChecked = rule.enabledCookieJar
        fieldMap.clear()
        entities.clear()
        addField("name", rule.name, R.string.name)
        addField("cron", rule.cron?.ifBlank { AutoTask.DEFAULT_CRON }, R.string.auto_task_cron)
        addField("comment", rule.comment, R.string.auto_task_comment)
        addField("script", rule.script, R.string.auto_task_script)
        addField("header", rule.header, R.string.auto_task_header)
        addField("jsLib", rule.jsLib, R.string.auto_task_jslib)
        addField("concurrentRate", rule.concurrentRate, R.string.auto_task_concurrent_rate)
        addField("loginUrl", rule.loginUrl, R.string.login_url)
        addField("loginUi", rule.loginUi, R.string.login_ui)
        addField("loginCheckJs", rule.loginCheckJs, R.string.login_check_js)
        adapter.editEntities = entities
        updateFieldNav(entities)
    }

    private fun addField(key: String, value: String?, hintRes: Int) {
        val entity = EditEntity(key, value, hintRes)
        fieldMap[key] = entity
        entities.add(entity)
    }

    private fun getFieldValue(key: String): String {
        return fieldMap[key]?.value?.trim().orEmpty()
    }

    private fun updateFieldNav(entities: List<EditEntity>) {
        val container = binding.fieldNavGroup
        container.removeAllViews()
        entities.forEachIndexed { index, entity ->
            val label = entity.hint.replace(Regex("[（(].+?[）)]"), "").trim()
            val tv = TextView(this).apply {
                text = label
                textSize = 12f
                setPadding(24, 8, 24, 8)
                setBackgroundResource(R.drawable.bg_field_nav_item)
                setTextColor(context.getColor(R.color.primaryText))
                gravity = android.view.Gravity.CENTER
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = 6
                layoutParams = lp
                setOnClickListener {
                    navClickScrolling = true
                    val lm = binding.recyclerView.layoutManager as? LinearLayoutManager
                    if (lm != null) {
                        val scroller = object : androidx.recyclerview.widget.LinearSmoothScroller(context) {
                            override fun getVerticalSnapPreference() = SNAP_TO_START
                            override fun calculateDtToFit(
                                viewStart: Int, viewEnd: Int,
                                boxStart: Int, boxEnd: Int,
                                snapPreference: Int
                            ): Int {
                                val offset = (resources.displayMetrics.density * 4).toInt()
                                return boxStart - viewStart + offset
                            }
                        }
                        scroller.targetPosition = index
                        lm.startSmoothScroll(scroller)
                    }
                    highlightNavItem(index)
                }
            }
            container.addView(tv)
        }
        highlightNavItem(0)
    }

    private fun highlightNavItem(index: Int) {
        val container = binding.fieldNavGroup
        if (selectedNavIndex in 0 until container.childCount) {
            val prev = container.getChildAt(selectedNavIndex) as? TextView
            prev?.isSelected = false
            prev?.setTextColor(getColor(R.color.primaryText))
        }
        if (index in 0 until container.childCount) {
            val curr = container.getChildAt(index) as? TextView
            curr?.isSelected = true
            curr?.setTextColor(android.graphics.Color.WHITE)
            curr?.let {
                binding.fieldNavScroll.smoothScrollTo(
                    (it.left - 16).coerceAtLeast(0), 0
                )
            }
        }
        selectedNavIndex = index
    }

    private fun buildTask(): AutoTaskRule? {
        val name = getFieldValue("name")
        if (name.isBlank()) {
            toastOnUi(getString(R.string.auto_task_name_required))
            return null
        }
        val cron = getFieldValue("cron").ifBlank { AutoTask.DEFAULT_CRON }
        if (CronSchedule.parse(cron) == null) {
            toastOnUi(getString(R.string.auto_task_cron_invalid))
            return null
        }
        val script = getFieldValue("script")
        if (script.isBlank()) {
            toastOnUi(getString(R.string.auto_task_script_empty))
            return null
        }
        val rule = task ?: AutoTaskRule()
        rule.name = name
        rule.cron = cron
        rule.comment = getFieldValue("comment").ifBlank { null }
        rule.script = script
        rule.header = getFieldValue("header").ifBlank { null }
        rule.jsLib = getFieldValue("jsLib").ifBlank { null }
        rule.concurrentRate = getFieldValue("concurrentRate").ifBlank { null }
        rule.loginUrl = getFieldValue("loginUrl").ifBlank { null }
        rule.loginUi = getFieldValue("loginUi").ifBlank { null }
        rule.loginCheckJs = getFieldValue("loginCheckJs").ifBlank { null }
        rule.enable = binding.cbEnable.isChecked
        rule.enabledCookieJar = binding.cbCookie.isChecked
        task = rule
        return rule
    }

    private fun buildTaskDraft(): AutoTaskRule {
        val base = originTask ?: task ?: AutoTaskRule()
        return base.copy(
            name = getFieldValue("name"),
            cron = getFieldValue("cron").ifBlank { AutoTask.DEFAULT_CRON },
            comment = getFieldValue("comment").ifBlank { null },
            script = getFieldValue("script"),
            header = getFieldValue("header").ifBlank { null },
            jsLib = getFieldValue("jsLib").ifBlank { null },
            concurrentRate = getFieldValue("concurrentRate").ifBlank { null },
            loginUrl = getFieldValue("loginUrl").ifBlank { null },
            loginUi = getFieldValue("loginUi").ifBlank { null },
            loginCheckJs = getFieldValue("loginCheckJs").ifBlank { null },
            enable = binding.cbEnable.isChecked,
            enabledCookieJar = binding.cbCookie.isChecked
        )
    }

    override fun finish() {
        val base = originTask ?: task ?: AutoTaskRule()
        val current = buildTaskDraft()
        if (current != base) {
            alert(R.string.exit) {
                setMessage(R.string.exit_no_save)
                positiveButton(R.string.yes)
                negativeButton(R.string.no) {
                    super.finish()
                }
            }
        } else {
            super.finish()
        }
    }

    private fun openLogin() {
        val rule = buildTask() ?: return
        val loginUrl = rule.loginUrl.orEmpty()
        if (loginUrl.isBlank()) {
            toastOnUi(getString(R.string.source_no_login))
            return
        }
        viewModel.save(rule) {
            startActivity<SourceLoginActivity> {
                putExtra("type", "autoTask")
                putExtra("key", rule.id)
            }
        }
    }

    private fun showHelpDialog() {
        showHelp("autoTaskHelp")
    }

    override fun helpActions(): List<SelectItem<String>> {
        return arrayListOf()
    }

    override fun onHelpActionSelect(action: String) {
    }

    override fun sendText(text: String) {
        if (text.isBlank()) return
        val view = window?.decorView?.findFocus()
        if (view is EditText) {
            val start = view.selectionStart
            val end = view.selectionEnd
            val edit = view.editableText
            if (start < 0 || start >= edit.length) {
                edit.append(text)
            } else if (start > end) {
                edit.replace(end, start, text)
            } else {
                edit.replace(start, end, text)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        softKeyboardTool.dismiss()
    }

    override fun onCodeSave(code: String, requestId: String?) {
        val entity = requestId?.let { webEditRequests.remove(it) } ?: return
        entity.value = code
        val index = entities.indexOf(entity)
        if (index >= 0) {
            adapter.notifyItemChanged(index)
        }
    }
}
