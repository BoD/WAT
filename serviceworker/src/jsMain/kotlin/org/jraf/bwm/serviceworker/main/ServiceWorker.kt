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

package org.jraf.bwm.serviceworker.main

import chrome.windows.CreateData
import chrome.windows.CreateType
import chrome.windows.QueryOptions
import chrome.windows.UpdateInfo
import chrome.windows.WindowType
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jraf.bwm.shared.messaging.FocusOrCreateBwmWindowMessage
import org.jraf.bwm.shared.messaging.Messenger
import org.jraf.bwm.shared.messaging.RequestPublishBwmWindows
import org.jraf.bwm.shared.messaging.SaveBwmWindowMessage
import org.jraf.bwm.shared.messaging.asMessage
import org.jraf.bwm.shared.model.BwmWindow
import org.jraf.bwm.shared.model.BwmWindowRepository

class ServiceWorker {
  private val bwmWindowRepository = BwmWindowRepository()

  private val messenger = Messenger()

  private var popupWindowId: Int? = null

  fun start() {
    initWindowRepository()
    observeWindows()
    observeTabs()
    registerMessageListener()

    GlobalScope.launch {
      bwmWindowRepository.bwmWindows.collect {
        messenger.sendPublishBwmWindows(it)
      }
    }

    setupActionButton()
  }

  private fun initWindowRepository() {
    GlobalScope.launch {
      val windows = chrome.windows.getAll(QueryOptions(populate = true, windowTypes = arrayOf(WindowType.normal))).await()
      bwmWindowRepository.addSystemWindows(windows.toList())
    }
  }

  private fun updateWindowRepository() {
    GlobalScope.launch {
      val windows = chrome.windows.getAll(QueryOptions(populate = true, windowTypes = arrayOf(WindowType.normal))).await()
      bwmWindowRepository.updateBwmWindows(windows.toList())
    }
  }

  private fun observeWindows() {
    chrome.windows.onCreated.addListener(
      callback = { window ->
        // Only consider normal windows
        if (window.type != WindowType.normal) return@addListener

        if (bindNextCreatedSystemWindowToBwmWindow != null) {
          bwmWindowRepository.bind(bwmWindowId = bindNextCreatedSystemWindowToBwmWindow!!, systemWindowId = window.id!!)
          bindNextCreatedSystemWindowToBwmWindow = null
        } else {
          bwmWindowRepository.addSystemWindow(window)
        }
      },
    )
    chrome.windows.onRemoved.addListener { systemWindowId ->
      bwmWindowRepository.unbind(systemWindowId)
    }
    chrome.windows.onFocusChanged.addListener { systemWindowId ->
      bwmWindowRepository.focusSystemWindow(systemWindowId)
    }
  }

  private fun observeTabs() {
    chrome.tabs.onCreated.addListener { tab ->
      updateWindowRepository()
    }
    chrome.tabs.onUpdated.addListener { tabId, changeInfo, tab ->
      updateWindowRepository()
    }
    chrome.tabs.onRemoved.addListener { tabId, removeInfo ->
      updateWindowRepository()
    }
    chrome.tabs.onMoved.addListener { tabId, moveInfo ->
      updateWindowRepository()
    }
    chrome.tabs.onAttached.addListener { tabId, attachInfo ->
      updateWindowRepository()
    }
    chrome.tabs.onDetached.addListener { tabId, detachInfo ->
      updateWindowRepository()
    }
    chrome.tabs.onReplaced.addListener { addedTabId, removedTabId ->
      updateWindowRepository()
    }
    chrome.tabs.onActivated.addListener { activeInfo ->
      updateWindowRepository()
    }
  }

  private fun registerMessageListener() {
    chrome.runtime.onMessage.addListener { msg, _, sendResponse ->
      when (val message = msg.asMessage()) {
//        GetSavedWindowsMessage -> {
//          GlobalScope.launch {
//            val response = PublishBwmWindows(bwmWindowRepository.loadSavedWindows())
//            sendResponse(Json.encodeToDynamic(response))
//          }
//        }

        RequestPublishBwmWindows -> {
          messenger.sendPublishBwmWindows(bwmWindowRepository.bwmWindows.value)
        }

        is FocusOrCreateBwmWindowMessage -> {
          val bwmWindow = message.bwmWindow
          focusOrCreateBwmWindow(bwmWindow)

        }

        is SaveBwmWindowMessage -> {
          bwmWindowRepository.saveWindow(bwmWindowId = message.bwmWindow.id, name = message.windowName)
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

  private var bindNextCreatedSystemWindowToBwmWindow: String? = null

  private fun focusOrCreateBwmWindow(bwmWindow: BwmWindow) {
    if (bwmWindow.systemWindowId != null) {
      GlobalScope.launch {
        chrome.windows.update(bwmWindow.systemWindowId!!, UpdateInfo(focused = true)).await()
      }
    } else {
      bindNextCreatedSystemWindowToBwmWindow = bwmWindow.id
      chrome.windows.create(CreateData(url = bwmWindow.tabs.map { it.url }.toTypedArray()))
    }
  }

  private fun setupActionButton() {
    // "action" is the extension's icon in the toolbar
    chrome.action.onClicked.addListener {
      GlobalScope.launch {
        // If the popup window is already open, focus it, otherwise create it
        if (chrome.windows.getAll(QueryOptions(windowTypes = arrayOf(WindowType.popup))).await().any { it.id == popupWindowId }) {
          chrome.windows.update(popupWindowId!!, UpdateInfo(focused = true))
        } else {
          // TODO
//          val height = chrome.system.display.getInfo().await().firstOrNull { it.isPrimary }?.workArea?.height ?: 800
          val height = 800
          popupWindowId = chrome.windows.create(
            CreateData(
              url = arrayOf(chrome.runtime.getURL("popup.html")),
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
