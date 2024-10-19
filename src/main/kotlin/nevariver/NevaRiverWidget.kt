package nevariver

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.Alarm
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import nevariver.NevaRiverWidgetModel.scaleLevel
import nevariver.NevaRiverWidgetModel.scaleTime
import java.awt.BasicStroke
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.RenderingHints.*
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import javax.swing.*

private val DATEFORMAT = SimpleDateFormat("dd MMM HH:mm")
private fun Long.formatDate() = DATEFORMAT.format(this)
private fun Int.formatLevel(): String = String.format("%+d", this)

class NevaRiverWidget(private val myProject: Project) : JLabel(),
    CustomStatusBarWidget, Runnable {

    private val myAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    override fun install(statusBar: StatusBar) {
        updateView()
        myAlarm.addRequest(this, 5000L)
    }

    override fun getComponent(): JComponent {
        return this
    }

    override fun ID(): String = ID

    override fun dispose() {
    }

    override fun run() {
        updateView()
        myAlarm.addRequest(this, MINUTE)
    }

    private fun updateView() {
        SwingUtilities.invokeLater {
            val levelMap = NevaRiverWidgetModel.levelMap
            if (levelMap.isEmpty()) {
                text = ""
                toolTipText = "Water level of Neva river near estuary"
                icon = EmptyIcon.ICON_16
            } else {
                val iconHeight = (WindowManager.getInstance().getStatusBar(myProject)?.component?.height ?: 20) - 2
                val iconWidth = iconHeight * 6
                val image = ImageUtil.createImage(
                    graphicsConfiguration
                        ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration,
                    iconWidth, iconHeight, BufferedImage.TRANSLUCENT
                )
                var rangeInMillis = levelMap.lastKey().time - levelMap.firstKey().time
                val hoursInRange = rangeInMillis / HOUR

                with(image.createGraphics()) {
                    setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON)
                    setRenderingHint(KEY_COLOR_RENDERING, VALUE_COLOR_RENDER_QUALITY)
                    setRenderingHint(KEY_STROKE_CONTROL, VALUE_STROKE_PURE)
                    setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR)
                    setRenderingHint(KEY_ALPHA_INTERPOLATION, VALUE_ALPHA_INTERPOLATION_QUALITY)


                    stroke =
                        BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(1f, 3f), 0f)
                    color = JBColor.GRAY
                    var x = scaleTime(NevaRiverWidgetModel.minLevelTime, iconWidth)
                    var y: Double
                    draw(Line2D.Double(x, 0.0, x, iconHeight.toDouble()))
                    x = scaleTime(NevaRiverWidgetModel.maxLevelTime, iconWidth)
                    draw(Line2D.Double(x, 0.0, x, iconHeight.toDouble()))

                    var lastX = -1.0
                    var lastY = -1.0
                    for ((time, level) in levelMap.entries) {
                        color = when {
                            level > 0 -> ColorUtil.darker(JBColor.GREEN, 3)
                            level < 0 -> ColorUtil.darker(JBColor.RED, 3)
                            else -> JBColor.GRAY
                        }
                        x = scaleTime(time.time, iconWidth)
                        y = scaleLevel(level, iconHeight)
                        if (x >= lastX + .5) {
                            draw(Line2D.Double(lastX, if (lastY < 0) y else lastY, x, y))
                            lastX = x
                            lastY = y
                        }
                    }

                    for (h in hoursInRange + 1 downTo 1) {
                        color = when {
                            h % 2 == 1L -> ColorUtil.withAlpha(JBColor.foreground(), .1)
                            else -> ColorUtil.withAlpha(JBColor.foreground(), .2)
                        }
                        fill(
                            Rectangle2D.Double(
                                scaleTime(System.currentTimeMillis() - h * HOUR, iconWidth),
                                0.0,
                                scaleTime(System.currentTimeMillis() - (h - 1) * HOUR, iconWidth)
                                        - scaleTime(System.currentTimeMillis() - h * HOUR, iconWidth),
                                iconHeight.toDouble()
                            )
                        )
                    }
                }
                icon = IconUtil.createImageIcon(image as Image)
                text = NevaRiverWidgetModel.lastLevel.formatLevel()
                val timeRange = buildString {
                    if (rangeInMillis > HOUR) {
                        append(hoursInRange)
                        append(if (hoursInRange > 1) " hours " else " hour ")
                        rangeInMillis -= hoursInRange * HOUR
                    }
                    if (rangeInMillis > MINUTE) {
                        val minutesInRange = rangeInMillis / MINUTE
                        append(minutesInRange)
                        append(if (minutesInRange > 1) " minutes" else " minute")
                    }
                }
                toolTipText =
                    """<html><body>
                          Water level of Neva river near estuary
                          <table cellspacing='5' cellpadding='0' border='0'>
                          <tr>
                            <td>Low: </td><td>${NevaRiverWidgetModel.minLevel.formatLevel()}</td><td> at ${NevaRiverWidgetModel.minLevelTime.formatDate()}</td>
                          </tr>
                          <tr>
                            <td>High: </td><td>${NevaRiverWidgetModel.maxLevel.formatLevel()}</td><td> at ${NevaRiverWidgetModel.maxLevelTime.formatDate()}</td>
                          </tr>
                          <tr>
                            <td>Last: </td><td>${NevaRiverWidgetModel.lastLevel.formatLevel()}</td><td> at ${NevaRiverWidgetModel.lastLevelTime.formatDate()}</td>
                          </tr>
                          </table>
                          Time range: $timeRange"""

            }
            font = if (SystemInfo.isMac) JBUI.Fonts.smallFont() else JBUI.Fonts.miniFont()
            iconTextGap = 1
            revalidate()
            repaint()
        }
    }

    override fun updateUI() {
        super.updateUI()
        updateView()
    }
}


