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
import chrome.runtime.onMessage
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.web.dom.B
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul
import org.jetbrains.compose.web.renderComposable
import org.jraf.wat.shared.messaging.Messenger
import org.jraf.wat.shared.messaging.PublishWatWindows
import org.jraf.wat.shared.messaging.asMessage
import org.jraf.wat.shared.model.WatWindow

class Popup {
  private val messenger = Messenger()

  private val watWindows: MutableStateFlow<List<WatWindow>> = MutableStateFlow(emptyList())

  fun start() {
    registerMessageListener()
    messenger.sendRequestPublishWatWindows()

    renderComposable(rootElementId = "root") {
      val watWindows: List<WatWindow> by watWindows.collectAsState()
      Ul {
        for (watWindow in watWindows) {
          Li(
            attrs = {
              onClick {
                messenger.sendFocusOrCreateWatWindowMessage(watWindow)
              }
            },
          ) {
            if (watWindow.focused) {
              B {
                Text(watWindow.name)
              }
            } else {
              Text(watWindow.name)
            }
            if (!watWindow.isSaved) {
              Span(
                attrs = {
                  onClick {
                    it.stopPropagation()
                    val windowName: String? = js("""prompt("Window name:")""")
                    if (!windowName.isNullOrBlank()) {
                      messenger.sendSaveWatWindowMessage(watWindow = watWindow, windowName = windowName.trim())
                    }
                  }
                },
              ) {
                Text(" 💾")
              }
            }
            Ul {
              for (savedTab in watWindow.tabs) {
                Li {
                  if (watWindow.focused && savedTab.active) {
                    B {
                      Text(savedTab.title)
                    }
                  } else {
                    Text(savedTab.title)
                  }
                }
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
