package com.demonwav.autosync

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.wm.WindowManager
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object AutoSyncFocusListener : WindowFocusListener {

    val pastSyncs = ConcurrentHashMap<Project, Instant>()
    val runningSyncs = HashSet<Project>()

    override fun windowGainedFocus(e: WindowEvent) {
        val project = WindowManager.getInstance().allProjectFrames.firstOrNull { it === e.component }?.project ?: return
        if (AutoSyncSettings.getInstance(project).isEnabled) {
            performSync(project)
        }
    }

    override fun windowLostFocus(e: WindowEvent) {}

    private fun performSync(project: Project) {
        if (project.isDisposed) {
            return
        }

        val time = pastSyncs[project]
        pastSyncs[project] = Instant.now()

        if (time != null && time.plus(Duration.ofMinutes(AutoSyncSettings.getInstance(project).timeBetweenSyncs)).isAfter(Instant.now())) {
            pastSyncs[project] = Instant.now()
            return
        }

        runningSyncs.add(project)
        runWriteAction {
            (project.baseDir as? NewVirtualFile)?.markDirtyRecursively()
        }

        RefreshQueue.getInstance().refresh(true, true, Runnable {
            postRefresh(project)
        }, project.baseDir)
    }

    private fun postRefresh(project: Project) {
        val dirtyScopeManager = VcsDirtyScopeManager.getInstance(project)
        dirtyScopeManager.dirDirtyRecursively(project.baseDir)

        WindowManager.getInstance().getStatusBar(project)?.info = IdeBundle.message(
            "action.sync.completed.successfully",
            IdeBundle.message(
                "action.synchronize.file",
                StringUtil.escapeMnemonics(StringUtil.firstLast(project.baseDir.name, 20))
            )
        )
        runningSyncs.remove(project)
    }
}
