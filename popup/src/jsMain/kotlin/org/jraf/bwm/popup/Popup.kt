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

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul
import org.jetbrains.compose.web.renderComposable
import org.jraf.bwm.shared.messaging.Messenger
import org.jraf.bwm.shared.model.SavedWindow

class Popup {
  private val messenger = Messenger()

  fun start() {
    renderComposable(rootElementId = "root") {
      var savedWindows: List<SavedWindow> by remember { mutableStateOf(emptyList()) }
      LaunchedEffect(Unit) {
        savedWindows = messenger.sendGetSavedWindowsMessage()
      }
      Ul {
        for (savedWindow in savedWindows) {
          Li(
            attrs = {
              onClick {
                messenger.sendOpenOrFocusSavedWindowMessage(savedWindow.id)
              }
            },
          ) {
            Text(savedWindow.name)
            Ul {
              for (savedTab in savedWindow.tabs) {
                Li {
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
