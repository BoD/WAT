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

@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUuidApi::class)

package org.jraf.bwm.shared.messaging

import kotlinx.coroutines.await
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.json.encodeToDynamic
import org.jraf.bwm.shared.model.SavedTab
import org.jraf.bwm.shared.model.SavedWindow
import kotlin.js.Promise
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class Messenger {
  private fun sendMessage(message: Message): Promise<Any?> {
    return chrome.runtime.sendMessage(Json.encodeToDynamic(message))
  }

  suspend fun sendGetSavedWindowsMessage(): List<SavedWindow> {
    val response = sendMessage(GetSavedWindowsMessage).await()
    console.log("Got response %o", response)
    return response.unsafeCast<JsonGetSavedWindowsResponse>().savedWindows.map { window ->
      SavedWindow(
        id = Uuid.parseHex(window.id),
        name = window.name,
        tabs = window.tabs.map { tab ->
          SavedTab(
            title = tab.title,
            url = tab.url,
            favIconUrl = tab.favIconUrl,
          )
        },
      )
    }
  }

  fun sendOpenOrFocusSavedWindowMessage(savedWindowId: Uuid) {
    val message = OpenOrFocusSavedWindowMessage(savedWindowId.toHexString())
    sendMessage(message)
  }
}

fun Any.asMessage(): Message {
  return Json.decodeFromDynamic(this)
}
