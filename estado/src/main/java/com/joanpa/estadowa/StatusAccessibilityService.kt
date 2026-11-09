package com.joanpa.estadowa

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Solo actúa mientras hay una publicación "armada" (unos 2 minutos tras la hora programada).
 * En la pantalla "Enviar a…" de WhatsApp pulsa "Mi estado" y después el botón de enviar
 * (una o dos veces: la lista de destinatarios y la vista previa del video).
 * El resto del tiempo ignora todo.
 */
class StatusAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            if (armedJob < 0) return
            try { tick() } catch (_: Exception) { }
            if (armedJob >= 0) handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        instance = this
        if (armedJob >= 0) startTicking()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // El trabajo lo hace el ticker; los eventos solo sirven para reaccionar antes.
        if (armedJob >= 0 && !ticking) startTicking()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ticking = false
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun startTicking() {
        ticking = true
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        if (now > deadline) {
            if (sendClicks > 0) finishJob(true, "Publicado (verifica en WhatsApp)")
            else finishJob(false, "No encontré el botón \"Mi estado\" en WhatsApp a tiempo.")
            return
        }
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        if (root == null || pkg != armedPkg) {
            // WhatsApp se cerró / volvió atrás después de enviar → hecho.
            if (sendClicks > 0 && now - lastAction > 2500) finishJob(true, "Publicado")
            return
        }
        if (now - lastAction < ACTION_GAP_MS) return

        if (!statusClicked) {
            val status = findStatusNode(root)
            if (status != null) {
                click(status)
                statusClicked = true
                lastAction = now
            }
            return
        }

        val send = findSendNode(root)
        if (send != null && sendClicks < MAX_SEND_CLICKS) {
            click(send)
            sendClicks++
            lastAction = now
            return
        }
        // Sin botón de enviar a la vista tras haber enviado: WhatsApp ya está subiendo el estado.
        if (sendClicks > 0 && now - lastAction > 3000) finishJob(true, "Publicado")
    }

    private fun findStatusNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (label in STATUS_LABELS) {
            val hit = root.findAccessibilityNodeInfosByText(label).firstOrNull { n ->
                n.isVisibleToUser && (matches(n.text, label) || matches(n.contentDescription, label))
            }
            if (hit != null) return hit
        }
        return null
    }

    private fun findSendNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in SEND_IDS) {
            val hit = root.findAccessibilityNodeInfosByViewId("$armedPkg:id/$id")
                .firstOrNull { it.isVisibleToUser && it.isEnabled }
            if (hit != null) return hit
        }
        return findByDescription(root, 0)
    }

    private fun findByDescription(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > 40) return null
        val desc = node.contentDescription?.toString()?.trim()
        if (desc != null && SEND_DESCS.any { it.equals(desc, ignoreCase = true) } && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            findByDescription(node.getChild(i), depth + 1)?.let { return it }
        }
        return null
    }

    private fun matches(cs: CharSequence?, label: String) =
        cs?.toString()?.trim()?.equals(label, ignoreCase = true) == true

    /** Pulsa el nodo (o el primer padre pulsable). Si nada es pulsable, toca su centro en pantalla. */
    private fun click(node: AccessibilityNodeInfo) {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            n = n.parent
        }
        val r = Rect().also { node.getBoundsInScreen(it) }
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 80)).build(),
            null, null
        )
    }

    private fun finishJob(ok: Boolean, detail: String) {
        val id = armedJob
        disarm()
        AppPrefs.unlock(this)
        handler.removeCallbacks(ticker)
        ticking = false
        if (id < 0) return
        val job = JobStore.get(this, id)
        JobStore.update(this, id, if (ok) StatusJob.DONE else StatusJob.FAILED, detail)
        App.notify(
            this, id.toInt(),
            if (ok) "Estado publicado ✅" else "No se publicó el estado",
            "${job?.fileName ?: ""}\n$detail"
        )
        if (ok) {
            handler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_HOME)
                if (AppPrefs.lockAfter(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }, 4000)
                }
            }, 2000)
        }
    }

    companion object {
        private const val TICK_MS = 500L
        private const val ACTION_GAP_MS = 1500L
        private const val ARM_WINDOW_MS = 2 * 60_000L
        private const val MAX_SEND_CLICKS = 3

        /** Texto de la fila "Mi estado" en la pantalla "Enviar a…" (varios idiomas). */
        private val STATUS_LABELS = listOf("Mi estado", "My status", "Meu status", "Mon statut", "Il mio stato")

        /** IDs conocidos del botón verde de enviar en distintas versiones de WhatsApp. */
        private val SEND_IDS = listOf("send", "send_media_btn", "send_btn", "media_send_button", "send_button")
        private val SEND_DESCS = listOf("Enviar", "Send", "Enviar ahora", "Publicar")

        @Volatile private var instance: StatusAccessibilityService? = null
        @Volatile private var armedJob = -1L
        @Volatile private var armedPkg = "com.whatsapp"
        @Volatile private var deadline = 0L
        @Volatile private var statusClicked = false
        @Volatile private var sendClicks = 0
        @Volatile private var lastAction = 0L
        @Volatile private var ticking = false

        fun arm(jobId: Long, pkg: String) {
            armedPkg = pkg
            statusClicked = false
            sendClicks = 0
            lastAction = System.currentTimeMillis()
            deadline = lastAction + ARM_WINDOW_MS
            armedJob = jobId
            instance?.let { svc -> svc.handler.post { svc.startTicking() } }
        }

        fun disarm() {
            armedJob = -1L
        }

        fun isEnabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, StatusAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
