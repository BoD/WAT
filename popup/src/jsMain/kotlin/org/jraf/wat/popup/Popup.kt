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

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import chrome.runtime.onMessage
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import org.jetbrains.compose.web.renderComposable
import org.jraf.wat.shared.messaging.Messenger
import org.jraf.wat.shared.messaging.PublishWatWindows
import org.jraf.wat.shared.messaging.asMessage
import org.jraf.wat.shared.repository.wat.WatWindow

class Popup {
  private val messenger = Messenger()

  private val watWindows: MutableStateFlow<List<WatWindow>> = MutableStateFlow(emptyList())

  fun start() {
    window.resizeTo(window.outerWidth, window.screen.availHeight)
    registerMessageListener()
    messenger.sendRequestPublishWatWindows()

    renderComposable(rootElementId = "root") {
      val watWindows: List<WatWindow> by watWindows.collectAsState()
      var watWindowIdBeingEdited: String? by remember { mutableStateOf(null) }
      var watWindowNameBeingEdited: String? by remember { mutableStateOf(null) }

      for (watWindow in watWindows) {
        Li(
          attrs = {
            classes(
              buildList {
                add("window")
                if (watWindow.focused) {
                  add("focused")
                }
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
                messenger.sendSetTreeExpandedMessage(watWindowId = watWindow.id, treeExpanded = !watWindow.treeExpanded)
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
              classes("name")
              autoFocus()
              onInput { watWindowNameBeingEdited = it.value }
              onKeyUp {
                when (it.key) {
                  "Enter" -> {
                    messenger.sendSaveWatWindowMessage(watWindowId = watWindowIdBeingEdited!!, windowName = watWindowNameBeingEdited!!)
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

          } else {
            Span(
              attrs = {
                classes("name")
                onClick {
                  messenger.sendFocusOrCreateWatWindowMessage(watWindowId = watWindow.id, tabIndex = null)
                }
                onDoubleClick {
                  messenger.sendSetTreeExpandedMessage(watWindowId = watWindow.id, treeExpanded = !watWindow.treeExpanded)
                }
              },
            ) {
              Text(watWindow.name)
              if (!watWindow.isSaved) {
                Text(" *")
              }
            }
          }
          if (watWindow.isSaved) {
            Span(
              attrs = {
                classes("actionIcon")
                onClick {
                  if (watWindowIdBeingEdited != null) {
                    messenger.sendSaveWatWindowMessage(watWindowId = watWindowIdBeingEdited!!, windowName = watWindowNameBeingEdited!!)
                    watWindowIdBeingEdited = null
                    watWindowNameBeingEdited = null
                  } else {
                    watWindowIdBeingEdited = watWindow.id
                    watWindowNameBeingEdited = watWindow.name
                  }
                }
              },
            ) {
              Text(" ✏️")
            }
            // Only show the unsave icon if the window is bound, because otherwise it's dangerous
            // Also don't show it if the window is being edited, because it would be confusing
            if (watWindow.isBound && watWindowIdBeingEdited == null) {
              Span(
                attrs = {
                  classes("actionIcon")
                  onClick {
                    messenger.sendUnsaveWatWindowMessage(watWindowId = watWindow.id)
                  }
                },
              ) {
                Text(" 🗑")
              }
            }
          } else {
            Span(
              attrs = {
                classes("actionIcon")
                onClick {
                  if (watWindowIdBeingEdited != null) {
                    messenger.sendSaveWatWindowMessage(watWindowId = watWindowIdBeingEdited!!, windowName = watWindowNameBeingEdited!!)
                    watWindowIdBeingEdited = null
                    watWindowNameBeingEdited = null
                  } else {
                    watWindowIdBeingEdited = watWindow.id
                    watWindowNameBeingEdited = watWindow.name
                  }
                }
              },
            ) {
              Text(" 💾")
            }
          }
        }

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
                  messenger.sendFocusOrCreateWatWindowMessage(watWindowId = watWindow.id, tabIndex = i)
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
              Span(
                attrs = {
                  classes("name")
                },
              ) {
                Text(watTab.title.takeIf { it.isNotBlank() } ?: watTab.url.takeIf { it.isNotBlank() } ?: "Loading…")
              }
            }
          }
        }
      }
    }
  }

  private fun registerMessageListener() {
    onMessage.addListener { msg, _, sendResponse ->
      when (val message = msg.asMessage()) {
        is PublishWatWindows -> {
          watWindows.value = message.watWindows
        }

        else -> {
          // Ignore
        }
      }
      // Return true to have the right to respond asynchronously
      // https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/runtime/onMessage#sending_an_asynchronous_response_using_sendresponse
      return@addListener true
    }
  }

}
