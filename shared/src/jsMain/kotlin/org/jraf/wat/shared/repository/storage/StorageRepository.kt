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

package org.jraf.wat.shared.repository.storage

import chrome.windows.QueryOptions
import chrome.windows.WindowType
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.json.encodeToDynamic
import org.jraf.wat.shared.repository.wat.WatTab
import org.jraf.wat.shared.repository.wat.WatWindow

class StorageRepository {
  suspend fun loadWatWindowsFromStorageMinusSystemWindows(): List<WatWindow> {
    val watWindowsFromStorage = loadWatWindowsFromStorage() ?: return emptyList()
    // Remove any saved system window ids that don't currently exist
    val systemWindowIds = chrome.windows.getAll(QueryOptions(populate = false, windowTypes = arrayOf(WindowType.normal))).await()
      .mapNotNull { it.id }
    return watWindowsFromStorage.map {
      if (!systemWindowIds.contains(it.systemWindowId)) {
        it.copy(systemWindowId = null)
      } else {
        it
      }
    }
  }

  private suspend fun loadWatWindowsFromStorage(): List<WatWindow>? {
    val items = chrome.storage.local.get("StorageRoot").await()
    val obj = items.StorageRoot
    return if (obj == undefined) {
      null
    } else {
      val storageRoot: StorageRoot = toKotlin(obj)
      storageRoot.windows.map { storageWindow ->
        storageWindow.toWatWindow()
      }
    }
  }

  suspend fun saveWatWindows(watWindows: List<WatWindow>) {
    val obj = js("{}")
    obj.StorageRoot = StorageRoot(windows = watWindows.map { it.toStorageWindow() }).toDynamic()
    chrome.storage.local.set(obj).await()
  }
}

private fun StorageWindow.toWatWindow(): WatWindow {
  return WatWindow(
    id = id,
    systemWindowId = systemWindowId,
    name = name,
    top = top,
    left = left,
    width = width,
    height = height,
    isSaved = true,
    focused = false,
    tabs = tabs.map { it.toWatTab() },
    treeExpanded = treeExpanded,
  )
}

private fun StorageTab.toWatTab(): WatTab {
  return WatTab(
    systemTabId = null,
    title = title,
    url = url,
    favIconUrl = favIconUrl,
    active = false,
  )
}

private fun WatWindow.toStorageWindow(): StorageWindow {
  return StorageWindow(
    id = id,
    systemWindowId = systemWindowId!!,
    name = name,
    top = top,
    left = left,
    width = width,
    height = height,
    tabs = tabs.map { it.toStorageTab() },
    treeExpanded = treeExpanded,
  )
}

private fun WatTab.toStorageTab(): StorageTab {
  return StorageTab(
    title = title,
    url = url,
    favIconUrl = favIconUrl,
  )
}


private inline fun <reified T> T.toDynamic(): dynamic = Json.encodeToDynamic(this)

private inline fun <reified T> toKotlin(o: dynamic): T = Json.decodeFromDynamic<T>(o)
