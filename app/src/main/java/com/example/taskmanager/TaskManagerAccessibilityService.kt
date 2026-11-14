package com.example.taskmanager

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AccessibilityCommandHolder {
    var targetPackageToForceStop: String? = null
}

class TaskManagerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            val target = AccessibilityCommandHolder.targetPackageToForceStop

            if (target != null && (pkg.contains("settings") || pkg.contains("packageinstaller"))) {
                serviceScope.launch {
                    delay(200) // Wait for UI to render
                    val root = rootInActiveWindow ?: return@launch
                    if (findAndClickForceStop(root)) {
                        AccessibilityCommandHolder.targetPackageToForceStop = null
                        delay(300)
                        // Click confirmation OK dialog if present
                        val rootAfter = rootInActiveWindow
                        if (rootAfter != null) {
                            clickConfirmationOk(rootAfter)
                        }
                        delay(200)
                        try {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun findAndClickForceStop(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        if ((text.contains("force stop") || desc.contains("force stop") || viewId.contains("force_stop") || text.contains("forçar") || text.contains("forzar") || text.contains("arrêt")) && node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickForceStop(child)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    private fun clickConfirmationOk(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if ((text == "ok" || text == "yes" || text == "force stop" || text.contains("ok") || text.contains("force")) && node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickConfirmationOk(child)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    override fun onInterrupt() {}
}
