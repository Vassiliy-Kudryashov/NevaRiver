package nevariver

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

const val ID = "Neva River Widget"

class NevaRiverWidgetFactory: StatusBarWidgetFactory{
    override fun getId(): String = ID

    override fun getDisplayName(): String {
        return ID
    }

    override fun isAvailable(project: Project): Boolean {
        return !project.isDefault
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return NevaRiverWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar) = true
}