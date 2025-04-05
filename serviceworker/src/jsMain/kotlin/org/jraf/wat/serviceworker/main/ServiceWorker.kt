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
import chrome.tabs.UpdateProperties
import chrome.tabs.onActivated
import chrome.tabs.onAttached
import chrome.tabs.onDetached
import chrome.tabs.onMoved
import chrome.tabs.onReplaced
import chrome.tabs.onUpdated
import chrome.tabs.update
import chrome.windows.CreateData
import chrome.windows.CreateType
import chrome.windows.QueryOptions
import chrome.windows.UpdateInfo
import chrome.windows.WindowType
import chrome.windows.create
import chrome.windows.get
import chrome.windows.getAll
import chrome.windows.onBoundsChanged
import chrome.windows.onCreated
import chrome.windows.onFocusChanged
import chrome.windows.onRemoved
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jraf.wat.serviceworker.repository.wat.WatRepository
import org.jraf.wat.shared.messaging.FocusOrCreateWatWindowMessage
import org.jraf.wat.shared.messaging.Messenger
import org.jraf.wat.shared.messaging.ReorderWatWindowsMessage
import org.jraf.wat.shared.messaging.RequestPublishWatWindows
import org.jraf.wat.shared.messaging.SaveWatWindowMessage
import org.jraf.wat.shared.messaging.SetTreeExpandedMessage
import org.jraf.wat.shared.messaging.UnsaveWatWindowMessage
import org.jraf.wat.shared.messaging.asMessage

class ServiceWorker {
  private val watRepository = WatRepository()

  private val messenger = Messenger()

  private val popupWindowUrl by lazy {
    getURL("popup.html")
  }

  fun start() {
    GlobalScope.launch {
      initWindowRepository()
      observeWindows()
      observeTabs()
      registerMessageListener()

      launch {
        watRepository.watWindows.collect {
          messenger.sendPublishWatWindows(it)
        }
      }

      setupActionButton()
    }
  }

  private suspend fun initWindowRepository() {
    watRepository.init()
    val windows = getAll(QueryOptions(populate = true, windowTypes = arrayOf(WindowType.normal))).await()
    watRepository.addSystemWindows(windows.toList())
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
          watRepository.bind(watWindowId = watWindowIdToBind!!, systemWindow = window)
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
      GlobalScope.launch {
        // Ignore focusing the popup window
        val focusedWindow =
          runCatching { get(systemWindowId, QueryOptions(populate = true)).await() }.getOrNull() ?: return@launch
        if (focusedWindow.tabs?.firstOrNull()?.url == popupWindowUrl) return@launch
        updateWindowRepository()
      }
    }
    // Doesn't exist in Firefox
    @Suppress("SENSELESS_COMPARISON")
    if (onBoundsChanged !== undefined) {
      onBoundsChanged.addListener {
        updateWindowRepository()
      }
    }
  }

  private fun observeTabs() {
    chrome.tabs.onCreated.addListener { tab ->
      updateWindowRepository()
    }
    onUpdated.addListener { tabId, changeInfo, tab ->
      updateWindowRepository()
      if (tabIndexToActivate != null) {
        val systemTabIdToActivate = watRepository.getWatWindowBySystemId(tab.windowId)?.tabs?.getOrNull(tabIndexToActivate!!)?.systemTabId
        if (systemTabIdToActivate != null) {
          GlobalScope.launch {
            update(systemTabIdToActivate, UpdateProperties(active = true)).await()
            tabIndexToActivate = null
          }
        }
      }
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
      GlobalScope.launch {
        // Ignore focusing the popup window
        val tab = chrome.tabs.get(activeInfo.tabId).await() ?: return@launch
        if (tab.url == popupWindowUrl) return@launch
        updateWindowRepository()
      }
    }
  }

  private fun registerMessageListener() {
    onMessage.addListener { msg, _, _ ->
      when (val message = msg.asMessage()) {
        RequestPublishWatWindows -> {
          messenger.sendPublishWatWindows(watRepository.watWindows.value)
        }

        is FocusOrCreateWatWindowMessage -> {
          focusOrCreateWatWindow(message.watWindowId, message.tabIndex)
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

        is SetTreeExpandedMessage -> {
          GlobalScope.launch {
            watRepository.setTreeExpanded(watWindowId = message.watWindowId, treeExpanded = message.treeExpanded)
          }
        }

        is ReorderWatWindowsMessage -> {
          GlobalScope.launch {
            watRepository.reorderWatWindows(
              toReorderWatWindowId = message.toReorderWatWindowId,
              relativeToWatWindowId = message.relativeToWatWindowId,
              isBefore = message.isBefore,
            )
          }
        }

        else -> {
          // Ignore
        }
      }
      // Return true to have the right to respond asynchronously
      // https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/runtime/onMessage#sending_an_asynchronous_response_using_sendresponse
      // We don't need to respond, so we return false
      return@addListener false
    }
  }

  private var watWindowIdToBind: String? = null
  private var tabIndexToActivate: Int? = null

  private fun focusOrCreateWatWindow(watWindowId: String, tabIndex: Int?) {
    val watWindow = watRepository.getWatWindow(watWindowId) ?: return
    if (watWindow.isBound) {
      GlobalScope.launch {
        chrome.windows.update(watWindow.systemWindowId!!, UpdateInfo(focused = true)).await()
        if (tabIndex != null) {
          update(watWindow.tabs[tabIndex].systemTabId!!, UpdateProperties(active = true)).await()
        }
      }
    } else {
      watWindowIdToBind = watWindowId
      tabIndexToActivate = tabIndex
      create(
        CreateData(
          url = watWindow.tabs.map { it.url }.toTypedArray(),
          top = watWindow.top,
          left = watWindow.left,
          width = watWindow.width,
          height = watWindow.height,
        ),
      )
    }
  }

  private fun setupActionButton() {
    // "action" is the extension's icon in the toolbar
    onClicked.addListener {
      GlobalScope.launch {
        // If the popup window is already open, focus it, otherwise create it
        val popupWindow = getAll(QueryOptions(populate = true, windowTypes = arrayOf(WindowType.popup))).await()
          .firstOrNull { it.tabs?.any { it.url == popupWindowUrl } == true }
        if (popupWindow != null) {
          chrome.windows.update(popupWindow.id!!, UpdateInfo(focused = true))
        } else {
          val height = if (jsTypeOf(window) == "undefined") {
            // In Chrome window is not defined in the service worker.
            // It's ok because the popup is resizing itself (which doesn't work in Firefox ¯\_(ツ)_/¯)
            // Also... "not defined" is not the same as undefined... ¯\_(ツ)_/¯
            800
          } else {
            window.screen.availHeight
          }
          create(
            CreateData(
              url = arrayOf(popupWindowUrl),
              type = CreateType.popup,
              focused = true,
              top = 0,
              left = 0,
              width = 320,
              height = height,
            ),
          ).await()
        }
      }
    }
  }
}
