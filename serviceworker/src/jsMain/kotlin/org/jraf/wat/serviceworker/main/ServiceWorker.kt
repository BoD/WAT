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

package org.jraf.wat.serviceworker.main

import chrome.action.onClicked
import chrome.runtime.getURL
import chrome.runtime.onMessage
import chrome.tabs.onActivated
import chrome.tabs.onAttached
import chrome.tabs.onDetached
import chrome.tabs.onMoved
import chrome.tabs.onReplaced
import chrome.tabs.onUpdated
import chrome.windows.CreateData
import chrome.windows.CreateType
import chrome.windows.QueryOptions
import chrome.windows.UpdateInfo
import chrome.windows.WindowType
import chrome.windows.create
import chrome.windows.getAll
import chrome.windows.onCreated
import chrome.windows.onFocusChanged
import chrome.windows.onRemoved
import chrome.windows.update
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jraf.wat.shared.messaging.FocusOrCreateWatWindowMessage
import org.jraf.wat.shared.messaging.Messenger
import org.jraf.wat.shared.messaging.RequestPublishWatWindows
import org.jraf.wat.shared.messaging.SaveWatWindowMessage
import org.jraf.wat.shared.messaging.UnsaveWatWindowMessage
import org.jraf.wat.shared.messaging.asMessage
import org.jraf.wat.shared.model.WatRepository
import org.jraf.wat.shared.model.WatWindow

class ServiceWorker {
  private val watRepository = WatRepository()

  private val messenger = Messenger()

  private var popupWindowId: Int? = null

  fun start() {
    initWindowRepository()
    observeWindows()
    observeTabs()
    registerMessageListener()

    GlobalScope.launch {
      watRepository.watWindows.collect {
        messenger.sendPublishWatWindows(it)
      }
    }

    setupActionButton()
  }

  private fun initWindowRepository() {
    GlobalScope.launch {
      val windows = getAll(QueryOptions(populate = true, windowTypes = arrayOf(WindowType.normal))).await()
      watRepository.addSystemWindows(windows.toList())
    }
  }

  private fun updateWindowRepository() {
    GlobalScope.launch {
      val windows = getAll(QueryOptions(populate = true, windowTypes = arrayOf(WindowType.normal))).await()
      watRepository.updateWatWindows(windows.toList())
    }
  }

  private fun observeWindows() {
    onCreated.addListener(
      callback = { window ->
        // Only consider normal windows
        if (window.type != WindowType.normal) return@addListener

        if (watWindowIdToBind != null) {
          watRepository.bind(watWindowId = watWindowIdToBind!!, systemWindowId = window.id!!)
          watWindowIdToBind = null
        } else {
          watRepository.addSystemWindow(window)
        }
      },
    )
    onRemoved.addListener { systemWindowId ->
      watRepository.unbind(systemWindowId)
    }
    onFocusChanged.addListener { systemWindowId ->
      watRepository.focusSystemWindow(systemWindowId)
    }
  }

  private fun observeTabs() {
    chrome.tabs.onCreated.addListener { tab ->
      updateWindowRepository()
    }
    onUpdated.addListener { tabId, changeInfo, tab ->
      updateWindowRepository()
    }
    chrome.tabs.onRemoved.addListener { tabId, removeInfo ->
      updateWindowRepository()
    }
    onMoved.addListener { tabId, moveInfo ->
      updateWindowRepository()
    }
    onAttached.addListener { tabId, attachInfo ->
      updateWindowRepository()
    }
    onDetached.addListener { tabId, detachInfo ->
      updateWindowRepository()
    }
    onReplaced.addListener { addedTabId, removedTabId ->
      updateWindowRepository()
    }
    onActivated.addListener { activeInfo ->
      updateWindowRepository()
    }
  }

  private fun registerMessageListener() {
    onMessage.addListener { msg, _, sendResponse ->
      when (val message = msg.asMessage()) {
//        GetSavedWindowsMessage -> {
//          GlobalScope.launch {
//            val response = PublishWatWindows(watWindowRepository.loadSavedWindows())
//            sendResponse(Json.encodeToDynamic(response))
//          }
//        }

        RequestPublishWatWindows -> {
          messenger.sendPublishWatWindows(watRepository.watWindows.value)
        }

        is FocusOrCreateWatWindowMessage -> {
          val watWindow = message.watWindow
          focusOrCreateWatWindow(watWindow)

        }

        is SaveWatWindowMessage -> {
          GlobalScope.launch {
            watRepository.saveWindow(watWindowId = message.watWindowId, name = message.windowName)
          }
        }

        is UnsaveWatWindowMessage -> {
          GlobalScope.launch {
            watRepository.unsaveWindow(watWindowId = message.watWindowId)
          }
        }

        else -> {
          // Ignore
        }
      }
      // Return true to have the right to respond asynchronously
      // https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/runtime/onMessage#sending_an_asynchronous_response_using_sendresponse
      return@addListener true
    }
  }

  private var watWindowIdToBind: String? = null

  private fun focusOrCreateWatWindow(watWindow: WatWindow) {
    if (watWindow.systemWindowId != null) {
      GlobalScope.launch {
        update(watWindow.systemWindowId!!, UpdateInfo(focused = true)).await()
      }
    } else {
      watWindowIdToBind = watWindow.id
      create(CreateData(url = watWindow.tabs.map { it.url }.toTypedArray()))
    }
  }

  private fun setupActionButton() {
    // "action" is the extension's icon in the toolbar
    onClicked.addListener {
      GlobalScope.launch {
        // If the popup window is already open, focus it, otherwise create it
        if (getAll(QueryOptions(windowTypes = arrayOf(WindowType.popup))).await().any { it.id == popupWindowId }) {
          update(popupWindowId!!, UpdateInfo(focused = true))
        } else {
          // TODO
//          val height = chrome.system.display.getInfo().await().firstOrNull { it.isPrimary }?.workArea?.height ?: 800
          val height = 800
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
}
