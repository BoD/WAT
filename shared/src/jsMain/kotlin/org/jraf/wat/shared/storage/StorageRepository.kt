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

package org.jraf.wat.shared.storage

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.json.encodeToDynamic
import org.jraf.wat.shared.model.WatTab
import org.jraf.wat.shared.model.WatWindow

class StorageRepository {
  private val _watWindows = MutableStateFlow<List<WatWindow>>(emptyList())
  val watWindows: Flow<List<WatWindow>> = _watWindows

  init {
    GlobalScope.launch {
      val watWindowsFromStorage = loadWatWindowsFromStorage()
      if (watWindowsFromStorage != null) {
        _watWindows.value = watWindowsFromStorage
      }
    }

    // TODO Listen to changes in the storage
    // via https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/storage/StorageArea/onChanged
  }

  private suspend fun loadWatWindowsFromStorage(): List<WatWindow>? {
    val items = chrome.storage.sync.get("windowIds").await()
    val obj = items.windowIds
    return if (obj == undefined) {
      null
    } else {
      val storageWindowIds: StorageWindowIds = toKotlin(obj)
      storageWindowIds.watWindowIds.mapNotNull { watWindowId ->
        val items = chrome.storage.sync.get(watWindowId).await()
        val obj = items[watWindowId]
        if (obj == undefined) {
          null
        } else {
          toKotlin<StorageWindow>(obj).toWatWindow()
        }
      }
    }
  }

  suspend fun saveWatWindows(watWindows: List<WatWindow>) {
    val obj = js("{}")
    obj.windowIds = StorageWindowIds(watWindows.map { it.id }).toDynamic()
    for (watWindow in watWindows) {
      obj[watWindow.id] = watWindow.toStorageWindow().toDynamic()
    }
    chrome.storage.sync.clear().await()
    chrome.storage.sync.set(obj).await()
    _watWindows.value = watWindows
  }
}

private fun StorageWindow.toWatWindow(): WatWindow {
  return WatWindow(
    id = id,
    name = name,
    top = top,
    left = left,
    width = width,
    height = height,
    isSaved = true,
    systemWindowId = null,
    focused = false,
    tabs = tabs.map { it.toWatTab() },
  )
}

private fun StorageTab.toWatTab(): WatTab {
  return WatTab(
    title = title,
    url = url,
    favIconUrl = favIconUrl,
    active = false,
  )
}

private fun WatWindow.toStorageWindow(): StorageWindow {
  return StorageWindow(
    id = id,
    name = name,
    top = top,
    left = left,
    width = width,
    height = height,
    tabs = tabs.map { it.toStorageTab() },
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
