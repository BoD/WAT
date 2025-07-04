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

package org.jraf.wat.serviceworker.repository.wat

import chrome.windows.Window
import com.jakewharton.cite.__FILE__
import com.jakewharton.cite.__MEMBER__
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jraf.wat.serviceworker.repository.storage.StorageRepository
import org.jraf.wat.shared.logging.logd
import org.jraf.wat.shared.model.WatTab
import org.jraf.wat.shared.model.WatWindow
import org.jraf.wat.shared.util.decodeSuspended
import kotlin.js.Date
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class WatRepository {
  private val storageRepository = StorageRepository()

  private var isInitialized = false

  private val _watWindows: MutableStateFlow<List<WatWindow>> = MutableStateFlow(emptyList())
  val watWindows: StateFlow<List<WatWindow>> = _watWindows

  suspend fun init() {
    if (isInitialized) return
    _watWindows.value = storageRepository.loadWatWindowsFromStorageMinusSystemWindows()
    isInitialized = true
  }

  fun bind(watWindowId: String, systemWindow: Window) {
    val watWindow = getWindow(watWindowId)
    check(watWindow != null) { "WatWindow $watWindowId not found" }
    check(!watWindow.isBound) { "WatWindow $watWindowId is already bound to a system window" }
    _watWindows.value = _watWindows.value.map {
      if (it.id == watWindowId) {
        it.copy(
          systemWindowId = systemWindow.id,
        )
      } else {
        it
      }
    }
  }

  fun unbind(systemWindowId: Int) {
    _watWindows.value = _watWindows.value.mapNotNull {
      if (it.systemWindowId == systemWindowId) {
        if (it.isSaved) {
          it.copy(
            systemWindowId = null,
            focused = false,
            tabs = it.tabs.map { tab ->
              tab.copy(
                systemTabId = null,
                active = false,
              )
            },
          )
        } else {
          null
        }
      } else {
        it
      }
    }
  }

  fun addSystemWindows(systemWindows: List<Window>) {
    _watWindows.value += systemWindows
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
          top = systemWindow.top,
          left = systemWindow.left,
          width = systemWindow.width,
          height = systemWindow.height,
          tabs = systemWindow.tabs?.map { systemTab ->
            WatTab(
              systemTabId = systemTab.id,
              title = systemTab.title,
              url = systemTab.url,
              favIconUrl = systemTab.favIconUrl,
              active = systemTab.active,
            )
          } ?: emptyList(),
          treeExpanded = true,
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
    saveWindows()
  }

  /**
   * Unsave a window.
   * If the window isn't bound, it is also removed from the list.
   */
  suspend fun unsaveWindow(watWindowId: String) {
    _watWindows.value = _watWindows.value.mapNotNull {
      if (it.id == watWindowId) {
        if (it.isBound) {
          it.copy(
            isSaved = false,
          )
        } else {
          null
        }
      } else {
        it
      }
    }
    saveWindows()
  }

  private suspend fun saveWindows() {
    storageRepository.saveWatWindows(
      _watWindows.value.filter { it.isSaved },
    )
  }

  private fun getWindow(watWindowId: String): WatWindow? = _watWindows.value.firstOrNull { it.id == watWindowId }

  suspend fun updateWatWindows(systemWindows: List<Window>) {
    if (!isInitialized) {
      logd(__FILE__, __MEMBER__, "Not initialized, ignoring")
      return
    }
    // When activating a popup (including THE popup of this extension), we'll get a list of windows which are all unfocused.
    // When that happens, just keep the current focus state.
    val atLeastOneSystemWindowFocused = systemWindows.any { it.focused }
    _watWindows.value = _watWindows.value.map { watWindow ->
      val systemWindow = systemWindows.firstOrNull { it.id == watWindow.systemWindowId }
      if (systemWindow != null) {
        watWindow.copy(
          focused = if (atLeastOneSystemWindowFocused) systemWindow.focused else watWindow.focused,
          top = systemWindow.top,
          left = systemWindow.left,
          width = systemWindow.width,
          height = systemWindow.height,
          tabs = systemWindow.tabs?.map { systemTab ->
            WatTab(
              systemTabId = systemTab.id,
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
    saveWindows()
  }

  fun getWatWindow(watWindowId: String): WatWindow? = watWindows.value.firstOrNull { it.id == watWindowId }

  fun getWatWindowBySystemId(systemWindowId: Int): WatWindow? = watWindows.value.firstOrNull { it.systemWindowId == systemWindowId }

  suspend fun setTreeExpanded(watWindowId: String, treeExpanded: Boolean) {
    _watWindows.value = _watWindows.value.map {
      if (it.id == watWindowId) {
        it.copy(treeExpanded = treeExpanded)
      } else {
        it
      }
    }
    saveWindows()
  }

  suspend fun reorderWatWindows(toReorderWatWindowId: String, relativeToWatWindowId: String, isBefore: Boolean) {
    val toReorderWatWindow = getWindow(toReorderWatWindowId) ?: return
    val relativeToWatWindow = getWindow(relativeToWatWindowId) ?: return
    if (toReorderWatWindow == relativeToWatWindow) return
    _watWindows.value = _watWindows.value.toMutableList().apply {
      remove(toReorderWatWindow)
      val index = indexOf(relativeToWatWindow)
      if (isBefore) {
        add(index, toReorderWatWindow)
      } else {
        add(index + 1, toReorderWatWindow)
      }
    }
    saveWindows()
  }

  @Serializable
  private class ExportWindow(
    val name: String,
    val tabs: List<String>,
  )

  fun getExport(): String {
    return Json.encodeToString(
      _watWindows.value
        .filter { it.isSaved }
        .map { window ->
          ExportWindow(
            name = window.name,
            tabs = window.tabs.mapNotNull { tab ->
              // Exclude special URLs
              if (tab.url.startsWith("chrome://")) {
                null
              } else {
                tab.url.decodeSuspended()
              }
            },
          )
        },
    )
  }

  suspend fun import(importJsonString: String): Boolean {
    val exportWindows = try {
      Json.decodeFromString<List<ExportWindow>>(importJsonString)
    } catch (e: Exception) {
      console.warn("Importing failed: %o", e)
      return false
    }
    _watWindows.value += exportWindows.map { exportWindow ->
      WatWindow(
        id = Uuid.random().toHexString(),
        name = exportWindow.name,
        isSaved = true,
        systemWindowId = null,
        focused = false,
        top = 0,
        left = 320,
        width = 800,
        height = 600,
        tabs = exportWindow.tabs.map { tabUrl ->
          WatTab(
            systemTabId = null,
            title = "",
            url = tabUrl,
            favIconUrl = null,
            active = false,
          )
        },
        treeExpanded = false,
      )
    }
    saveWindows()
    return true
  }
}
