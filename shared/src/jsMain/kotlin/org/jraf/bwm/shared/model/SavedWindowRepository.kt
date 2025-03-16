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

package org.jraf.bwm.shared.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SavedWindowRepository {
  private val savedWindows = listOf(
    SavedWindow(
      name = "Dev",
      id = Uuid.random().toHexString(),
      tabs = listOf(
        SavedTab(
          title = "Google",
          url = "https://www.google.com",
          favIconUrl = "https://www.google.com/favicon.ico",
        ),
        SavedTab(
          title = "GitHub",
          url = "https://www.github.com",
          favIconUrl = "https://www.github.com/favicon.ico",
        ),
      ),
    ),
    SavedWindow(
      name = "Personal",
      id = Uuid.random().toHexString(),
      tabs = listOf(
        SavedTab(
          title = "Reddit",
          url = "https://www.reddit.com",
          favIconUrl = "https://www.reddit.com/favicon.ico",
        ),
        SavedTab(
          title = "JRAF",
          url = "https://JRAF.org",
          favIconUrl = "https://JRAF.org/favicon.ico",
        ),
      ),
    ),
  )


  fun loadSavedWindows(): List<SavedWindow> {
    return savedWindows
  }

  private val windowIdToSavedWindow = mutableMapOf<Number, SavedWindow>()

  fun bindSavedWindow(windowId: Number, savedWindow: SavedWindow) {
    check(getWindowIdBySavedWindow(savedWindow) == null) { "SavedWindow is already bound to a window" }
    windowIdToSavedWindow[windowId] = savedWindow
  }

  fun unbindSavedWindow(windowId: Number) {
    windowIdToSavedWindow.remove(windowId)
  }

  fun getSavedWindowByWindowId(windowId: Number): SavedWindow? {
    return windowIdToSavedWindow[windowId]
  }

  fun getWindowIdBySavedWindow(savedWindow: SavedWindow): Number? {
    return windowIdToSavedWindow.entries.firstOrNull { it.value == savedWindow }?.key
  }

  fun getSavedWindowById(id: String): SavedWindow? {
    return savedWindows.firstOrNull { it.id == id }
  }
}
