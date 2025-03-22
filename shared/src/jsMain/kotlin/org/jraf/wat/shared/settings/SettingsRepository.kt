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

package org.jraf.wat.shared.settings

import chrome.storage.sync
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromDynamic
import kotlinx.serialization.json.encodeToDynamic
import org.jraf.wat.shared.model.WatWindow

class SettingsRepository {
  private val _settings = MutableStateFlow<Settings>(Settings(savedWatWindows = emptyList()))
  val settings: Flow<Settings> = _settings.filterNotNull()

  init {
    GlobalScope.launch {
      val settingsFromStorage = loadSettingsFromStorage()
      if (settingsFromStorage != null) {
        _settings.value = settingsFromStorage
      }
    }

    // TODO Listen to changes in the settings
    // via https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/storage/StorageArea/onChanged
  }

  private suspend fun loadSettingsFromStorage(): Settings? {
    val items = sync.get("settings").await()
    val obj = items.settings
    return if (obj == undefined) {
      null
    } else {
      toSettings(obj)
    }
  }

  private suspend fun saveSettingsToStorage(settings: Settings) {
    val obj = js("{}")
    obj.settings = settings.toDynamic()
    sync.set(obj).await()
    _settings.value = settings
  }

  suspend fun saveWatWindows(watWindows: List<WatWindow>) {
    val settings = _settings.value.copy(savedWatWindows = watWindows)
    saveSettingsToStorage(settings)
  }
}

@OptIn(ExperimentalSerializationApi::class)
private fun Settings.toDynamic(): dynamic = Json.encodeToDynamic(this)

@OptIn(ExperimentalSerializationApi::class)
private fun toSettings(o: dynamic): Settings = Json.decodeFromDynamic<Settings>(o)
