package io.legado.app.service

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.constant.AppConst
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.constant.NotificationId
import io.legado.app.constant.PreferKey
import io.legado.app.receiver.NetworkChangedListener
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.startService
import io.legado.app.utils.stopService
import io.legado.app.utils.toastOnUi
import io.legado.app.web.mcp.McpToolServer
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import splitties.init.appCtx

/**
 * App 内直出的 MCP server(Streamable HTTP,端点 /mcp)。
 * 与 WebService 相互独立:工具直调内部,不依赖 Web 服务。
 */
class McpService : BaseService() {

    companion object {
        var isRun = false
        var hostAddress = ""

        fun start(context: Context) {
            context.startService<McpService>()
        }

        fun stop(context: Context) {
            context.stopService<McpService>()
        }
    }

    private var engine: EmbeddedServer<*, *>? = null
    private var notificationList = mutableListOf(appCtx.getString(R.string.service_starting))
    private val networkChangedListener by lazy {
        NetworkChangedListener(this)
    }

    override fun onCreate() {
        super.onCreate()
        isRun = true
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            val addressList = NetworkUtils.getLocalIPAddress()
            notificationList.clear()
            if (addressList.any()) {
                notificationList.addAll(addressList.map { address ->
                    "http://${address.hostAddress}:${getPort()}/mcp"
                })
                hostAddress = notificationList.first()
            } else {
                hostAddress = getString(R.string.network_connection_unavailable)
                notificationList.add(hostAddress)
            }
            startForegroundNotification()
            postEvent(EventBus.MCP_SERVICE, hostAddress)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IntentAction.stop -> stopSelf()
            "copyHostAddress" -> sendToClip(hostAddress)
            else -> upMcpServer()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        networkChangedListener.unRegister()
        isRun = false
        engine?.stop(500, 1000)
        engine = null
        postEvent(EventBus.MCP_SERVICE, "")
    }

    private fun upMcpServer() {
        engine?.stop(500, 1000)
        engine = null
        val addressList = NetworkUtils.getLocalIPAddress()
        if (addressList.any()) {
            val port = getPort()
            try {
                engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                    // SDK 默认的 DNS-rebinding 防护只放行 localhost,拦掉局域网直连;
                    // 信任模型与相邻端口的 WebService(无 Host 校验)一致,关闭之
                    mcpStreamableHttp(enableDnsRebindingProtection = false) {
                        McpToolServer.create()
                    }
                }.also { it.start(wait = false) }
                notificationList.clear()
                notificationList.addAll(addressList.map { address ->
                    "http://${address.hostAddress}:$port/mcp"
                })
                hostAddress = notificationList.first()
                isRun = true
                postEvent(EventBus.MCP_SERVICE, hostAddress)
                startForegroundNotification()
            } catch (e: Exception) {
                toastOnUi(e.localizedMessage ?: "MCP 服务启动失败")
                e.printOnDebug()
                stopSelf()
            }
        } else {
            toastOnUi("mcp service cant start, no ip address")
            stopSelf()
        }
    }

    private fun getPort(): Int {
        var port = getPrefInt(PreferKey.mcpPort, 1236)
        if (port > 65530 || port < 1024) {
            port = 1236
        }
        return port
    }

    override fun startForegroundNotification() {
        val builder = NotificationCompat.Builder(this, AppConst.channelIdWeb)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_web_service_noti)
            .setOngoing(true)
            .setContentTitle(getString(R.string.mcp_service))
            .setContentText(notificationList.joinToString("\n"))
            .setContentIntent(
                servicePendingIntent<McpService>("copyHostAddress")
            )
        builder.addAction(
            R.drawable.ic_stop_black_24dp,
            getString(R.string.cancel),
            servicePendingIntent<McpService>(IntentAction.stop)
        )
        val notification = builder.build()
        startForeground(NotificationId.McpService, notification)
    }
}
