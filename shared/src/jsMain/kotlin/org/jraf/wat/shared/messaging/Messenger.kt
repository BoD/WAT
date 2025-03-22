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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.json.encodeToDynamic
import org.jraf.wat.shared.model.WatWindow
import kotlin.js.Promise
import kotlin.uuid.ExperimentalUuidApi

class Messenger {
  private fun sendMessage(message: Message): Promise<Any?> {
    return chrome.runtime.sendMessage(Json.encodeToDynamic(message))
  }

//  suspend fun sendGetSavedWindowsMessage(): List<WatWindow> {
//    val response = sendMessage(GetSavedWindowsMessage).await()
//    val getSavedWindowsResponse = Json.decodeFromDynamic<PublishWatWindows>(response)
//    return getSavedWindowsResponse.watWindows
//  }

  fun sendRequestPublishWatWindows() {
    sendMessage(RequestPublishWatWindows)
  }

  fun sendPublishWatWindows(watWindows: List<WatWindow>) {
    val message = PublishWatWindows(watWindows)
    sendMessage(message)
  }

  fun sendFocusOrCreateWatWindowMessage(watWindow: WatWindow) {
    val message = FocusOrCreateWatWindowMessage(watWindow)
    sendMessage(message)
  }

  fun sendSaveWatWindowMessage(watWindowId: String, windowName: String) {
    val message = SaveWatWindowMessage(watWindowId, windowName)
    sendMessage(message)
  }

  fun sendUnsaveWatWindowMessage(watWindowId: String) {
    val message = UnsaveWatWindowMessage(watWindowId)
    sendMessage(message)
  }
}

fun Any.asMessage(): Message {
  return Json.decodeFromDynamic(this)
}
