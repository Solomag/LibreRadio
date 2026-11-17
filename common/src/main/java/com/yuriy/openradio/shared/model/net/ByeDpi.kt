package com.yuriy.openradio.shared.model.net

import android.os.Build
import android.os.SystemClock
import com.yuriy.openradio.shared.utils.AppLogger
import java.net.InetSocketAddress
import java.net.Socket

/**
 * ByeDPI (https://github.com/hufrea/byedpi, MIT) built into the app: a SOCKS5 proxy on the phone itself
 * that splits and disguises the first packets of each connection, so a network that blocks or cuts
 * connections by what it reads in them (the server name) lets them through. No server, no VPN.
 *
 * [NetProxy] sends the app's connections to it when the proxy type is [NetProxy.Type.BYEDPI].
 * Native part: src/main/cpp. Needs Android 5 (the NDK builds for API 21 and up).
 */
object ByeDpi {

    private const val TAG = "BDPI"
    private const val HOST = "127.0.0.1"

    /**
     * Ports tried in turn, in case another app holds one.
     */
    private val PORTS = intArrayOf(31080, 31081, 31082, 31083)
    private const val START_WAIT_MS = 3_000L

    /**
     * Ways to get past blocking, simplest first: networks differ, so [NetProxy.pickDpi] tries them in turn.
     */
    val PRESETS = listOf(
        "-d1",
        "-s1 -d3+s",
        "-d1 -r1+s",
        "-o1 -d1+s",
        "-f-1 -t5 -r1+s",
        "-q1 -r1+s -s5+s",
        // A fake first packet with a white name (see WhiteSni): the network sees the name it lets through.
        "-f-1 -n vk.com -t5 -r1+s",
        "-d1 -f-1 -n vk.com -t5",
        "-d1 -s1+s -r1+s -At,r,s -f-1 -t5 -r1+s -As -d1"
    )

    val isAvailable: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && try {
            System.loadLibrary("byedpi")
            true
        } catch (e: Throwable) {
            AppLogger.e("$TAG no native library", e)
            false
        }
    }

    @JvmStatic
    private external fun nativeRun(args: Array<String>): Int

    @JvmStatic
    private external fun nativeStop()

    private var mArgs: String? = null
    private var mThread: Thread? = null

    /**
     * Port the running proxy listens on.
     */
    @Volatile
    var port = PORTS[0]
        private set

    /**
     * Starts the proxy with [args] (byedpi's options), or restarts it when it runs with other ones.
     * Blocks until it listens.
     *
     * @return True when it runs.
     */
    @Synchronized
    fun start(args: String): Boolean {
        if (!isAvailable) {
            return false
        }
        if (mArgs == args && mThread?.isAlive == true) {
            return true
        }
        stopLocked()
        for (candidate in PORTS) {
            if (isListening(candidate)) {
                continue
            }
            val argv = arrayOf("-i", HOST, "-p", candidate.toString()) + split(args)
            val thread = Thread({
                val result = nativeRun(argv)
                AppLogger.i("$TAG stopped: $result")
            }, "ByeDPI")
            thread.isDaemon = true
            thread.start()
            val until = SystemClock.elapsedRealtime() + START_WAIT_MS
            while (SystemClock.elapsedRealtime() < until) {
                if (!thread.isAlive) {
                    // Wrong options or the port is taken.
                    break
                }
                if (isListening(candidate)) {
                    mArgs = args
                    mThread = thread
                    port = candidate
                    AppLogger.i("$TAG runs on $candidate: $args")
                    return true
                }
                SystemClock.sleep(30)
            }
            if (thread.isAlive) {
                nativeStop()
                thread.join(1000)
            }
        }
        AppLogger.w("$TAG did not start: $args")
        return false
    }

    @Synchronized
    fun stop() {
        stopLocked()
    }

    private fun stopLocked() {
        val thread = mThread ?: return
        nativeStop()
        thread.join(3000)
        mThread = null
        mArgs = null
    }

    private fun split(args: String): List<String> = args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun isListening(port: Int): Boolean {
        return try {
            Socket().use { it.connect(InetSocketAddress(HOST, port), 200) }
            true
        } catch (e: Exception) {
            false
        }
    }
}
