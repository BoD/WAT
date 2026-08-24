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

import browser.browser
import chrome.action.onClicked
import chrome.runtime.getURL
import chrome.runtime.onMessage
import chrome.sidePanel.PanelBehavior
import chrome.sidePanel.sidePanel
import chrome.tabs.UpdateProperties
import chrome.tabs.onActivated
import chrome.tabs.onAttached
import chrome.tabs.onDetached
import chrome.tabs.onMoved
import chrome.tabs.onReplaced
import chrome.tabs.onUpdated
import chrome.tabs.update
import chrome.windows.CreateData
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jraf.wat.serviceworker.repository.wat.WatRepository
import org.jraf.wat.serviceworker.tabgroups.TabGroupController
import org.jraf.wat.shared.messaging.CloseTabMessage
import org.jraf.wat.shared.messaging.FocusOrCreateWatWindowMessage
import org.jraf.wat.shared.messaging.GetExportMessage
import org.jraf.wat.shared.messaging.ImportMessage
import org.jraf.wat.shared.messaging.Messenger
import org.jraf.wat.shared.messaging.ReorderWatWindowsMessage
import org.jraf.wat.shared.messaging.RequestPublishWatWindowsMessage
import org.jraf.wat.shared.messaging.SaveWatWindowMessage
import org.jraf.wat.shared.messaging.SetTreeExpandedMessage
import org.jraf.wat.shared.messaging.UnsaveWatWindowMessage
import org.jraf.wat.shared.messaging.asMessage
import kotlin.time.Duration.Companion.milliseconds
import chrome.tabGroups.onCreated as onTabGroupCreated
import chrome.tabGroups.onRemoved as onTabGroupRemoved
import chrome.tabGroups.onUpdated as onTabGroupUpdated
import chrome.tabs.QueryInfo as TabQueryInfo
import chrome.tabs.query as queryTabs
import chrome.windows.remove as removeWindow

class ServiceWorker {
  private val watRepository = WatRepository()
  private val tabGroupController = TabGroupController(
    onSystemTabGroupIdChanged = watRepository::setSystemTabGroupId,
    shouldSkipEnsuring = { it in systemWindowIdsWithRemovedWatGroup },
  )
  private val systemWindowIdsBeingEnsured = mutableSetOf<Int>()
  private val systemWindowIdsWithRemovedWatGroup = mutableSetOf<Int>()

  private val messenger = Messenger()

  private val popupWindowUrl by lazy {
    getURL("popup.html")
  }

  fun start() {
    observeWindows()
    observeTabs()
    observeTabGroups()
    registerMessageListener()
    setupActionButton()

    GlobalScope.launch {
      initWindowRepository()
      watRepository.watWindows.collect {
        messenger.publishWatWindows(it)
      }
    }
  }

  private suspend fun initWindowRepository() {
    watRepository.init()
    val windows = getAll(QueryOptions(populate = true, windowTypes = arrayOf(WindowType.normal))).await()
    watRepository.addSystemWindows(windows.toList())
    watRepository.updateWatWindows(windows.toList())
    tabGroupController.ensureGroups(watRepository.watWindows.value)
  }

  private fun updateWindowRepository() {
    GlobalScope.launch {
      val windows = getAll(QueryOptions(populate = true, windowTypes = arrayOf(WindowType.normal))).await()
      watRepository.updateWatWindows(windows.toList())
    }
  }

  private fun ensureTabGroupForSystemWindow(systemWindowId: Int) {
    if (!systemWindowIdsBeingEnsured.add(systemWindowId)) return
    GlobalScope.launch {
      try {
        // A dragged tab can be attached after windows.onCreated, so the new
        // window is briefly empty when the first reconciliation runs.
        var watWindowWasFound = false
        repeat(10) { attempt ->
          val watWindow = watRepository.getWatWindowBySystemId(systemWindowId)
          if (watWindow == null && watWindowWasFound) {
            return@launch
          }
          watWindowWasFound = watWindow != null
          try {
            val ensured = watWindow?.let {
              tabGroupController.ensureGroup(it)
            } == true
            if (ensured) {
              return@launch
            }
          } catch (e: Throwable) {
            if (e.message?.contains("Tabs cannot be edited right now") != true) {
              throw e
            }
          }
          if (attempt < 9) delay(100.milliseconds)
        }
      } finally {
        systemWindowIdsBeingEnsured.remove(systemWindowId)
      }
    }
  }

  private fun observeWindows() {
    onCreated.addListener(
      callback = { window ->
        // Only consider normal windows
        if (window.type != WindowType.normal) return@addListener

        val watWindowIdToBind = watWindowIdToBind
        this.watWindowIdToBind = null
        GlobalScope.launch {
          if (watWindowIdToBind != null) {
            watRepository.bind(watWindowId = watWindowIdToBind, systemWindow = window)
          } else {
            watRepository.addSystemWindow(window)
          }
          ensureTabGroupForSystemWindow(window.id!!)
        }
      },
    )
    onRemoved.addListener { systemWindowId ->
      systemWindowIdsWithRemovedWatGroup.remove(systemWindowId)
      watRepository.getWatWindowBySystemId(systemWindowId)?.let {
        tabGroupController.forgetGroup(it.id)
      }
      GlobalScope.launch {
        watRepository.unbind(systemWindowId)
      }
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
      ensureTabGroupForSystemWindow(tab.windowId)
    }
    onUpdated.addListener { _, changeInfo, tab ->
      updateWindowRepository()
      if (changeInfo.groupId != null) {
        ensureTabGroupForSystemWindow(tab.windowId)
      }
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
    chrome.tabs.onRemoved.addListener { _, removeInfo ->
      val shouldCloseWindow = systemWindowIdsWithRemovedWatGroup.remove(removeInfo.windowId)
      GlobalScope.launch {
        if (shouldCloseWindow) {
          // Chrome replaces the last closed grouped tab with a blank tab.
          // Wait until the replacement has a definite URL.
          closeWindowIfStillBlankNewTab(removeInfo.windowId)
          return@launch
        }
        // This event seems to be sent before the tab is actually removed.
        // So wait a bit before querying the windows.
        delay(100.milliseconds)
        updateWindowRepository()
        ensureTabGroupForSystemWindow(removeInfo.windowId)
      }
    }
    onMoved.addListener { _, _ ->
      updateWindowRepository()
    }
    onAttached.addListener { _, attachInfo ->
      updateWindowRepository()
      ensureTabGroupForSystemWindow(attachInfo.newWindowId)
    }
    onDetached.addListener { _, detachInfo ->
      updateWindowRepository()
      ensureTabGroupForSystemWindow(detachInfo.oldWindowId)
    }
    onReplaced.addListener { _, _ ->
      updateWindowRepository()
    }
    onActivated.addListener { activeInfo ->
      updateWindowRepository()
    }
  }

  private fun observeTabGroups() {
    // Creating a native group or deleting WAT's group must both converge back
    // to WAT's one-group-per-window invariant.
    onTabGroupCreated.addListener { group ->
      updateWindowRepository()
      ensureTabGroupForSystemWindow(group.windowId)
    }
    onTabGroupRemoved.addListener { group ->
      val watWindow = watRepository.getWatWindowBySystemId(group.windowId)
      val removedWatGroup = watWindow != null && tabGroupController.isManagedGroup(watWindow, group.id)
      if (removedWatGroup) {
        systemWindowIdsWithRemovedWatGroup += group.windowId
      }
      updateWindowRepository()
      GlobalScope.launch {
        // Wait to learn whether the group disappeared because its final tab
        // was closed. If not, this was a manual group removal to restore.
        delay(100.milliseconds)
        if (removedWatGroup && !systemWindowIdsWithRemovedWatGroup.remove(group.windowId)) return@launch
        ensureTabGroupForSystemWindow(group.windowId)
      }
    }
    onTabGroupUpdated.addListener { group ->
      GlobalScope.launch {
        val watWindow = watRepository.getWatWindowBySystemId(group.windowId) ?: return@launch
        if (!tabGroupController.isManagedGroup(watWindow, group)) return@launch

        // Ignore the update emitted when WAT creates or maintains the
        // temporary-window asterisk. A different title is a user rename.
        if (!watWindow.isSaved && tabGroupController.hasExpectedTitle(watWindow, group)) {
          tabGroupController.ensurePresentation(watWindow, group)
          return@launch
        }

        val newName = if (watWindow.isSaved) {
          group.title.orEmpty()
        } else {
          group.title.orEmpty().removeSuffix(" *")
        }
        val updatedWatWindow = if (!watWindow.isSaved) {
          watRepository.saveWindow(watWindow.id, newName)
          watWindow.copy(name = newName, isSaved = true)
        } else if (newName != watWindow.name) {
          watRepository.renameWindow(watWindow.id, newName)
          watWindow.copy(name = newName)
        } else {
          watWindow
        }
        tabGroupController.ensurePresentation(updatedWatWindow, group)
      }
    }
  }

  private fun registerMessageListener() {
    onMessage.addListener { msg, _, sendResponse ->
      when (val message = msg.asMessage()) {
        RequestPublishWatWindowsMessage -> {
          messenger.publishWatWindows(watRepository.watWindows.value)
        }

        is FocusOrCreateWatWindowMessage -> {
          focusOrCreateWatWindow(message.watWindowId, message.tabIndex)
        }

        is SaveWatWindowMessage -> {
          GlobalScope.launch {
            watRepository.saveWindow(watWindowId = message.watWindowId, name = message.windowName)
            watRepository.getWatWindow(message.watWindowId)?.let { tabGroupController.renameGroup(it) }
          }
        }

        is UnsaveWatWindowMessage -> {
          GlobalScope.launch {
            watRepository.unsaveWindow(watWindowId = message.watWindowId)
            watRepository.getWatWindow(message.watWindowId)?.let { tabGroupController.renameGroup(it) }
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

        is GetExportMessage -> {
          sendResponse(watRepository.getExport())
        }

        is ImportMessage -> {
          GlobalScope.launch {
            val success = watRepository.import(message.importJsonString)
            sendResponse(success)
          }
        }

        is CloseTabMessage -> {
          closeTab(
            watWindowId = message.watWindowId,
            tabIndex = message.tabIndex,
          )
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
  private var tabIndexToActivate: Int? = null

  private fun focusOrCreateWatWindow(watWindowId: String, tabIndex: Int?) {
    val watWindow = watRepository.getWatWindow(watWindowId) ?: return
    if (watWindow.isBound) {
      GlobalScope.launch {
        chrome.windows.update(watWindow.systemWindowId!!, UpdateInfo(focused = true)).await()
        if (tabIndex != null) {
          watWindow.tabs.getOrNull(tabIndex)?.systemTabId?.let { systemTabId ->
            // The tab may have been closed after the popup rendered it.
            runCatching { update(systemTabId, UpdateProperties(active = true)).await() }
          }
        }
      }
    } else {
      watWindowIdToBind = watWindowId
      tabIndexToActivate = tabIndex

      // In Chrome window is not defined in the service worker.
      val isFirefox = jsTypeOf(window) != "undefined"

      create(
        CreateData(
          url = watWindow.tabs
            .map { it.url }
            .let {
              if (isFirefox) {
                // Firefox refuses to open URLs other than http(s)
                it.filter { it.startsWith("http://") || it.startsWith("https://") }
              } else {
                it
              }
            }
            .toTypedArray(),
          top = watWindow.top,
          left = watWindow.left,
          width = watWindow.width,
          height = watWindow.height,
        ),
      )
    }
  }

  private fun setupActionButton() {
    // In Chrome window is not defined in the service worker.
    // Note: "not defined" is not the same as `undefined`... ¯\_(ツ)_/¯
    val isChrome = jsTypeOf(window) == "undefined"
    if (isChrome) {
      // Chrome: we use the sidePanel API -> https://developer.chrome.com/docs/extensions/reference/api/sidePanel
      onClicked.addListener {
        GlobalScope.launch {
          sidePanel.setPanelBehavior(PanelBehavior(openPanelOnActionClick = true)).await()
        }
      }
    } else {
      // Firefox: we use the sidebarAction API -> https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/sidebarAction
      onClicked.addListener {
        browser.sidebarAction.toggle()
      }
    }
  }

  private fun closeTab(watWindowId: String, tabIndex: Int) {
    val systemTabId = watRepository.getWatWindow(watWindowId)?.tabs?.getOrNull(tabIndex)?.systemTabId ?: return
    GlobalScope.launch {
      chrome.tabs.remove(systemTabId).await()
    }
  }
}

private suspend fun closeWindowIfStillBlankNewTab(systemWindowId: Int) {
  repeat(10) {
    delay(100.milliseconds)
    val tabs = runCatching { queryTabs(TabQueryInfo(windowId = systemWindowId)).await() }.getOrNull() ?: return
    if (tabs.size != 1) return

    val url = tabs.single().url
    when {
      url.isBlank() -> Unit
      url == "chrome://newtab/" || url == "about:newtab" -> {
        runCatching { removeWindow(systemWindowId).await() }
        return
      }

      else -> return
    }
  }
}
