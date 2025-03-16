/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2024-present Benoit 'BoD' Lubek (BoD@JRAF.org)
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

package org.jraf.bwm.popup

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.web.dom.B
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul
import org.jetbrains.compose.web.renderComposable
import org.jraf.bwm.shared.messaging.Messenger
import org.jraf.bwm.shared.messaging.PublishBwmWindows
import org.jraf.bwm.shared.messaging.asMessage
import org.jraf.bwm.shared.model.BwmWindow

class Popup {
  private val messenger = Messenger()

  private val bwmWindows: MutableStateFlow<List<BwmWindow>> = MutableStateFlow(emptyList())

  fun start() {
    registerMessageListener()
    messenger.sendRequestPublishBwmWindows()

    renderComposable(rootElementId = "root") {
      val bwmWindows: List<BwmWindow> by bwmWindows.collectAsState()
      Ul {
        for (bwmWindow in bwmWindows) {
          Li(
            attrs = {
              onClick {
                messenger.sendFocusOrCreateBwmWindowMessage(bwmWindow)
              }
            },
          ) {
            if (bwmWindow.focused) {
              B {
                Text(bwmWindow.name ?: "Unsaved")
              }
            } else {
              Text(bwmWindow.name ?: "Unsaved")
            }
            Ul {
              for (savedTab in bwmWindow.tabs) {
                Li {
                  if (bwmWindow.focused && savedTab.active) {
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
    chrome.runtime.onMessage.addListener { msg, _, sendResponse ->
      when (val message = msg.asMessage()) {
        is PublishBwmWindows -> {
          bwmWindows.value = message.bwmWindows
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
