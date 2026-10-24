@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.piko.ui.ios

import dev.piko.shared.media.PreparedPlayback
import dev.piko.ui.PikoServices
import dev.piko.ui.VideoPlayerHost
import dev.piko.ui.VideoPlayerRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS 的播放器呈现：全屏弹一个 `AVPlayerViewController`。iOS 没有「一边播一边浏览网盘」的独立窗口，
 * 所以用 [VideoPlayerHost.Detached] 的形态：界面层把播放交给平台，播放页用系统播放器自带的进度条。
 *
 * 取流次序与 Android/桌面一致：本机已下完的文件直接读文件；否则经媒体仓库开本机回环代理，
 * 把 `proxyUrl` 交给 AVPlayer。代理会话在下次起播或播放器关掉时释放，见 [PlayerSession]。
 *
 * [VideoPlayerHost.Detached] 是 final 类，这里用工厂函数返回实例，不继承。
 */
fun iosVideoPlayerHost(services: PikoServices, scope: CoroutineScope): VideoPlayerHost.Detached {
    val session = PlayerSession()
    return VideoPlayerHost.Detached(
        open = { request -> scope.launch { session.present(services, request) } },
        // 信息流弹出窗口是桌面专有能力：iOS 的信息流就在网盘页里
        openClipFeed = { },
        isClipFeedOpen = { false },
        closeClipFeed = { },
    )
}

/** 播放期间挂着的代理会话，播放器关掉后释放，回环流的连接随之断开。 */
private class PlayerSession {
    private var playback: PreparedPlayback? = null

    suspend fun present(services: PikoServices, request: VideoPlayerRequest) {
        val url = when {
            !request.localPath.isNullOrBlank() -> NSURL.fileURLWithPath(request.localPath)
            else -> {
                val prepared = services.mediaRepository.preparePlayback(request.fileId).getOrNull()
                // 取流失败就不弹空白播放器，界面上仍停在原页
                val address = prepared?.proxyUrl ?: return
                playback?.close()
                playback = prepared
                NSURL.URLWithString(address)
            }
        } ?: return
        dispatch_async(dispatch_get_main_queue()) {
            val controller = AVPlayerViewController().apply {
                player = AVPlayer.playerWithURL(url)
                modalPresentationStyle = UIModalPresentationFullScreen
            }
            topViewController()?.presentViewController(controller, animated = true, completion = null)
            controller.player?.play()
        }
    }
}

/** 从最上层的 `presentedViewController` 往上找，避免把播放器塞进已推开的导航栈里。 */
private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
    while (true) {
        val presented = controller.presentedViewController ?: return controller
        controller = presented
    }
}
