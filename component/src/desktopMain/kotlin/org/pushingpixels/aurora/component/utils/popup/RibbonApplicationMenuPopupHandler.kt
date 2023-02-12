/*
 * Copyright 2020-2023 Aurora, Kirill Grouchnikov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pushingpixels.aurora.component.utils.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import org.pushingpixels.aurora.common.AuroraInternalApi
import org.pushingpixels.aurora.common.AuroraPopupManager
import org.pushingpixels.aurora.common.AuroraSwingPopupMenu
import org.pushingpixels.aurora.component.layout.CommandButtonLayoutManager
import org.pushingpixels.aurora.component.model.*
import org.pushingpixels.aurora.component.popup.BaseCommandMenuHandler
import org.pushingpixels.aurora.component.popup.awtColor
import org.pushingpixels.aurora.component.projection.CommandButtonProjection
import org.pushingpixels.aurora.component.ribbon.RibbonApplicationMenuCommandPopupMenuPresentationModel
import org.pushingpixels.aurora.component.ribbon.RibbonApplicationMenuContentModel
import org.pushingpixels.aurora.component.utils.getPlacementAwarePopupShift
import org.pushingpixels.aurora.theming.*
import org.pushingpixels.aurora.theming.colorscheme.AuroraSkinColors
import java.awt.*
import java.awt.geom.Rectangle2D
import javax.swing.JPopupMenu
import javax.swing.border.Border
import kotlin.math.ceil

internal data class RibbonApplicationMenuLevel1ContentLayoutInfo(
    val fullSize: Size,
    val itemButtonPresentationModel: CommandButtonPresentationModel,
)

internal data class RibbonApplicationMenuFooterContentLayoutInfo(
    val fullHeight: Float,
    val footerButtonPresentationModel: CommandButtonPresentationModel,
)

internal object RibbonApplicationMenuPopupHandler : BaseCommandMenuHandler<
        RibbonApplicationMenuContentModel, RibbonApplicationMenuCommandPopupMenuPresentationModel> {
    private fun getLevel1ContentLayoutInfo(
        menuContentModel: RibbonApplicationMenuContentModel,
        menuPresentationModel: RibbonApplicationMenuCommandPopupMenuPresentationModel,
        displayPrototypeCommand: BaseCommand?,
        layoutDirection: LayoutDirection,
        density: Density,
        textStyle: TextStyle,
        fontFamilyResolver: FontFamily.Resolver
    ): RibbonApplicationMenuLevel1ContentLayoutInfo {

        // If at least one secondary command in this popup menu has icon factory
        // we force all command buttons to allocate space for the icon (for overall
        // alignment of content across the entire popup menu)
        var atLeastOneButtonHasIcon = false
        for (commandGroup in menuContentModel.groups) {
            for (secondaryCommand in commandGroup.commands) {
                if (secondaryCommand.icon != null) {
                    atLeastOneButtonHasIcon = true
                }
                if (secondaryCommand.isActionToggle) {
                    atLeastOneButtonHasIcon = true
                }
            }
        }

        // Command presentation for menu content, taking some values from
        // the popup menu presentation model configured on the top-level presentation model
        val itemButtonPresentationModel = CommandButtonPresentationModel(
            presentationState = menuPresentationModel.itemPresentationState,
            iconActiveFilterStrategy = IconFilterStrategy.Original,
            iconEnabledFilterStrategy = IconFilterStrategy.Original,
            iconDisabledFilterStrategy = IconFilterStrategy.ThemedFollowColorScheme,
            forceAllocateSpaceForIcon = atLeastOneButtonHasIcon,
            popupPlacementStrategy = PopupPlacementStrategy.Endward.VAlignTop,
            backgroundAppearanceStrategy = BackgroundAppearanceStrategy.Flat,
            horizontalAlignment = HorizontalAlignment.Fill,
            contentPadding = menuPresentationModel.itemContentPadding,
            isMenu = true,
            sides = Sides.ClosedRectangle
        )

        val layoutManager: CommandButtonLayoutManager =
            itemButtonPresentationModel.presentationState.createLayoutManager(
                layoutDirection = layoutDirection,
                density = density,
                textStyle = textStyle,
                fontFamilyResolver = fontFamilyResolver
            )

        var maxWidth = 0.0f
        var combinedHeight = 0.0f
        for (commandGroup in menuContentModel.groups) {
            for (secondaryCommand in commandGroup.commands) {
                val preferredSize = layoutManager.getPreferredSize(
                    command = secondaryCommand,
                    presentationModel = itemButtonPresentationModel,
                    preLayoutInfo = layoutManager.getPreLayoutInfo(
                        command = secondaryCommand,
                        presentationModel = itemButtonPresentationModel
                    )
                )
                maxWidth = kotlin.math.max(maxWidth, preferredSize.width)
                combinedHeight += preferredSize.height
            }
        }

        return RibbonApplicationMenuLevel1ContentLayoutInfo(
            fullSize = Size(
                width = maxWidth,
                height = combinedHeight
            ),
            itemButtonPresentationModel = itemButtonPresentationModel
        )
    }

    private fun getFooterContentLayoutInfo(
        menuContentModel: RibbonApplicationMenuContentModel,
        menuPresentationModel: RibbonApplicationMenuCommandPopupMenuPresentationModel,
        layoutDirection: LayoutDirection,
        density: Density,
        textStyle: TextStyle,
        fontFamilyResolver: FontFamily.Resolver
    ): RibbonApplicationMenuFooterContentLayoutInfo {
        // Footer command presentation for menu content
        val footerButtonPresentationModel = CommandButtonPresentationModel(
            presentationState = CommandButtonPresentationState.Medium,
            iconActiveFilterStrategy = IconFilterStrategy.Original,
            iconEnabledFilterStrategy = IconFilterStrategy.Original,
            iconDisabledFilterStrategy = IconFilterStrategy.ThemedFollowColorScheme,
            backgroundAppearanceStrategy = BackgroundAppearanceStrategy.Always,
            isMenu = true
        )

        if (menuContentModel.footerCommands.commands.isEmpty()) {
            return RibbonApplicationMenuFooterContentLayoutInfo(
                fullHeight = 0.0f,
                footerButtonPresentationModel = footerButtonPresentationModel
            )
        }

        val layoutManager: CommandButtonLayoutManager =
            footerButtonPresentationModel.presentationState.createLayoutManager(
                layoutDirection = layoutDirection,
                density = density,
                textStyle = textStyle,
                fontFamilyResolver = fontFamilyResolver
            )

        var maxHeight = 0.0f
        for (footerCommand in menuContentModel.footerCommands.commands) {
            val preferredSize = layoutManager.getPreferredSize(
                command = footerCommand,
                presentationModel = footerButtonPresentationModel,
                preLayoutInfo = layoutManager.getPreLayoutInfo(
                    command = footerCommand,
                    presentationModel = footerButtonPresentationModel
                )
            )
            maxHeight = kotlin.math.max(maxHeight, preferredSize.height)
        }

        val footerTopPadding = menuPresentationModel.footerContentPadding.calculateTopPadding()
        val footerBottomPadding = menuPresentationModel.footerContentPadding.calculateBottomPadding()
        return RibbonApplicationMenuFooterContentLayoutInfo(
            fullHeight = maxHeight + (footerTopPadding + footerBottomPadding).value * density.density,
            footerButtonPresentationModel = footerButtonPresentationModel
        )
    }

    @Composable
    private fun generateLevel1Content(
        menuContentModel: RibbonApplicationMenuContentModel,
        menuPresentationModel: RibbonApplicationMenuCommandPopupMenuPresentationModel,
        overlays: Map<Command, CommandButtonPresentationModel.Overlay>,
        level1ContentLayoutInfo: RibbonApplicationMenuLevel1ContentLayoutInfo
    ) {
        val itemButtonPresentationModel = level1ContentLayoutInfo.itemButtonPresentationModel

        val backgroundColorScheme = AuroraSkin.colors.getBackgroundColorScheme(
            decorationAreaType = AuroraSkin.decorationAreaType
        )
        Column(
            modifier = Modifier.fillMaxSize().background(color = backgroundColorScheme.backgroundFillColor)
                .padding(all = 1.0.dp)
        ) {
            for (commandGroup in menuContentModel.groups) {
                for (secondaryCommand in commandGroup.commands) {
                    // Check if we have a presentation overlay for this secondary command
                    val hasOverlay = overlays.containsKey(secondaryCommand)
                    val currSecondaryPresentationModel = if (hasOverlay)
                        itemButtonPresentationModel.overlayWith(overlays[secondaryCommand]!!)
                    else itemButtonPresentationModel
                    // Project a command button for each secondary command, passing the same
                    // overlays into it.
                    CommandButtonProjection(
                        contentModel = secondaryCommand,
                        presentationModel = currSecondaryPresentationModel,
                        overlays = overlays
                    ).project(
                        modifier = Modifier.fillMaxWidth(),
                        actionInteractionSource = remember { MutableInteractionSource() },
                        popupInteractionSource = remember { MutableInteractionSource() }
                    )
                }
            }
        }
    }

    @Composable
    private fun generateFooterContent(
        menuContentModel: RibbonApplicationMenuContentModel,
        menuPresentationModel: RibbonApplicationMenuCommandPopupMenuPresentationModel,
        overlays: Map<Command, CommandButtonPresentationModel.Overlay>,
        footerContentLayoutInfo: RibbonApplicationMenuFooterContentLayoutInfo
    ) {
        val footerButtonPresentationModel = footerContentLayoutInfo.footerButtonPresentationModel
        val backgroundColorScheme = AuroraSkin.colors.getBackgroundColorScheme(
            decorationAreaType = AuroraSkin.decorationAreaType
        )
        Row(
            modifier = Modifier.fillMaxSize()
                .background(color = backgroundColorScheme.accentedBackgroundFillColor)
                .padding(menuPresentationModel.footerContentPadding),
            horizontalArrangement = Arrangement.End
        ) {
            for (footerCommand in menuContentModel.footerCommands.commands) {
                // Check if we have a presentation overlay for this footer command
                val hasOverlay = overlays.containsKey(footerCommand)
                val currFooterPresentationModel = if (hasOverlay)
                    footerButtonPresentationModel.overlayWith(overlays[footerCommand]!!)
                else footerButtonPresentationModel
                // Project a command button for each footer command, passing the same
                // overlays into it.
                CommandButtonProjection(
                    contentModel = footerCommand,
                    presentationModel = currFooterPresentationModel,
                    overlays = overlays
                ).project(
                    modifier = Modifier.fillMaxWidth(),
                    actionInteractionSource = remember { MutableInteractionSource() },
                    popupInteractionSource = remember { MutableInteractionSource() }
                )
            }
        }
    }

    @OptIn(AuroraInternalApi::class)
    override fun showPopupContent(
        popupOriginator: Component,
        layoutDirection: LayoutDirection,
        density: Density,
        textStyle: TextStyle,
        fontFamilyResolver: FontFamily.Resolver,
        skinColors: AuroraSkinColors,
        skinPainters: AuroraPainters,
        decorationAreaType: DecorationAreaType,
        compositionLocalContext: CompositionLocalContext,
        anchorBoundsInWindow: Rect,
        popupTriggerAreaInWindow: Rect,
        contentModel: State<RibbonApplicationMenuContentModel?>,
        presentationModel: RibbonApplicationMenuCommandPopupMenuPresentationModel,
        displayPrototypeCommand: BaseCommand?,
        toDismissPopupsOnActivation: Boolean,
        popupPlacementStrategy: PopupPlacementStrategy,
        overlays: Map<Command, CommandButtonPresentationModel.Overlay>
    ) {
        val popupOriginatorLocationOnScreen = popupOriginator.locationOnScreen
        val currentScreenBounds = popupOriginator.graphicsConfiguration.bounds
        popupOriginatorLocationOnScreen.translate(-currentScreenBounds.x, -currentScreenBounds.y)

        val level1ContentLayoutInfo = getLevel1ContentLayoutInfo(
            menuContentModel = contentModel.value!!,
            menuPresentationModel = presentationModel,
            displayPrototypeCommand = displayPrototypeCommand,
            layoutDirection = layoutDirection,
            density = density,
            textStyle = textStyle,
            fontFamilyResolver = fontFamilyResolver
        )
        val footerContentLayoutInfo = getFooterContentLayoutInfo(
            menuContentModel = contentModel.value!!,
            menuPresentationModel = presentationModel,
            layoutDirection = layoutDirection,
            density = density,
            textStyle = textStyle,
            fontFamilyResolver = fontFamilyResolver
        )

        // From this point, all coordinates are in Swing display units - which are density independent.
        // The popup width and height is converted from pixels into dp (density-independent units),
        // and then passed those as is (the numeric value) to Swing / AWT

        // Full size of the popup accounts for extra pixel on each side for the popup border
        val fullPopupWidth = ceil(level1ContentLayoutInfo.fullSize.width / density.density).toInt() + 2
        val fullPopupHeight = ceil((level1ContentLayoutInfo.fullSize.height + footerContentLayoutInfo.fullHeight)
                / density.density).toInt() + 2

        val initialAnchorX = if (layoutDirection == LayoutDirection.Ltr)
            (popupOriginatorLocationOnScreen.x + anchorBoundsInWindow.left).toInt() else
            (popupOriginatorLocationOnScreen.x + anchorBoundsInWindow.left + anchorBoundsInWindow.width).toInt() - fullPopupWidth
        // Initial anchor corresponds to the on-screen location of the top-left corner of the
        // popup window under the default PopupPlacementStrategy.Downward.HAlignStart placement
        // strategy
        val initialAnchor = IntOffset(
            x = initialAnchorX,
            y = (popupOriginatorLocationOnScreen.y + anchorBoundsInWindow.top).toInt()
        )

        val popupShift = getPlacementAwarePopupShift(
            ltr = (layoutDirection == LayoutDirection.Ltr),
            anchorDimension = IntSize(
                width = anchorBoundsInWindow.width.toInt(),
                height = anchorBoundsInWindow.height.toInt()
            ),
            popupDimension = IntSize(fullPopupWidth, fullPopupHeight),
            popupPlacementStrategy = popupPlacementStrategy
        )
        val popupRect = Rectangle(
            initialAnchor.x + popupShift.width,
            initialAnchor.y + anchorBoundsInWindow.height.toInt() + popupShift.height,
            fullPopupWidth,
            fullPopupHeight
        )

        // Make sure the popup stays in screen bounds
        val screenBounds = popupOriginator.graphicsConfiguration.bounds
        if (popupRect.x < 0) {
            popupRect.translate(-popupRect.x, 0)
        }
        if ((popupRect.x + popupRect.width) > screenBounds.width) {
            popupRect.translate(
                screenBounds.width - popupRect.x - popupRect.width,
                0
            )
        }
        if (popupRect.y < 0) {
            popupRect.translate(0, -popupRect.y)
        }
        if ((popupRect.y + popupRect.height) > screenBounds.height) {
            popupRect.translate(
                0,
                screenBounds.height - popupRect.y - popupRect.height
            )
        }

        val popupContent = ComposePanel()
        val fillColor = skinColors.getBackgroundColorScheme(decorationAreaType).backgroundFillColor
        val awtFillColor = fillColor.awtColor
        popupContent.background = awtFillColor

        val borderScheme = skinColors.getColorScheme(
            decorationAreaType = DecorationAreaType.None,
            associationKind = ColorSchemeAssociationKind.Border,
            componentState = ComponentState.Enabled
        )
        val popupBorderColor = skinPainters.borderPainter.getRepresentativeColor(borderScheme)
        val awtBorderColor = popupBorderColor.awtColor
        val borderThickness = 1.0f / density.density

        popupContent.border = object : Border {
            override fun paintBorder(
                c: Component,
                g: Graphics,
                x: Int,
                y: Int,
                width: Int,
                height: Int
            ) {
                val g2d = g.create() as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.setRenderingHint(
                    RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE
                )

                g2d.color = awtFillColor
                g2d.fill(Rectangle(0, 0, width, height))

                val thickness = 0.5f
                g2d.stroke = BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)
                g2d.color = awtBorderColor
                g2d.draw(
                    Rectangle2D.Float(
                        borderThickness / 2.0f, borderThickness / 2.0f,
                        width - borderThickness, height - borderThickness
                    )
                )
                g2d.dispose()
            }

            override fun getBorderInsets(c: Component?): Insets {
                return Insets(1, 1, 1, 1)
            }

            override fun isBorderOpaque(): Boolean {
                return false
            }
        }
        popupContent.preferredSize = Dimension(popupRect.width, popupRect.height)

        val popupDpSize = DpSize(
            width = (popupRect.width / density.density).dp,
            height = (popupRect.height / density.density).dp
        )

        // This line is needed to ensure that each popup is displayed in its own heavyweight window
        JPopupMenu.setDefaultLightWeightPopupEnabled(false)

        val popupMenu = AuroraSwingPopupMenu(toDismissPopupsOnActivation)
        popupContent.setContent {
            // Get the current composition context
            CompositionLocalProvider(compositionLocalContext) {
                // And add the composition locals for the new popup
                CompositionLocalProvider(
                    LocalPopupMenu provides popupMenu,
                    LocalWindowSize provides popupDpSize
                ) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(1.0f)
                            .height(height = (level1ContentLayoutInfo.fullSize.height / density.density).dp)) {
                            generateLevel1Content(
                                menuContentModel = contentModel.value!!,
                                menuPresentationModel = presentationModel,
                                overlays = overlays,
                                level1ContentLayoutInfo = level1ContentLayoutInfo
                            )
                        }
                        generateFooterContent(
                            menuContentModel = contentModel.value!!,
                            menuPresentationModel = presentationModel,
                            overlays = overlays,
                            footerContentLayoutInfo = footerContentLayoutInfo
                        )
                    }
                }
            }
        }
        popupMenu.add(popupContent)

        // Hide the popups that "start" from our popup originator
        AuroraPopupManager.hidePopups(originator = popupOriginator)
        // And display our new popup content
        AuroraPopupManager.showPopup(
            originator = popupOriginator,
            popupTriggerAreaInOriginatorWindow = popupTriggerAreaInWindow,
            popup = popupMenu,
            popupContent = popupContent,
            popupRectOnScreen = popupRect,
            popupKind = AuroraPopupManager.PopupKind.Popup
        )
    }
}
