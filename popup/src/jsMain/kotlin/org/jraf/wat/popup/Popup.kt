/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2025-present Benoit 'BoD' Lubek (BoD@JRAF.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jraf.wat.popup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.web.events.SyntheticDragEvent
import androidx.compose.web.events.SyntheticMouseEvent
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.Draggable
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Hr
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import org.jetbrains.compose.web.dom.Ul
import org.jetbrains.compose.web.renderComposable
import org.jraf.wat.shared.messaging.Messenger
import org.jraf.wat.shared.messaging.PublishWatWindowsMessage
import org.jraf.wat.shared.messaging.asMessage
import org.jraf.wat.shared.model.WatWindow
import org.jraf.wat.shared.util.decodeSuspended
import org.w3c.dom.HTMLDialogElement
import org.w3c.dom.HTMLInputElement
import kotlin.js.Date

class Popup {
  private val messenger = Messenger()

  private val watWindows: MutableStateFlow<List<WatWindow>> = MutableStateFlow(emptyList())
  private val snackBarSpec: MutableStateFlow<SnackBarSpec?> = MutableStateFlow(null)

  fun start() {
    window.resizeTo(window.outerWidth, window.screen.availHeight)
    registerMessageListener()
    messenger.requestPublishWatWindows()

    renderComposable(rootElementId = "root") {
      val watWindows: List<WatWindow> by watWindows.collectAsState()
      val snackBarSpec: SnackBarSpec? by snackBarSpec.collectAsState()
      WindowList(watWindows)
      SettingsDialog()
      if (snackBarSpec != null) {
        SnackBar(snackBarSpec!!)
      }
    }
  }

  @Composable
  private fun WindowList(watWindows: List<WatWindow>) {
    var watWindowIdBeingEdited: String? by remember { mutableStateOf(null) }
    var watWindowNameBeingEdited: String? by remember { mutableStateOf(null) }

    for ((i, watWindow) in watWindows.withIndex()) {
      Ul(
        attrs = {
          classes(
            buildList {
              if (watWindow.focused) {
                add("focused")
              }
              if (watWindow.isBound) {
                add("bound")
              }
            },
          )
          draggable(Draggable.True)
          onDragStart { e ->
            // Hack: we set the id inside the mime type so that we can retrieve it in onDragOver
            e.dataTransfer!!.setData("$DATA_PREFIX${watWindow.id}", watWindow.id)
          }
          onDragOver { e ->
            val draggedWatWindowId = e.watWindowId ?: return@onDragOver
            if (draggedWatWindowId == watWindow.id) return@onDragOver
            e.preventDefault()
            e.dataTransfer!!.dropEffect = "move"
            // Make the drop target visible
            val isBefore = watWindows.indexOfFirst { it.id == watWindow.id } < watWindows.indexOfFirst { it.id == draggedWatWindowId }
            val dropTargetId = if (isBefore) {
              "dropTarget-before-${watWindow.id}"
            } else {
              "dropTarget-after-${watWindow.id}"
            }
            val dropTarget = document.getElementById(dropTargetId) ?: return@onDragOver
            dropTarget.classList.add("dragOver")
          }
          onDragLeave { e ->
            e.preventDefault()
            // Make the drop target invisible
            val dropTargetBefore = document.getElementById("dropTarget-before-${watWindow.id}")
            dropTargetBefore?.classList?.remove("dragOver")
            val dropTargetAfter = document.getElementById("dropTarget-after-${watWindow.id}")
            dropTargetAfter?.classList?.remove("dragOver")
          }
          onDrop { e ->
            val draggedWatWindowId = e.watWindowId ?: return@onDrop
            if (draggedWatWindowId == watWindow.id) return@onDrop
            e.preventDefault()
            // Make the drop target invisible
            val dropTargetBefore = document.getElementById("dropTarget-before-${watWindow.id}")
            dropTargetBefore?.classList?.remove("dragOver")
            val dropTargetAfter = document.getElementById("dropTarget-after-${watWindow.id}")
            dropTargetAfter?.classList?.remove("dragOver")
            val isBefore = watWindows.indexOfFirst { it.id == watWindow.id } < watWindows.indexOfFirst { it.id == draggedWatWindowId }
            messenger.reorderWatWindows(
              toReorderWatWindowId = draggedWatWindowId,
              relativeToWatWindowId = watWindow.id,
              isBefore = isBefore,
            )
          }
        },
      ) {
        // Drop target before
        Hr(
          attrs = {
            id("dropTarget-before-${watWindow.id}")
            classes("dropTarget", "before")
          },
        )

        // Window name
        Li(
          attrs = {
            classes(
              buildList {
                add("window")
                if (watWindow.isBound) {
                  add("bound")
                }
              },
            )
          },
        ) {
          Span(
            attrs = {
              classes("treeExpander")
              onClick {
                messenger.setTreeExpanded(watWindowId = watWindow.id, treeExpanded = !watWindow.treeExpanded)
              }
            },
          ) {
            Text(
              if (watWindow.treeExpanded) {
                "▾"
              } else {
                "▸"
              },
            )
          }
          if (watWindow.id == watWindowIdBeingEdited) {
            TextInput(
              value = watWindowNameBeingEdited!!,
            ) {
              id("watWindowNameInput")
              classes("name")
              onFocus {
                (it.target as HTMLInputElement).select()
              }
              onInput { watWindowNameBeingEdited = it.value }
              onKeyUp {
                when (it.key) {
                  "Enter" -> {
                    messenger.saveWatWindow(watWindowId = watWindowIdBeingEdited!!, windowName = watWindowNameBeingEdited!!)
                    watWindowIdBeingEdited = null
                    watWindowNameBeingEdited = null
                  }

                  "Escape" -> {
                    watWindowIdBeingEdited = null
                    watWindowNameBeingEdited = null
                  }
                }
              }
            }

            LaunchedEffect(Unit) {
              // Focus the input when it is created
              (document.getElementById("watWindowNameInput") as? HTMLInputElement)?.focus()
            }

          } else {
            Span(
              attrs = {
                classes("name")
                onClick {
                  messenger.focusOrCreateWatWindow(watWindowId = watWindow.id, tabIndex = null)
                }
                onDoubleClick {
                  messenger.setTreeExpanded(watWindowId = watWindow.id, treeExpanded = !watWindow.treeExpanded)
                }
              },
            ) {
              Text(watWindow.name)
              if (!watWindow.isSaved) {
                Text(" *")
              }

              if (!watWindow.treeExpanded) {
                Span(
                  attrs = {
                    classes("count")
                  },
                ) {
                  Text("${watWindow.tabs.size}")
                }
              }
            }
          }

          // Actions
          ActionIcon(
            if (watWindow.isSaved) {
              "✏️"
            } else {
              "💾"
            },
          ) {
            if (watWindowIdBeingEdited != null) {
              messenger.saveWatWindow(watWindowId = watWindowIdBeingEdited!!, windowName = watWindowNameBeingEdited!!)
              watWindowIdBeingEdited = null
              watWindowNameBeingEdited = null
            } else {
              watWindowIdBeingEdited = watWindow.id
              watWindowNameBeingEdited = watWindow.name
            }
          }

          // Only show the unsave icon if the window is bound, because otherwise it's dangerous
          // Also don't show it if the window is being edited, because it would be confusing
          if (watWindow.isSaved && watWindow.isBound && watWindowIdBeingEdited == null) {
            ActionIcon("🗑") {
              messenger.unsaveWatWindow(watWindowId = watWindow.id)
            }
          }

          // Add Settings action on the first window only
          // Also don't show it if the window is being edited, because it would be confusing
          if (i == 0 && watWindowIdBeingEdited == null) {
            Span(
              attrs = {
                classes("actionIcon")
                onClick {
                  (document.getElementById("settingsDialog") as? HTMLDialogElement)?.showModal()
                }
              },
            ) {
              Text("⚙️")
            }
          }
        }

        // Tabs
        if (watWindow.treeExpanded) {
          for ((i, watTab) in watWindow.tabs.withIndex()) {
            Li(
              attrs = {
                classes(
                  buildList {
                    add("tab")
                    if (watWindow.focused && watTab.active) {
                      add("active")
                    }
                    if (watWindow.isBound) {
                      add("bound")
                    }
                  },
                )
                onClick {
                  messenger.focusOrCreateWatWindow(watWindowId = watWindow.id, tabIndex = i)
                }
              },
            ) {
              if (watTab.favIconUrl.isNullOrBlank()) {
                Span(
                  attrs = {
                    classes("favIcon")
                  },
                ) {
                  Text("🌍")
                }
              } else {
                Img(
                  src = watTab.favIconUrl!!,
                  attrs = {
                    classes("favIcon")
                  },
                )
              }
              Div(
                attrs = {
                  classes("nameAndUrl")
                },
              ) {
                Span(
                  attrs = {
                    classes("name")
                  },
                ) {
                  Text(watTab.title.takeIf { it.isNotBlank() } ?: watTab.url.takeIf { it.isNotBlank() } ?: "Loading…")
                }
                Span(
                  attrs = {
                    classes("url")
                  },
                ) {
                  Text(watTab.url.prettyUrl())
                }
              }
            }
          }
        }

        // Drop target after
        Hr(
          attrs = {
            id("dropTarget-after-${watWindow.id}")
            classes("dropTarget", "after")
          },
        )
      }
    }
  }

  @Composable
  private fun ActionIcon(icon: String, onClick: (SyntheticMouseEvent) -> Unit) {
    Span(
      attrs = {
        classes("actionIcon")
        onClick(onClick)
      },
    ) {
      Text(icon)
    }
  }

  @Composable
  private fun SettingsDialog() {
    Dialog(
      attrs = {
        id("settingsDialog")
        classes("menu")
        onClick {
          // Close the dialog when clicking anywhere
          (document.getElementById("settingsDialog") as? HTMLDialogElement)?.close()
        }
      },
    ) {
      Li(
        attrs = {
          onClick {
            GlobalScope.launch {
              val ok = messenger.import(navigator.clipboard.readText().await())
              snackBarSpec.value = if (ok) {
                SnackBarSpec(
                  message = "Import successful",
                  className = "success",
                )
              } else {
                SnackBarSpec(
                  message = "Import from clipboard failed",
                  className = "error",
                )
              }
            }
          }
        },
      ) {
        Text("Import...")
      }
      Li(
        attrs = {
          onClick {
            GlobalScope.launch {
              navigator.clipboard.writeText(messenger.getExport()).await()
              snackBarSpec.value = SnackBarSpec(
                message = "Exported to clipboard",
                className = "success",
              )
            }
          }
        },
      ) {
        Text("Export...")
      }
    }
  }

  @Composable
  private fun SnackBar(snackBarSpec: SnackBarSpec) {
    Div(
      attrs = {
        classes("snackBar", snackBarSpec.className)
      },
    ) {
      LaunchedEffect(snackBarSpec) {
        delay(4000)
        this@Popup.snackBarSpec.value = null
      }
      Text(snackBarSpec.message)
    }
  }

  private val SyntheticDragEvent.watWindowId: String?
    get() = dataTransfer?.types?.firstOrNull()?.takeIf { it.startsWith(DATA_PREFIX) }?.removePrefix(DATA_PREFIX)

  private fun registerMessageListener() {
    chrome.runtime.onMessage.addListener { msg, _, sendResponse ->
      when (val message = msg.asMessage()) {
        is PublishWatWindowsMessage -> {
          watWindows.value = message.watWindows
        }

        else -> {
          // Ignore
        }
      }
      // Return true to have the right to respond asynchronously
      // https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/runtime/onMessage#sending_an_asynchronous_response_using_sendresponse
      // We don't need to respond, so we return false
      return@addListener false
    }
  }

  companion object {
    const val DATA_PREFIX = "application/wat+"
  }
}

private class SnackBarSpec(
  val message: String,
  val className: String,
) {
  override fun equals(other: Any?): Boolean {
    return false
  }

  override fun hashCode(): Int {
    return Date().getMilliseconds()
  }
}

private fun String.prettyUrl(): String {
  return decodeSuspended()
    .removePrefix("https://")
    .removePrefix("http://")
    .removePrefix("www.")
    .simplifyGitHub()
    // xyz.com -> xyz
    .replace(Regex("^([^/.]+)\\.com(.*)"), "$1$2")
    .replace("/", " / ")
}

// github.com/$org/$proj/xxxx/y/z -> github/$proj/xxxx/y/z
private fun String.simplifyGitHub(): String {
  return this
    .replace(Regex("^github\\.com/([^/]+)/$"), "github.com/$1")
    .replace(Regex("^github\\.com/([^/]+)/(.+)$"), "github.com/$2")
}


