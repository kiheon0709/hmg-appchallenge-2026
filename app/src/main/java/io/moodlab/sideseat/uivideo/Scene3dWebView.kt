package io.moodlab.sideseat.uivideo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import io.moodlab.sideseat.ui.SideSeatUiDebug
import io.moodlab.sideseat.ui.scene.Scene3dHost
import io.moodlab.sideseat.ui.scene.SceneRender
import java.io.ByteArrayInputStream

/**
 * 3D 장면(안드로이드). assets/scene3d/index.html을 WebView에 띄우고 `sim.set(...)`으로 값을 넘긴다.
 * 장면이 보내는 이벤트는 `SideSeatBridge.onSimEvent`로 받는다. 화면이 사라지면 WebView도 바로 없앤다(정차 중 화면에서만 쓴다).
 * 앱 안의 파일만 읽고 그 밖의 주소는 전부 막는다.
 */
class WebViewScene3d : Scene3dHost {
    @Composable
    override fun Layer(command: String, modifier: Modifier) {
        // WebView를 Compose 안(AndroidView)에 넣으면 Pleos 에뮬레이터에서 아무것도 그려지지 않는다(실측 11-14).
        // 그래서 액티비티 창의 Compose 화면 아래에 직접 붙이고, 이 자리의 Compose는 바탕을 칠하지 않아 비쳐 보이게 한다.
        val composeView = LocalView.current
        val holder = remember { Scene3dHolder() }
        DisposableEffect(composeView) {
            holder.attach(composeView)
            onDispose { holder.destroy() }
        }
        Box(modifier.onGloballyPositioned { c -> c.boundsInWindow().let { holder.place(it.left, it.top, it.width, it.height) } })
        SideEffect { holder.send(command) }
    }
}

/** 디버그 명령(`scene_js`)이 지금 떠 있는 장면에 JS를 넣어 보는 통로. 결과는 로그로 남는다. */
object Scene3dDebug {
    var eval: ((String) -> Unit)? = null
}

private class Scene3dHolder {
    private var web: WebView? = null
    private var ready = false
    private var wanted: String? = null
    private var sent: String? = null
    private var perfCount = 0
    private val main = Handler(Looper.getMainLooper())
    private val frameTick = Runnable { SceneRender.onSceneFrame() }

    private var root: ViewGroup? = null

    /** Compose 화면이 들어 있는 창의 내용 영역 맨 아래에 WebView를 넣는다. */
    fun attach(composeView: View) {
        if (web != null) return
        val content = composeView.rootView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val w = create(composeView.context)
        content.addView(w, 0, FrameLayout.LayoutParams(1, 1))
        root = content
    }

    /** Compose가 잡아 준 자리(창 좌표, px)에 맞춘다. */
    fun place(left: Float, top: Float, width: Float, height: Float) {
        val w = web ?: return
        val origin = IntArray(2).also { root?.getLocationInWindow(it) }
        val lp = w.layoutParams
        val wi = width.toInt().coerceAtLeast(1)
        val hi = height.toInt().coerceAtLeast(1)
        if (lp.width != wi || lp.height != hi) { lp.width = wi; lp.height = hi; w.layoutParams = lp }
        w.translationX = left - origin[0]
        w.translationY = top - origin[1]
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(context: Context): WebView {
        val assets = context.applicationContext.assets
        val w = WebView(context)
        w.setBackgroundColor(Color.BLACK)
        w.overScrollMode = View.OVER_SCROLL_NEVER
        w.isVerticalScrollBarEnabled = false
        w.isHorizontalScrollBarEnabled = false
        w.isFocusable = false
        w.settings.javaScriptEnabled = true
        w.settings.allowFileAccess = false
        w.settings.allowContentAccess = false
        w.settings.setSupportZoom(false)
        w.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val url = request.url
                val path = url.path.orEmpty()
                if (url.host == HOST && path.startsWith("/assets/scene3d/") && !path.contains("..")) {
                    val mime = when {
                        path.endsWith(".html") -> "text/html"
                        path.endsWith(".js") -> "text/javascript"
                        else -> "application/octet-stream"
                    }
                    runCatching { return WebResourceResponse(mime, "utf-8", assets.open(path.removePrefix("/assets/"))) }
                }
                return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.w(TAG, "scene3d render process gone crashed=${detail.didCrash()}")
                SceneRender.failed = true
                return true
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                Log.i(TAG, "scene3d console ${m.messageLevel()} ${m.message()} (${m.lineNumber()})")
                return true
            }
        }
        w.addJavascriptInterface(Bridge(), "SideSeatBridge")
        w.loadUrl("https://$HOST/assets/scene3d/index.html?fps_cap=${SceneRender.fpsCap}")
        web = w
        Scene3dDebug.eval = { js -> w.evaluateJavascript(js) { Log.i(TAG, "scene3d js -> $it") } }
        return w
    }

    fun send(command: String) {
        wanted = command
        val w = web ?: return
        if (!ready || command == sent) return
        sent = command
        w.evaluateJavascript("sim.set($command)", null)
    }

    fun destroy() {
        web?.let {
            root?.removeView(it)
            it.stopLoading()
            it.removeJavascriptInterface("SideSeatBridge")
            it.destroy()
        }
        web = null
        root = null
        ready = false
        SceneRender.ready = false
        Scene3dDebug.eval = null
    }

    private fun onEvent(name: String, json: String) {
        when (name) {
            "ready" -> {
                ready = true
                SceneRender.ready = true
                Log.i(TAG, "scene3d ready")
                wanted?.let(::send)
            }
            // WebGL2가 없거나 그리기 문맥을 잃었다 → 영상 방식으로 바꾼다
            "unsupported" -> {
                Log.w(TAG, "scene3d unsupported $json")
                SceneRender.failed = true
            }
            // 2초마다 온다. 프레임 로그를 켜면 모두, 아니면 10초에 한 번만 남긴다
            "perf" -> if (SideSeatUiDebug.logFrames || perfCount++ % 5 == 0) Log.i(TAG, "scene3d perf $json")
            else -> Log.i(TAG, "scene3d event $name $json")
        }
    }

    private inner class Bridge {
        /** 장면(JS) 스레드에서 불린다. 메인 스레드로 넘겨 처리한다. */
        /** 장면이 한 장 그릴 때마다 불린다. 장면 위 Compose 층이 이때 같이 다시 그린다. */
        @JavascriptInterface
        fun onFrame() {
            main.post(frameTick)
        }

        @JavascriptInterface
        fun onSimEvent(name: String, json: String) {
            main.post { if (web != null) onEvent(name, json) }
        }
    }

    private companion object {
        const val TAG = "SideSeatMeasure"
        const val HOST = "appassets.androidplatform.net"
    }
}
