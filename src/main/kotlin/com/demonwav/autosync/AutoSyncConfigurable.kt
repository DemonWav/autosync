package com.demonwav.autosync

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.TableUtil
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ArrayUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel

class AutoSyncConfigurable(private val project: Project) : Configurable {

    private lateinit var panel: JPanel
    private lateinit var enableCheckBox: JCheckBox
    private lateinit var timeSpinner: JSpinner
    private lateinit var excludeDirsPanel: JPanel

    private var table: JTable? = null

    @Nls
    override fun getDisplayName() = "Auto Sync Settings"
    override fun getHelpTopic() = null

    override fun createComponent(): JComponent? {
        timeSpinner.model = model
        createExcludePanel(excludeDirsPanel)
        return panel
    }

    override fun isModified(): Boolean {
        val settings = AutoSyncSettings.getInstance(project)
        return settings.isEnabled != enableCheckBox.isSelected || settings.timeBetweenSyncs != (timeSpinner.value as Number).toLong() ||
            includedDirsIsModified()
    }

    private fun includedDirsIsModified(): Boolean {
        val settings = AutoSyncSettings.getInstance(project)
        val tableModel = table!!.model as TableModel

        if (settings.excludedUrls.size != tableModel.rowCount) {
            return true
        }

        val count = tableModel.rowCount
        return (0 until count).any { settings.excludedUrls[it] != tableModel.getValueAt(it).url }
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val settings = AutoSyncSettings.getInstance(project)
        settings.modified = true

        settings.isEnabled = enableCheckBox.isSelected
        settings.timeBetweenSyncs = (timeSpinner.value as Number).toLong()

        WindowManager.getInstance().getFrame(project)?.let { frame ->
            frame.removeWindowFocusListener(AutoSyncFocusListener) // for good measure

            if (settings.isEnabled) {
                frame.addWindowFocusListener(AutoSyncFocusListener)
            }
        }

        saveData()
    }

    override fun reset() {
        val settings = AutoSyncSettings.getInstance(project)
        enableCheckBox.isSelected = settings.isEnabled
        timeSpinner.value = settings.timeBetweenSyncs

        val array = ArrayUtil.newIntArray(table!!.rowCount)
        for (i in 0 until table!!.rowCount) {
            array[i] = i
        }
        TableUtil.selectRows(table!!, array)
        TableUtil.removeSelectedItems(table!!)

        val tableModel = table!!.model as TableModel
        val excludedUrls = AutoSyncSettings.getInstance(project).excludedUrls
        for (url in excludedUrls) {
            tableModel.addTableItem(TableItem(url))
        }
    }

    private fun createExcludePanel(panel: JPanel) {
        val model = createModel()
        table = JBTable(model)
        table?.apply {
            intercellSpacing = Dimension(0, 0)
            setDefaultRenderer(TableItem::class.java, Renderer())
            setShowGrid(false)
            dragEnabled = false
            showHorizontalLines = false
            showVerticalLines = false
            selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        }

        val tablePanel = ToolbarDecorator.createDecorator(table!!).setAddAction {
            val descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
            descriptor.title = "Choose directories and files to include"

            val files = FileChooser.chooseFiles(descriptor, table, project, null)
            val tableModel = table!!.model as TableModel
            var changes = false
            for (file in files) {
                if (file != null) {
                    tableModel.addTableItem(TableItem(file))
                    changes = true
                }
            }

            if (changes) {
                TableUtil.selectRows(table!!, intArrayOf(model.rowCount - 1))
            }
        }.setRemoveAction {
            TableUtil.removeSelectedItems(table!!)
        }.createPanel()

        val mainPanel = JPanel(BorderLayout())

        mainPanel.add(tablePanel, BorderLayout.CENTER)
        mainPanel.add(JBLabel("Manage directories and files to include in the sync process.", UIUtil.ComponentStyle.SMALL,
                              UIUtil.FontColor.BRIGHTER), BorderLayout.NORTH)

        panel.add(mainPanel, BorderLayout.CENTER)
    }

    private fun createModel() : TableModel {
        val tableModel = TableModel()
        val excludedUrls = AutoSyncSettings.getInstance(project).excludedUrls
        for (url in excludedUrls) {
            tableModel.addTableItem(TableItem(url))
        }
        return tableModel
    }

    private fun saveData() {
        TableUtil.stopEditing(table!!)
        val count = table!!.rowCount
        val urls = ArrayUtil.newStringArray(count)
        for (row in 0 until count) {
            val item = (table!!.model as TableModel).getValueAt(row)
            urls[row] = item.url
        }
        AutoSyncSettings.getInstance(project).excludedUrls = mutableListOf(*urls)
    }

    companion object {
        private val model = SpinnerNumberModel(15, 0, 60, 1)
    }
}
