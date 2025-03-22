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

import chrome.windows.Window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.js.Date
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class BwmWindowRepository {
  private val savedWindows = listOf(
    BwmWindow(
      id = Uuid.random().toHexString(),
      name = "Dev",
      isSaved = true,
      systemWindowId = null,
      focused = false,
      tabs = listOf(
        BwmTab(
          title = "Google",
          url = "https://www.google.com",
          favIconUrl = "https://www.google.com/favicon.ico",
          active = false,
        ),
        BwmTab(
          title = "GitHub",
          url = "https://www.github.com",
          favIconUrl = "https://www.github.com/favicon.ico",
          active = false,
        ),
      ),
    ),
    BwmWindow(
      id = Uuid.random().toHexString(),
      name = "Personal",
      isSaved = true,
      systemWindowId = null,
      focused = false,
      tabs = listOf(
        BwmTab(
          title = "Reddit",
          url = "https://www.reddit.com",
          favIconUrl = "https://www.reddit.com/favicon.ico",
          active = false,
        ),
        BwmTab(
          title = "JRAF",
          url = "https://JRAF.org",
          favIconUrl = "https://JRAF.org/favicon.ico",
          active = false,
        ),
      ),
    ),
  )

  private val _bwmWindows: MutableStateFlow<List<BwmWindow>> = MutableStateFlow(savedWindows)
  val bwmWindows: StateFlow<List<BwmWindow>> = _bwmWindows

  fun bind(bwmWindowId: String, systemWindowId: Int) {
    val bwmWindow = getWindow(bwmWindowId)
    check(bwmWindow != null) { "BwmWindow $bwmWindowId not found" }
    check(!bwmWindow.isBound) { "BwmWindow $bwmWindowId is already bound to a system window" }
    _bwmWindows.value = _bwmWindows.value.map { bwmWindow ->
      if (bwmWindow.id == bwmWindowId) {
        bwmWindow.copy(systemWindowId = systemWindowId)
      } else {
        bwmWindow
      }
    }
  }

  fun unbind(systemWindowId: Int) {
    _bwmWindows.value = _bwmWindows.value.mapNotNull {
      if (it.systemWindowId == systemWindowId) {
        if (it.isSaved) {
          it.copy(systemWindowId = null)
        } else {
          null
        }
      } else {
        it
      }
    }
  }

  fun addSystemWindows(systemWindows: List<Window>) {
    _bwmWindows.value = _bwmWindows.value + systemWindows
      // Ignore windows that are already bound
      .filterNot {
        _bwmWindows.value.any { bwmWindow -> bwmWindow.systemWindowId == it.id }
      }
      .map { systemWindow ->
        BwmWindow(
          id = Uuid.random().toHexString(),
          name = Date().toLocaleString(
            options = dateLocaleOptions {
              year = "numeric"
              month = "short"
              day = "2-digit"
              hour = "2-digit"
              minute = "2-digit"
            },
          ),
          isSaved = false,
          systemWindowId = systemWindow.id!!,
          focused = systemWindow.focused,
          tabs = systemWindow.tabs?.map { systemTab ->
            BwmTab(
              title = systemTab.title,
              url = systemTab.url,
              favIconUrl = systemTab.favIconUrl,
              active = systemTab.active,
            )
          } ?: emptyList(),
        )
      }
  }

  fun addSystemWindow(systemWindow: Window) {
    addSystemWindows(listOf(systemWindow))
  }

  fun saveWindow(bwmWindowId: String, name: String) {
    _bwmWindows.value = _bwmWindows.value.map {
      if (it.id == bwmWindowId) {
        it.copy(
          name = name,
          isSaved = true,
        )
        // TODO: save to storage
      } else {
        it
      }
    }
  }

  private fun getWindow(bwmWindowId: String): BwmWindow? = _bwmWindows.value.firstOrNull { it.id == bwmWindowId }

  fun updateBwmWindows(systemWindows: List<Window>) {
    _bwmWindows.value = _bwmWindows.value.map { bwmWindow ->
      val systemWindow = systemWindows.firstOrNull { it.id == bwmWindow.systemWindowId }
      if (systemWindow != null) {
        bwmWindow.copy(
          focused = systemWindow.focused,
          tabs = systemWindow.tabs?.map { systemTab ->
            BwmTab(
              title = systemTab.title,
              url = systemTab.url,
              favIconUrl = systemTab.favIconUrl,
              active = systemTab.active,
            )
          } ?: emptyList(),
        )
      } else {
        bwmWindow
      }
    }
  }

  fun focusSystemWindow(systemWindowId: Int) {
    _bwmWindows.value = _bwmWindows.value.map { bwmWindow ->
      if (bwmWindow.systemWindowId == systemWindowId) {
        bwmWindow.copy(focused = true)
      } else {
        bwmWindow.copy(focused = false)
      }
    }
  }
}
