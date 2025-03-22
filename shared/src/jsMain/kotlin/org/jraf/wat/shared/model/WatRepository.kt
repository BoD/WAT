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

package org.jraf.wat.shared.model

import chrome.windows.Window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jraf.wat.shared.settings.SettingsRepository
import kotlin.js.Date
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class WatRepository {
  private val settingsRepository = SettingsRepository()

  private val _watWindows: MutableStateFlow<List<WatWindow>> = MutableStateFlow(emptyList())
  val watWindows: StateFlow<List<WatWindow>> = _watWindows

  init {
    GlobalScope.launch {
      settingsRepository.settings.collect { settings ->
        addSavedWatWindows(settings.savedWatWindows)
      }
    }
  }

  fun bind(watWindowId: String, systemWindowId: Int) {
    val watWindow = getWindow(watWindowId)
    check(watWindow != null) { "WatWindow $watWindowId not found" }
    check(!watWindow.isBound) { "WatWindow $watWindowId is already bound to a system window" }
    _watWindows.value = _watWindows.value.map { watWindow ->
      if (watWindow.id == watWindowId) {
        watWindow.copy(systemWindowId = systemWindowId)
      } else {
        watWindow
      }
    }
  }

  fun unbind(systemWindowId: Int) {
    _watWindows.value = _watWindows.value.mapNotNull {
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

  private fun addSavedWatWindows(savedWatWindows: List<WatWindow>) {
    _watWindows.value = savedWatWindows
      // Don't add windows twice
      .filterNot {
        _watWindows.value.any { watWindow -> watWindow.id == it.id }
      } + _watWindows.value
  }

  fun addSystemWindows(systemWindows: List<Window>) {
    _watWindows.value = _watWindows.value + systemWindows
      // Ignore windows that are already bound
      .filterNot {
        _watWindows.value.any { watWindow -> watWindow.systemWindowId == it.id }
      }
      .map { systemWindow ->
        WatWindow(
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
            WatTab(
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

  suspend fun saveWindow(watWindowId: String, name: String) {
    _watWindows.value = _watWindows.value.map {
      if (it.id == watWindowId) {
        it.copy(
          name = name,
          isSaved = true,
        )
      } else {
        it
      }
    }
    settingsRepository.saveWatWindows(
      _watWindows.value.filter { it.isSaved }.map { window ->
        window.copy(
          systemWindowId = null,
          focused = false,
          tabs = window.tabs.map { tab ->
            tab.copy(
              active = false,
            )
          },
        )
      },
    )
  }

  private fun getWindow(watWindowId: String): WatWindow? = _watWindows.value.firstOrNull { it.id == watWindowId }

  fun updateWatWindows(systemWindows: List<Window>) {
    _watWindows.value = _watWindows.value.map { watWindow ->
      val systemWindow = systemWindows.firstOrNull { it.id == watWindow.systemWindowId }
      if (systemWindow != null) {
        watWindow.copy(
          focused = systemWindow.focused,
          tabs = systemWindow.tabs?.map { systemTab ->
            WatTab(
              title = systemTab.title,
              url = systemTab.url,
              favIconUrl = systemTab.favIconUrl,
              active = systemTab.active,
            )
          } ?: emptyList(),
        )
      } else {
        watWindow
      }
    }
  }

  fun focusSystemWindow(systemWindowId: Int) {
    _watWindows.value = _watWindows.value.map { watWindow ->
      if (watWindow.systemWindowId == systemWindowId) {
        watWindow.copy(focused = true)
      } else {
        watWindow.copy(focused = false)
      }
    }
  }
}
