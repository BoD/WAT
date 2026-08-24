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

package org.jraf.wat.serviceworker.repository.storage

import chrome.windows.QueryOptions
import chrome.windows.WindowType
import chrome.windows.getAll
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.json.encodeToDynamic
import org.jraf.wat.shared.model.WatTab
import org.jraf.wat.shared.model.WatWindow

class StorageRepository {
  suspend fun loadWatWindowsFromStorageMinusSystemWindows(): List<WatWindow> {
    val savedWatWindows = loadSavedWatWindows() ?: emptyList()
    val unsavedWatWindows = loadUnsavedWatWindows() ?: emptyList()
    val systemWindowIds = getAll(QueryOptions(populate = false, windowTypes = arrayOf(WindowType.normal))).await()
      .mapNotNull { it.id }

    val savedWindowsWithCurrentSystemIds = savedWatWindows.map {
      if (!systemWindowIds.contains(it.systemWindowId)) {
        it.copy(
          systemWindowId = null,
          systemTabGroupId = null,
        )
      } else {
        it
      }
    }
    // Unsaved windows only exist for the current browser session, so discard
    // any leftover entry whose native window no longer exists.
    val unsavedWindowsWithCurrentSystemIds = unsavedWatWindows.filter { it.systemWindowId in systemWindowIds }
    return savedWindowsWithCurrentSystemIds + unsavedWindowsWithCurrentSystemIds
  }

  private suspend fun loadSavedWatWindows(): List<WatWindow>? {
    val items = chrome.storage.local.get("StorageRoot").await()
    val obj = items.StorageRoot
    return if (obj == undefined) {
      null
    } else {
      val storageRoot: StorageRoot = toKotlin(obj)
      storageRoot.windows.map { storageWindow ->
        storageWindow.toWatWindow(isSaved = true)
      }
    }
  }

  private suspend fun loadUnsavedWatWindows(): List<WatWindow>? {
    val items = chrome.storage.session.get("SessionStorageRoot").await()
    val obj = items.SessionStorageRoot
    return if (obj == undefined) {
      null
    } else {
      val storageRoot: StorageRoot = toKotlin(obj)
      storageRoot.windows.map { storageWindow ->
        storageWindow.toWatWindow(isSaved = false)
      }
    }
  }

  suspend fun saveWatWindows(savedWatWindows: List<WatWindow>, unsavedWatWindows: List<WatWindow>) {
    val obj = js("{}")
    obj.StorageRoot = StorageRoot(windows = savedWatWindows.map { it.toStorageWindow() }).toDynamic()
    chrome.storage.local.set(obj).await()

    val sessionObj = js("{}")
    sessionObj.SessionStorageRoot = StorageRoot(windows = unsavedWatWindows.map { it.toStorageWindow() }).toDynamic()
    chrome.storage.session.set(sessionObj).await()
  }
}

private fun StorageWindow.toWatWindow(isSaved: Boolean): WatWindow {
  return WatWindow(
    id = id,
    systemWindowId = systemWindowId,
    name = name,
    top = top,
    left = left,
    width = width,
    height = height,
    isSaved = isSaved,
    focused = false,
    tabs = tabs.map { it.toWatTab() },
    treeExpanded = treeExpanded,
    systemTabGroupId = systemTabGroupId,
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
    systemWindowId = systemWindowId,
    name = name,
    top = top,
    left = left,
    width = width,
    height = height,
    tabs = tabs.map { it.toStorageTab() },
    treeExpanded = treeExpanded,
    systemTabGroupId = systemTabGroupId,
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
