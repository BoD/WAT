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

@file:OptIn(ExperimentalUuidApi::class)

package org.jraf.wat.shared.messaging

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.json.encodeToDynamic
import org.jraf.wat.shared.model.WatWindow
import kotlin.uuid.ExperimentalUuidApi

class Messenger {
  private fun sendMessage(message: Message) {
    GlobalScope.launch {
      // Enclose in a runCatching because sending a message when there is no listener throws an error:
      // "Could not establish connection. Receiving end does not exist."
      // We don't care if nobody's listening.
      runCatching {
        chrome.runtime.sendMessage(Json.encodeToDynamic(message)).await()
      }
    }
  }

  private suspend fun <T> sendMessageAndWaitForResult(message: Message): T {
    return chrome.runtime.sendMessage(Json.encodeToDynamic(message)).await().unsafeCast<T>()
  }

  fun requestPublishWatWindows() {
    sendMessage(RequestPublishWatWindowsMessage)
  }

  fun publishWatWindows(watWindows: List<WatWindow>) {
    val message = PublishWatWindowsMessage(watWindows = watWindows)
    sendMessage(message)
  }

  fun focusOrCreateWatWindow(watWindowId: String, tabIndex: Int?) {
    val message = FocusOrCreateWatWindowMessage(watWindowId = watWindowId, tabIndex = tabIndex)
    sendMessage(message)
  }

  fun saveWatWindow(watWindowId: String, windowName: String) {
    val message = SaveWatWindowMessage(watWindowId = watWindowId, windowName = windowName)
    sendMessage(message)
  }

  fun unsaveWatWindow(watWindowId: String) {
    val message = UnsaveWatWindowMessage(watWindowId = watWindowId)
    sendMessage(message)
  }

  fun setTreeExpanded(watWindowId: String, treeExpanded: Boolean) {
    val message = SetTreeExpandedMessage(watWindowId = watWindowId, treeExpanded = treeExpanded)
    sendMessage(message)
  }

  fun reorderWatWindows(toReorderWatWindowId: String, relativeToWatWindowId: String, isBefore: Boolean) {
    val message = ReorderWatWindowsMessage(
      toReorderWatWindowId = toReorderWatWindowId,
      relativeToWatWindowId = relativeToWatWindowId,
      isBefore = isBefore,
    )
    sendMessage(message)
  }

  suspend fun getExport(): String {
    return sendMessageAndWaitForResult(GetExportMessage)
  }

  suspend fun import(importJsonString: String): Boolean {
    val message = ImportMessage(importJsonString = importJsonString)
    return sendMessageAndWaitForResult(message)
  }
}

fun Any.asMessage(): Message {
  return Json.decodeFromDynamic(this)
}
