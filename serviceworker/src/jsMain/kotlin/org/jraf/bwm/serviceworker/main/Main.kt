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

@file:OptIn(DelicateCoroutinesApi::class, ExperimentalUuidApi::class)

package org.jraf.bwm.serviceworker.main

import chrome.action.onClicked
import chrome.runtime.getURL
import chrome.system.display.getInfo
import chrome.windows.CreateData
import chrome.windows.CreateType
import chrome.windows.QueryOptions
import chrome.windows.UpdateInfo
import chrome.windows.WindowType
import chrome.windows.create
import chrome.windows.getAll
import chrome.windows.update
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jraf.bwm.shared.messaging.GetSavedWindowsMessage
import org.jraf.bwm.shared.messaging.JsonGetSavedWindowsResponse
import org.jraf.bwm.shared.messaging.JsonSavedTab
import org.jraf.bwm.shared.messaging.JsonSavedWindow
import org.jraf.bwm.shared.messaging.OpenOrFocusSavedWindowMessage
import org.jraf.bwm.shared.messaging.asMessage
import org.jraf.bwm.shared.model.SavedWindow
import org.jraf.bwm.shared.model.SavedWindowRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val savedWindowRepository = SavedWindowRepository()

private var popupWindowId: Number? = null

// This is executed once when the extension starts
fun main() {
  console.log("Service Worker started")

  setupActionButton()

  chrome.windows.onRemoved.addListener { windowId ->
    console.log("onRemoved: %o", windowId)
    savedWindowRepository.unbindSavedWindow(windowId)
  }



//  chrome.tabs.onActivated.addListener { activeInfo ->
//    console.log("onActivated: %o", activeInfo)
//    GlobalScope.launch {
//      val tab = chrome.tabs.get(activeInfo.tabId).await()
//      console.log("tab: %o", tab)
//    }
//  }

//  chrome.windows.onFocusChanged.addListener { windowId ->
//    console.log("onFocusChanged: %o", windowId)
//    GlobalScope.launch {
//      val windows = chrome.windows.getAll(QueryOptions(populate = true, windowTypes = arrayOf(WindowType.normal))).await()
//      console.log("windows: %o", windows)
//    }
//  }

  registerMessageListener()
}

private fun setupActionButton() {
  // "action" is the extension's icon in the toolbar
  onClicked.addListener {
    GlobalScope.launch {
      // If the popup window is already open, focus it, otherwise create it
      if (getAll(QueryOptions(windowTypes = arrayOf(WindowType.popup))).await().any { it.id == popupWindowId }) {
        update(popupWindowId!!, UpdateInfo(focused = true))
      } else {
        val height = getInfo().await().firstOrNull { it.isPrimary }?.workArea?.height ?: 800
        popupWindowId = create(
          CreateData(
            url = arrayOf(getURL("popup.html")),
            type = CreateType.popup,
            focused = true,
            top = 0,
            left = 0,
            width = 320,
            height = height,
          ),
        ).await().id
      }
    }
  }
}

private fun registerMessageListener() {
  chrome.runtime.onMessage.addListener { msg, _, sendResponse ->
    when (val message = msg.asMessage()) {
      GetSavedWindowsMessage -> {
        console.log("Received GetSavedWindowsMessage")
        GlobalScope.launch {
          val savedWindows = savedWindowRepository.loadSavedWindows()
          val response = JsonGetSavedWindowsResponse(
            savedWindows.map { window ->
              JsonSavedWindow(
                id = window.id.toHexString(),
                name = window.name,
                tabs = window.tabs.map { tab ->
                  JsonSavedTab(
                    title = tab.title,
                    url = tab.url,
                    favIconUrl = tab.favIconUrl,
                  )
                }.toTypedArray(),
              )
            }.toTypedArray(),
          )
          console.log("Responding %o", response)
          sendResponse(response)
        }
      }

      is OpenOrFocusSavedWindowMessage -> {
        console.log("Received OpenOrFocusSavedWindowMessage")
        val savedWindow = savedWindowRepository.getSavedWindowById(Uuid.parseHex(message.savedWindowId))
        console.log("savedWindow: %o", savedWindow)
        if (savedWindow != null) {
          openOrFocusSavedWindow(savedWindow)
        }
      }

      else -> {
        // Ignore
      }
    }
    return@addListener true
  }
}

private fun openOrFocusSavedWindow(savedWindow: SavedWindow) {
  val windowId = savedWindowRepository.getWindowIdBySavedWindow(savedWindow)
  if (windowId != null) {
    GlobalScope.launch {
      chrome.windows.update(windowId, UpdateInfo(focused = true)).await()
    }
  } else {
    GlobalScope.launch {
      val openedWindow = chrome.windows.create(CreateData(url = savedWindow.tabs.map { it.url }.toTypedArray())).await()
      savedWindowRepository.bindSavedWindow(openedWindow.id!!, savedWindow)
    }
  }
}

