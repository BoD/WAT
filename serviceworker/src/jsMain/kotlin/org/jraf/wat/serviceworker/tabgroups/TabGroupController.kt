/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/_
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

package org.jraf.wat.serviceworker.tabgroups

import chrome.tabGroups.TabGroup
import chrome.tabs.GroupOptions
import chrome.tabs.group
import kotlinx.coroutines.await
import org.jraf.wat.shared.model.WatWindow
import chrome.tabGroups.QueryInfo as TabGroupQueryInfo
import chrome.tabGroups.UpdateProperties as TabGroupUpdateProperties
import chrome.tabGroups.query as queryTabGroups
import chrome.tabGroups.update as updateTabGroup
import chrome.tabs.QueryInfo as TabQueryInfo
import chrome.tabs.query as queryTabs

/**
 * Maintains WAT's invariant that every tab in a managed browser window belongs
 * to one authoritative tab group. Group ids are browser-session identifiers,
 * so this class only keeps them in memory and recovers them by window and name
 * whenever the service worker starts again.
 */
class TabGroupController {
  private val groupIdsByWatWindowId = mutableMapOf<String, Int>()
  private val watWindowIdsBeingEnsured = mutableSetOf<String>()
  private val queuedWatWindows = mutableMapOf<String, WatWindow>()

  suspend fun ensureGroups(watWindows: List<WatWindow>) {
    watWindows.forEach { ensureGroup(it) }
  }

  suspend fun ensureGroup(watWindow: WatWindow) {
    if (!watWindowIdsBeingEnsured.add(watWindow.id)) {
      queuedWatWindows[watWindow.id] = watWindow
      return
    }

    try {
      var windowToEnsure: WatWindow? = watWindow
      while (windowToEnsure != null) {
        ensureGroupOnce(windowToEnsure)
        windowToEnsure = queuedWatWindows.remove(watWindow.id)
      }
    } finally {
      watWindowIdsBeingEnsured.remove(watWindow.id)
    }
  }

  private suspend fun ensureGroupOnce(watWindow: WatWindow) {
    val systemWindowId = watWindow.systemWindowId ?: return
    val tabs = queryTabs(TabQueryInfo(windowId = systemWindowId)).await()
    if (tabs.isEmpty()) return

    val tabIds = tabs.map { it.id }.toTypedArray()
    val tabGroups = queryTabGroups(TabGroupQueryInfo(windowId = systemWindowId)).await()
    val existingGroupId = findGroup(tabGroups, watWindow)
    val groupId = existingGroupId
      ?: group(GroupOptions(tabIds = tabIds, groupId = null)).await().also {
        updateTabGroup(it, TabGroupUpdateProperties(title = watWindow.name)).await()
      }

    groupIdsByWatWindowId[watWindow.id] = groupId

    // Regrouping all tabs deliberately dissolves any independently-created
    // groups in this window, which is WAT's ownership policy.
    if (existingGroupId != null && tabs.any { it.groupId != groupId }) {
      group(
        GroupOptions(
          tabIds = tabIds,
          groupId = groupId,
        ),
      ).await()
    }
  }

  fun forgetGroup(watWindowId: String) {
    groupIdsByWatWindowId.remove(watWindowId)
    queuedWatWindows.remove(watWindowId)
  }

  private fun findGroup(tabGroups: Array<TabGroup>, watWindow: WatWindow): Int? {
    val rememberedGroupId = groupIdsByWatWindowId[watWindow.id]
    if (rememberedGroupId != null && tabGroups.any { it.id == rememberedGroupId }) {
      return rememberedGroupId
    }

    // A service worker restart loses the in-memory id. The matching title is
    // the best cross-browser recovery signal because ids are not persistent.
    return tabGroups.firstOrNull { it.title == watWindow.name }?.id
  }
}
