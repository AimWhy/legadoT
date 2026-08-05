@file:Suppress("DEPRECATION")

package io.legado.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.annotation.CallSuper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.RoleCast
import io.legado.app.help.MediaHelp
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.readaloud.RoleAnnotator
import io.legado.app.model.readaloud.RoleCastManager
import io.legado.app.model.readaloud.SpeechScript
import io.legado.app.receiver.MediaButtonReceiver
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.LogUtils
import io.legado.app.utils.activityPendingIntent
import io.legado.app.utils.broadcastPendingIntent
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.observeEvent
import io.legado.app.utils.observeSharedPreferences
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import splitties.init.appCtx
import splitties.systemservices.audioManager
import splitties.systemservices.notificationManager
import splitties.systemservices.powerManager
import splitties.systemservices.telephonyManager
import splitties.systemservices.wifiManager

/**
 * 朗读服务
 */
abstract class BaseReadAloudService : BaseService(),
    AudioManager.OnAudioFocusChangeListener {

    companion object {
        @JvmStatic
        var isRun = false
            private set

        @JvmStatic
        var pause = true
            private set

        @JvmStatic
        var timeMinute: Int = 0
            private set

        /** 听完剩余章数后停止(0=关闭);与 timeMinute 互斥 */
        @JvmStatic
        var chapterToStop: Int = 0
            private set

        fun isPlay(): Boolean {
            return isRun && !pause
        }

        private const val TAG = "BaseReadAloudService"
        private val speechFollowState = SpeechFollowState()

        /** 当前朗读所在章节索引(朗读游标),脱离跟随后与显示章节可能不同; -1 表示无 */
        @JvmStatic
        @Volatile
        var readAloudChapterIndex: Int = -1
            private set

        /** 当前朗读位置在章内的字符偏移(朗读游标); -1 表示无。由 upTtsProgress 持续更新, 进程内存活。 */
        @JvmStatic
        @Volatile
        var readAloudChapterStart: Int = -1
            private set

        @JvmStatic
        val followReadAloudPosition: Boolean
            get() = speechFollowState.followReadAloudPosition

        @JvmStatic
        fun detachReadAloudFollow() {
            speechFollowState.detachForManualNavigation()
            postEvent(EventBus.READ_ALOUD_FOLLOW, speechFollowState.followReadAloudPosition)
        }

        @JvmStatic
        fun restoreReadAloudFollow() {
            speechFollowState.restoreForNewSpeechSession()
            postEvent(EventBus.READ_ALOUD_FOLLOW, speechFollowState.followReadAloudPosition)
        }

        @JvmStatic
        fun shouldSyncSpeechNavigation(): Boolean {
            return speechFollowState.shouldSyncSpeechNavigation()
        }

        @JvmStatic
        internal fun updateReadAloudChapterIndex(index: Int) {
            readAloudChapterIndex = index
        }

        @JvmStatic
        fun shouldApplySpeechProgressToVisibleReader(isSpeechPlaying: Boolean): Boolean {
            return speechFollowState.shouldApplySpeechProgressToVisibleReader(isSpeechPlaying)
        }

        @JvmStatic
        fun nextChapterDecision(
            hasNextSpeechChapter: Boolean,
            visibleSyncMoved: Boolean
        ): SpeechFollowState.NextChapterDecision {
            return speechFollowState.nextChapterDecision(
                hasNextSpeechChapter = hasNextSpeechChapter,
                visibleSyncMoved = visibleSyncMoved
            )
        }
    }

    private val useWakeLock = appCtx.getPrefBoolean(PreferKey.readAloudWakeLock, false)
    private val wakeLock by lazy {
        powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "legado:ReadAloudService")
            .apply {
                this.setReferenceCounted(false)
            }
    }
    private val wifiLock by lazy {
        @Suppress("DEPRECATION")
        wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "legado:AudioPlayService")
            ?.apply {
                setReferenceCounted(false)
            }
    }
    private val mFocusRequest: AudioFocusRequestCompat by lazy {
        MediaHelp.buildAudioFocusRequestCompat(this)
    }
    private val mediaSessionCompat: MediaSessionCompat by lazy {
        MediaSessionCompat(this, "readAloud")
    }
    private val phoneStateListener by lazy {
        ReadAloudPhoneStateListener()
    }
    internal var contentList = emptyList<String>()
    internal var nowSpeak: Int = 0
    /** 段内片段游标, 与 nowSpeak 一起构成朗读位置 */
    internal var nowSegment: Int = 0
    @Volatile
    internal var speechScript: SpeechScript? = null
    /** 旁白 casting 的缓存, 由 [prepareSpeechScript] 在 IO 上下文写入 */
    @Volatile
    private var speechNarratorCast: RoleCast? = null
    /** 角色分析期间的通知副标题状态位, 下载协程写、通知协程读 */
    @Volatile
    private var analyzingRoles = false
    internal var readAloudNumber: Int = 0
    internal var textChapter: TextChapter? = null
    internal var pageIndex = 0
    private var needResumeOnAudioFocusGain = false
    private var needResumeOnCallStateIdle = false
    private var registeredPhoneStateListener = false
    private var dsJob: Job? = null
    private var upNotificationJob: Coroutine<*>? = null
    private var cover: Bitmap =
        BitmapFactory.decodeResource(appCtx.resources, R.drawable.icon_read_book)
    var pageChanged = false
    private var toLast = false
    var paragraphStartPos = 0
    var readAloudByPage = false
        private set

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                pauseReadAloud()
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        isRun = true
        pause = false
        restoreReadAloudFollow()
        observeLiveBus()
        initMediaSession()
        initBroadcastReceiver()
        initPhoneStateListener()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        setTimer(AppConfig.ttsTimer)
        if (AppConfig.ttsTimer > 0) {
            toastOnUi("朗读定时 ${AppConfig.ttsTimer} 分钟")
        }
        execute {
            ImageLoader
                .loadBitmap(this@BaseReadAloudService, ReadBook.book?.getDisplayCover())
                .submit()
                .get()
        }.onSuccess {
            if (it.width > 16 && it.height > 16) {
                cover = it
                upReadAloudNotification()
            }
        }
    }

    fun observeLiveBus() {
        observeEvent<Bundle>(EventBus.READ_ALOUD_PLAY) {
            val play = it.getBoolean("play")
            val pageIndex = it.getInt("pageIndex")
            val startPos = it.getInt("startPos")
            newReadAloud(play, pageIndex, startPos)
        }
        observeEvent<String>(EventBus.ROLE_CAST_CHANGED) { bookUrl ->
            if (ReadBook.book?.bookUrl == bookUrl) onRoleCastChanged()
        }
        observeSharedPreferences { _, key ->
            when (key) {
                PreferKey.ignoreAudioFocus,
                PreferKey.pauseReadAloudWhilePhoneCalls -> {
                    initPhoneStateListener()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        restoreReadAloudFollow()
        updateReadAloudChapterIndex(-1)
        readAloudChapterStart = -1
        analyzingRoles = false
        if (useWakeLock) {
            wakeLock.release()
            wifiLock?.release()
        }
        isRun = false
        pause = true
        abandonFocus()
        unregisterReceiver(broadcastReceiver)
        postEvent(EventBus.ALOUD_STATE, Status.STOP)
        notificationManager.cancel(NotificationId.ReadAloudService)
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        mediaSessionCompat.release()
        ReadBook.uploadProgress()
        unregisterPhoneStateListener(phoneStateListener)
        upNotificationJob?.invokeOnCompletion {
            notificationManager.cancel(NotificationId.ReadAloudService)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.play -> newReadAloud(
                intent.getBooleanExtra("play", true),
                intent.getIntExtra("pageIndex", ReadBook.durPageIndex),
                intent.getIntExtra("startPos", 0)
            )

            IntentAction.pause -> pauseReadAloud()
            IntentAction.resume -> resumeReadAloud()
            IntentAction.upTtsSpeechRate -> upSpeechRate(true)
            IntentAction.prevParagraph -> prevP()
            IntentAction.nextParagraph -> nextP()
            IntentAction.prev -> prevChapter()
            IntentAction.next -> nextChapter()
            IntentAction.addTimer -> addTimer()
            IntentAction.setTimer -> setTimer(intent.getIntExtra("minute", 0))
            IntentAction.setChapterStop -> setChapterStop(intent.getIntExtra("count", 0))
            IntentAction.stop -> stopSelf()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun newReadAloud(play: Boolean, pageIndex: Int, startPos: Int) {
        restoreReadAloudFollow()
        execute(executeContext = IO) {
            this@BaseReadAloudService.pageIndex = pageIndex
            textChapter = ReadBook.curTextChapter
            val textChapter = textChapter ?: return@execute
            if (!textChapter.isCompleted) {
                return@execute
            }
            updateReadAloudChapterIndex(textChapter.chapter.index)
            readAloudNumber = textChapter.getReadLength(pageIndex) + startPos
            readAloudByPage = getPrefBoolean(PreferKey.readAloudByPage)
            contentList = textChapter.getNeedReadAloud(0, readAloudByPage, 0)
                .split("\n")
                .filter { it.isNotEmpty() }
            var pos = startPos
            val page = textChapter.getPage(pageIndex)!!
            if (pos > 0) {
                for (paragraph in page.paragraphs) {
                    val tmp = pos - paragraph.length - 1
                    if (tmp < 0) break
                    pos = tmp
                }
            }
            nowSpeak = textChapter.getParagraphNum(readAloudNumber + 1, readAloudByPage) - 1
            nowSegment = 0
            resetSpeechScript()
            if (!readAloudByPage && startPos == 0 && !toLast) {
                pos = page.chapterPosition -
                        textChapter.paragraphs[nowSpeak].chapterPosition
            }
            if (toLast) {
                toLast = false
                readAloudNumber = textChapter.getLastParagraphPosition()
                nowSpeak = contentList.lastIndex
                nowSegment = 0
                if (page.paragraphs.size == 1) {
                    pos = page.chapterPosition -
                            textChapter.paragraphs[nowSpeak].chapterPosition
                }
            }
            paragraphStartPos = pos
            launch(Main) {
                if (play) play() else pageChanged = true
            }
        }.onError {
            AppLog.put("启动朗读出错\n${it.localizedMessage}", it, true)
        }
    }

    @SuppressLint("WakelockTimeout")
    open fun play() {
        if (useWakeLock) {
            wakeLock.acquire()
            wifiLock?.acquire()
        }
        isRun = true
        pause = false
        needResumeOnAudioFocusGain = false
        needResumeOnCallStateIdle = false
        upReadAloudNotification()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        postEvent(EventBus.ALOUD_STATE, Status.PLAY)
    }

    abstract fun playStop()

    @CallSuper
    open fun pauseReadAloud(abandonFocus: Boolean = true) {
        if (useWakeLock) {
            wakeLock.release()
            wifiLock?.release()
        }
        pause = true
        if (abandonFocus) {
            abandonFocus()
        }
        upReadAloudNotification()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED)
        postEvent(EventBus.ALOUD_STATE, Status.PAUSE)
        ReadBook.uploadProgress()
        doDs()
    }

    @SuppressLint("WakelockTimeout")
    @CallSuper
    open fun resumeReadAloud() {
        resumeReadAloudInternal()
    }

    private fun resumeReadAloudInternal() {
        pause = false
        needResumeOnAudioFocusGain = false
        needResumeOnCallStateIdle = false
        upReadAloudNotification()
        upMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        postEvent(EventBus.ALOUD_STATE, Status.PLAY)
    }

    abstract fun upSpeechRate(reset: Boolean = false)

    fun upTtsProgress(progress: Int) {
        readAloudChapterStart = progress
        postEvent(EventBus.TTS_PROGRESS, progress)
    }

    /** 分析期通知副标题改显角色分析中, 状态不变时不重发通知 */
    private fun upAnalyzingRoles(analyzing: Boolean) {
        if (analyzingRoles == analyzing) return
        analyzingRoles = analyzing
        upReadAloudNotification()
    }

    /**
     * 未标注或标注失败时退化为每段一个旁白片段。
     * 播放回调线程直接调用, 只读 [speechNarratorCast] 缓存, 不触库。
     */
    internal fun currentScript(): SpeechScript {
        speechScript?.let { return it }
        val fallback = speechNarratorCast ?: RoleCast(roleName = RoleCast.NARRATOR)
        return SpeechScript.narratorOnly(contentList, fallback).also { speechScript = it }
    }

    /**
     * 脚本就绪的唯一挂起入口: 在 IO 上下文备好旁白 casting 与角色标注, 之后 [currentScript] 只走缓存。
     * 标注前先落纯旁白脚本, 覆盖掉播放回调路径可能用占位 casting 建成的那份,
     * 分析期间回调线程取到的也是可播序列。
     */
    internal suspend fun prepareSpeechScript() {
        if (speechNarratorCast != null) {
            currentScript()
            return
        }
        val cast = resolveNarratorCast()
        val paragraphs = contentList
        speechScript = SpeechScript.narratorOnly(paragraphs, cast)
        upAnalyzingRoles(AppConfig.multiRoleReadAloud)
        val script = try {
            buildScriptFor(
                textChapter?.chapter?.index ?: -1,
                paragraphs,
                cast,
                notifyFailure = true
            )
        } finally {
            upAnalyzingRoles(false)
        }
        // 标注跨越换章时 contentList 已换新, 旧段落表建出的脚本作废, 留给新一轮重建
        if (contentList !== paragraphs) return
        speechScript = script
        speechNarratorCast = cast
        // 「从这里朗读」的 paragraphStartPos 可落在段中, 起播片段取覆盖它的那个。
        // 纯旁白脚本每段恰好一个片段, segmentIndexAt 恒得 0, 游标保持 newReadAloud 置的初值。
        nowSegment = script.segmentIndexAt(nowSpeak, paragraphStartPos)
    }

    /**
     * 四道降级: 无书 / 开关关闭 / 章号未知 / 标注失败, 一律退化为每段一个旁白片段。
     * casting 取库抛错走同一条退化路径, 朗读不因标注链路中断; 取消照旧向上传播。
     *
     * @param fallback 未知角色所用的 casting, 即旁白
     */
    internal suspend fun buildScriptFor(
        chapterIndex: Int,
        paragraphs: List<String>,
        fallback: RoleCast,
        notifyFailure: Boolean = false
    ): SpeechScript {
        val narratorOnly = SpeechScript.narratorOnly(paragraphs, fallback)
        val book = ReadBook.book
        if (book == null || !AppConfig.multiRoleReadAloud || chapterIndex < 0) {
            return narratorOnly
        }
        return try {
            val annotated = RoleAnnotator.annotate(book.bookUrl, chapterIndex, paragraphs)
                ?: return narratorOnly.also {
                    if (notifyFailure) toastOnUi(R.string.role_annotation_fallback)
                }
            val canonical = RoleCastManager.canonicalize(book.bookUrl, annotated)
            RoleCastManager.ensureCast(book.bookUrl, canonical.roles, chapterIndex)
            SpeechScript(
                paragraphs = paragraphs,
                segments = canonical.segments,
                cast = RoleCastManager.castOf(book.bookUrl),
                fallback = fallback
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("角色脚本构建失败\n${e.localizedMessage}", e)
            if (notifyFailure) toastOnUi(R.string.role_annotation_fallback)
            narratorOnly
        }
    }

    /** 取库失败退化为占位 casting, 朗读照常起播; 取消照旧向上传播 */
    private suspend fun resolveNarratorCast(): RoleCast {
        val book = ReadBook.book ?: return RoleCast(roleName = RoleCast.NARRATOR)
        return try {
            RoleCastManager.narratorCast(book.bookUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("旁白配音读取失败\n${e.localizedMessage}", e)
            RoleCast(roleName = RoleCast.NARRATOR)
        }
    }

    /** 章节或朗读列表变更后, 脚本与 casting 缓存一并作废 */
    internal fun resetSpeechScript() {
        speechScript = null
        speechNarratorCast = null
    }

    internal open fun onRoleCastChanged() {
        resetSpeechScript()
    }

    private fun prevP() {
        if (nowSpeak > 0) {
            playStop()
            do {
                nowSpeak--
                readAloudNumber -= contentList[nowSpeak].length + 1 + paragraphStartPos
                paragraphStartPos = 0
            } while (contentList[nowSpeak].matches(AppPattern.notReadAloudRegex))
            nowSegment = 0
            textChapter?.let {
                if (readAloudByPage) {
                    val paragraphs = it.getParagraphs(true)
                    if (!paragraphs[nowSpeak].isParagraphEnd) readAloudNumber++
                }
                if (readAloudNumber < it.getReadLength(pageIndex)) {
                    pageIndex--
                    ReadBook.moveToPrevPage(syncReadAloudFollow = true)
                }
            }
            upTtsProgress(readAloudNumber + 1)
            play()
        } else {
            toLast = true
            ReadBook.moveToPrevChapter(true, syncReadAloudFollow = true)
        }
    }

    private fun nextP() {
        if (nowSpeak < contentList.size - 1) {
            playStop()
            readAloudNumber += contentList[nowSpeak].length.plus(1) - paragraphStartPos
            paragraphStartPos = 0
            nowSpeak++
            nowSegment = 0
            textChapter?.let {
                if (readAloudByPage) {
                    val paragraphs = it.getParagraphs(true)
                    if (!paragraphs[nowSpeak].isParagraphEnd) readAloudNumber--
                }
                if (pageIndex + 1 < it.pageSize
                    && readAloudNumber >= it.getReadLength(pageIndex + 1)
                ) {
                    pageIndex++
                    ReadBook.moveToNextPage(syncReadAloudFollow = true)
                }
            }
            upTtsProgress(readAloudNumber + 1)
            play()
        } else {
            nextChapter()
        }
    }

    private fun setTimer(minute: Int) {
        timeMinute = minute
        chapterToStop = 0
        postEvent(EventBus.READ_ALOUD_CHAPTER, chapterToStop)
        doDs()
    }

    /** 定时+: 集数停止生效时+1集; 空闲时按上次停止模式起步(集数模式起1集); 其余+10分钟(180翻回0) */
    private fun addTimer() {
        if (chapterToStop > 0) {
            setChapterStop(chapterToStop + 1)
            return
        }
        if (timeMinute <= 0 && AppConfig.sleepTimerPreferChapter) {
            setChapterStop(1)
            return
        }
        if (timeMinute == 180) {
            timeMinute = 0
        } else {
            timeMinute += 10
            if (timeMinute > 180) timeMinute = 180
        }
        chapterToStop = 0
        postEvent(EventBus.READ_ALOUD_CHAPTER, chapterToStop)
        doDs()
    }

    /**
     * 按集数停止: 听完剩余 count 章后停止(0=关闭)。与定时互斥, 启用时取消定时。
     */
    private fun setChapterStop(count: Int) {
        chapterToStop = count
        if (count > 0) {
            timeMinute = 0
            dsJob?.cancel()
            postEvent(EventBus.READ_ALOUD_DS, timeMinute)
        }
        postEvent(EventBus.READ_ALOUD_CHAPTER, chapterToStop)
        upReadAloudNotification()
    }

    /**
     * 定时
     */
    @Synchronized
    private fun doDs() {
        postEvent(EventBus.READ_ALOUD_DS, timeMinute)
        upReadAloudNotification()
        dsJob?.cancel()
        dsJob = lifecycleScope.launch {
            while (isActive) {
                delay(60000)
                if (!pause) {
                    if (timeMinute >= 0) {
                        timeMinute--
                    }
                    if (timeMinute == 0) {
                        ReadAloud.stop(this@BaseReadAloudService)
                        postEvent(EventBus.READ_ALOUD_DS, timeMinute)
                        break
                    }
                }
                postEvent(EventBus.READ_ALOUD_DS, timeMinute)
                upReadAloudNotification()
            }
        }
    }

    /**
     * 请求音频焦点
     * @return 音频焦点
     */
    fun requestFocus(): Boolean {
        if (AppConfig.ignoreAudioFocus) {
            return true
        }
        val requestFocus = MediaHelp.requestFocus(mFocusRequest)
        if (!requestFocus) {
            pauseReadAloud(false)
            toastOnUi("未获取到音频焦点")
        }
        return requestFocus
    }

    /**
     * 放弃音频焦点
     */
    private fun abandonFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, mFocusRequest)
    }

    /**
     * 更新媒体状态
     */
    private fun upMediaSessionPlaybackState(state: Int) {
        mediaSessionCompat.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(MediaHelp.MEDIA_SESSION_ACTIONS)
                .setState(state, nowSpeak.toLong(), 1f)
                // 为系统媒体控件添加定时按钮
                .addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                        "ACTION_ADD_TIMER",
                        getString(R.string.set_timer),
                        R.drawable.ic_time_add_24dp
                    ).build()
                )
                .build()
        )
    }

    /**
     * 初始化MediaSession, 注册多媒体按钮
     */
    @SuppressLint("UnspecifiedImmutableFlag")
    private fun initMediaSession() {
        if (getPrefBoolean("systemMediaControlCompatibilityChange")) {
            mediaSessionCompat.setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    resumeReadAloud()
                }

                override fun onPause() {
                    pauseReadAloud()
                }

                override fun onSkipToNext() {
                    if (getPrefBoolean("mediaButtonPerNext", false)) {
                        nextChapter()
                    } else {
                        nextP()
                    }
                }

                override fun onSkipToPrevious() {
                    if (getPrefBoolean("mediaButtonPerNext", false)) {
                        prevChapter()
                    } else {
                        prevP()
                    }
                }

                override fun onStop() {
                    stopSelf()
                }

                override fun onCustomAction(action: String, extras: Bundle?) {
                    if (action == "ACTION_ADD_TIMER") addTimer()
                }

                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    return MediaButtonReceiver.handleIntent(
                        this@BaseReadAloudService, mediaButtonEvent
                    )
                }
            })
        } else {
            mediaSessionCompat.setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    return MediaButtonReceiver.handleIntent(
                        this@BaseReadAloudService, mediaButtonEvent
                    )
                }
            })
        }
        mediaSessionCompat.setMediaButtonReceiver(
            broadcastPendingIntent<MediaButtonReceiver>(Intent.ACTION_MEDIA_BUTTON)
        )
        mediaSessionCompat.isActive = true
    }

    /**
     * 注册多媒体按钮监听
     */
    private fun initBroadcastReceiver() {
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(broadcastReceiver, intentFilter)
    }

    /**
     * 音频焦点变化
     */
    override fun onAudioFocusChange(focusChange: Int) {
        if (AppConfig.ignoreAudioFocus) {
            AppLog.put("忽略音频焦点处理(TTS)")
            return
        }
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (needResumeOnAudioFocusGain) {
                    AppLog.put("音频焦点获得,继续朗读")
                    resumeReadAloud()
                } else {
                    AppLog.put("音频焦点获得")
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                AppLog.put("音频焦点丢失,暂停朗读")
                pauseReadAloud()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                AppLog.put("音频焦点暂时丢失并会很快再次获得,暂停朗读")
                if (!pause) {
                    needResumeOnAudioFocusGain = true
                    pauseReadAloud(false)
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 短暂丢失焦点，这种情况是被其他应用申请了短暂的焦点希望其他声音能压低音量（或者关闭声音）凸显这个声音（比如短信提示音），
                AppLog.put("音频焦点短暂丢失,不做处理")
            }
        }
    }

    private fun upReadAloudNotification() {
        upNotificationJob = execute {
            try {
                val notification = createNotification()
                notificationManager.notify(NotificationId.ReadAloudService, notification.build())
            } catch (e: Exception) {
                AppLog.put("创建朗读通知出错,${e.localizedMessage}", e, true)
            }
        }
    }

    private fun choiceMediaStyle(): androidx.media.app.NotificationCompat.MediaStyle {
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(1, 2, 4)
        if (getPrefBoolean("systemMediaControlCompatibilityChange")) {
            //fix #4090 android 14 can not show play control in lock screen
            mediaStyle.setMediaSession(mediaSessionCompat.sessionToken)
        }
        return mediaStyle
    }

    private fun createNotification(): NotificationCompat.Builder {
        var nTitle: String = when {
            pause -> getString(R.string.read_aloud_pause)
            chapterToStop > 0 -> getString(R.string.read_aloud_timer_chapter, chapterToStop)
            timeMinute > 0 -> getString(
                R.string.read_aloud_timer,
                timeMinute
            )

            else -> getString(R.string.read_aloud_t)
        }
        nTitle += ": ${ReadBook.book?.name}"
        var nSubtitle = if (analyzingRoles) {
            getString(R.string.role_analyzing)
        } else {
            ReadBook.curTextChapter?.title
        }
        if (nSubtitle.isNullOrBlank())
            nSubtitle = getString(R.string.read_aloud_s)
        val builder = NotificationCompat
            .Builder(this, AppConst.channelIdReadAloud)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setSmallIcon(R.drawable.ic_volume_up)
            .setSubText(getString(R.string.read_aloud))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(nTitle)
            .setContentText(nSubtitle)
            .setContentIntent(
                activityPendingIntent<ReadBookActivity>("activity")
            )
            .setVibrate(null)
            .setSound(null)
            .setLights(0, 0, 0)
        builder.setLargeIcon(cover)
        // 按钮定义：上一章、播放、停止、下一章、定时
        builder.addAction(
            R.drawable.ic_skip_previous,
            getString(R.string.previous_chapter),
            aloudServicePendingIntent(IntentAction.prev)
        )
        if (pause) {
            builder.addAction(
                R.drawable.ic_play_24dp,
                getString(R.string.resume),
                aloudServicePendingIntent(IntentAction.resume)
            )
        } else {
            builder.addAction(
                R.drawable.ic_pause_24dp,
                getString(R.string.pause),
                aloudServicePendingIntent(IntentAction.pause)
            )
        }
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.stop),
            aloudServicePendingIntent(IntentAction.stop)
        )
        builder.addAction(
            R.drawable.ic_skip_next,
            getString(R.string.next_chapter),
            aloudServicePendingIntent(IntentAction.next)
        )
        builder.addAction(
            R.drawable.ic_time_add_24dp,
            getString(R.string.set_timer),
            aloudServicePendingIntent(IntentAction.addTimer)
        )
        builder.setStyle(choiceMediaStyle())
        return builder
    }

    /**
     * 更新通知
     */
    override fun startForegroundNotification() {
        execute {
            try {
                val notification = createNotification()
                startForeground(NotificationId.ReadAloudService, notification.build())
            } catch (e: Exception) {
                AppLog.put("创建朗读通知出错,${e.localizedMessage}", e, true)
                //创建通知出错不结束服务就会崩溃,服务必须绑定通知
                stopSelf()
            }
        }
    }

    abstract fun aloudServicePendingIntent(actionStr: String): PendingIntent?

    open fun prevChapter() {
        toLast = false
        resumeReadAloudInternal()
        ReadBook.moveToPrevChapter(true, toLast = false, syncReadAloudFollow = true)
    }

    open fun nextChapter(auto: Boolean = false) {
        ReadBook.upReadTime()
        if (auto && chapterToStop > 0) {
            chapterToStop--
            postEvent(EventBus.READ_ALOUD_CHAPTER, chapterToStop)
            if (chapterToStop == 0) {
                ReadAloud.stop(this)
                return
            }
            upReadAloudNotification()
        }
        AppLog.putDebug("${ReadBook.curTextChapter?.chapter?.title} 朗读结束跳转下一章并朗读")
        resumeReadAloudInternal()
        val hasNextChapter = speechChapterIndex() < ReadBook.simulatedChapterSize - 1
        val visibleSyncMoved = ReadBook.moveToNextChapter(true, syncReadAloudFollow = true)
        when (nextChapterDecision(
            hasNextSpeechChapter = hasNextChapter,
            visibleSyncMoved = visibleSyncMoved
        )) {
            SpeechFollowState.NextChapterDecision.ContinueWithVisibleSync -> Unit
            SpeechFollowState.NextChapterDecision.ContinueSpeechOnly -> loadNextSpeechChapterOnly()
            SpeechFollowState.NextChapterDecision.Stop -> stopSelf()
        }
    }

    private fun speechChapterIndex(): Int {
        return textChapter?.chapter?.index ?: ReadBook.durChapterIndex
    }

    private fun loadNextSpeechChapterOnly() {
        val nextIndex = speechChapterIndex() + 1
        execute(executeContext = IO) {
            val book = ReadBook.book ?: return@execute false
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, nextIndex) ?: return@execute false
            val content = BookHelp.getContent(book, chapter)
                ?: ReadBook.bookSource?.let { source ->
                    CacheBook.getOrCreate(source, book).downloadAwait(chapter)
                }
                ?: "加载正文失败\n${if (book.isLocal) "无内容" else "没有书源"}"
            val contentProcessor = ContentProcessor.get(book)
            val displayTitle = chapter.getDisplayTitle(
                contentProcessor.getTitleReplaceRules(),
                book.getUseReplaceRule()
            )
            val contents = contentProcessor.getContent(book, chapter, content, includeTitle = false)
            val nextTextChapter = ChapterProvider.getTextChapterAsync(
                this,
                book,
                chapter,
                displayTitle,
                contents,
                ReadBook.simulatedChapterSize
            )
            for (page in nextTextChapter.layoutChannel) {
                if (page.index > 0) continue
            }
            textChapter = nextTextChapter
            updateReadAloudChapterIndex(chapter.index)
            pageIndex = 0
            readAloudNumber = 0
            nowSpeak = 0
            nowSegment = 0
            paragraphStartPos = 0
            contentList = nextTextChapter.getNeedReadAloud(0, readAloudByPage, 0)
                .split("\n")
                .filter { it.isNotEmpty() }
            resetSpeechScript()
            contentList.isNotEmpty()
        }.onSuccess(Main) { canContinue ->
            if (canContinue) {
                play()
            } else {
                stopSelf()
            }
        }.onError(Main) {
            AppLog.put("加载朗读下一章出错\n${it.localizedMessage}", it, true)
            stopSelf()
        }
    }

    private fun initPhoneStateListener() {
        val needRegister = AppConfig.ignoreAudioFocus && AppConfig.pauseReadAloudWhilePhoneCalls
        if (needRegister && registeredPhoneStateListener) {
            return
        }
        if (needRegister) {
            registerPhoneStateListener(phoneStateListener)
        } else {
            unregisterPhoneStateListener(phoneStateListener)
        }
    }

    private fun unregisterPhoneStateListener(l: PhoneStateListener) {
        if (registeredPhoneStateListener) {
            withReadPhoneStatePermission {
                telephonyManager.listen(l, PhoneStateListener.LISTEN_NONE)
                registeredPhoneStateListener = false
            }
        }
    }

    private fun registerPhoneStateListener(l: PhoneStateListener) {
        withReadPhoneStatePermission {
            telephonyManager.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            registeredPhoneStateListener = true
        }
    }

    private fun withReadPhoneStatePermission(block: () -> Unit) {
        try {
            block.invoke()
        } catch (_: SecurityException) {
            PermissionsCompat.Builder()
                .addPermissions(Permissions.READ_PHONE_STATE)
                .rationale(R.string.read_aloud_read_phone_state_permission_rationale)
                .onGranted {
                    try {
                        block.invoke()
                    } catch (_: SecurityException) {
                        LogUtils.d(TAG, "Grant read phone state permission fail.")
                    }
                }
                .request()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    inner class ReadAloudPhoneStateListener : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            super.onCallStateChanged(state, phoneNumber)
            when (state) {
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (needResumeOnCallStateIdle) {
                        AppLog.put("来电结束,继续朗读")
                        resumeReadAloud()
                    } else {
                        AppLog.put("来电结束")
                    }
                }

                TelephonyManager.CALL_STATE_RINGING -> {
                    if (!pause) {
                        AppLog.put("来电响铃,暂停朗读")
                        needResumeOnCallStateIdle = true
                        pauseReadAloud()
                    } else {
                        AppLog.put("来电响铃")
                    }
                }

                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    AppLog.put("来电接听,不做处理")
                }
            }
        }
    }

}
