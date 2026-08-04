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
import chrome.tabGroups.query as queryTabGroups
import chrome.tabGroups.update as updateTabGroup
import chrome.tabs.QueryInfo as TabQueryInfo
import chrome.tabs.query as queryTabs

/**
 * Maintains WAT's invariant that every tab in a managed browser window belongs
 * to one authoritative tab group. Group ids are browser-session identifiers,
 * so this class persists the current id and recovers it by window and name if
 * the browser changes it during session restore.
 */
class TabGroupController(
  private val onSystemTabGroupIdChanged: suspend (watWindowId: String, systemTabGroupId: Int) -> Unit,
) {
  private val groupColors = arrayOf(
    "grey",
    "blue",
    "red",
    "yellow",
    "green",
    "pink",
    "purple",
    "cyan",
    "orange",
  )

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
        updateTabGroup(
          it,
          updateProperties(
            title = watWindow.name,
            color = colorFor(watWindow.name),
          ),
        ).await()
      }

    rememberGroup(watWindow, groupId)

    val existingGroup = tabGroups.firstOrNull { it.id == groupId }
    if (existingGroup != null && existingGroup.color != colorFor(watWindow.name)) {
      updateTabGroup(groupId, updateProperties(color = colorFor(watWindow.name))).await()
    }

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

  suspend fun renameGroup(watWindow: WatWindow, newName: String) {
    val systemWindowId = watWindow.systemWindowId ?: return
    val tabGroups = queryTabGroups(TabGroupQueryInfo(windowId = systemWindowId)).await()
    val groupId = findGroup(tabGroups, watWindow)
    if (groupId == null) {
      ensureGroup(watWindow.copy(name = newName))
      return
    }

    rememberGroup(watWindow, groupId)
    updateTabGroup(
      groupId,
      updateProperties(
        title = newName,
        color = colorFor(newName),
      ),
    ).await()
  }

  fun isManagedGroup(watWindow: WatWindow, tabGroup: TabGroup): Boolean {
    return tabGroup.id == groupIdsByWatWindowId[watWindow.id] || tabGroup.id == watWindow.systemTabGroupId
  }

  suspend fun ensureColor(watWindow: WatWindow, tabGroup: TabGroup) {
    if (isManagedGroup(watWindow, tabGroup) && tabGroup.color != colorFor(watWindow.name)) {
      updateTabGroup(tabGroup.id, updateProperties(color = colorFor(watWindow.name))).await()
    }
  }

  private fun findGroup(tabGroups: Array<TabGroup>, watWindow: WatWindow): Int? {
    val groupIds = listOfNotNull(
      groupIdsByWatWindowId[watWindow.id],
      watWindow.systemTabGroupId,
    )
    groupIds.firstOrNull { groupId -> tabGroups.any { it.id == groupId } }?.let {
      return it
    }

    // Group ids change on browser session restore, so title matching is the
    // cross-browser fallback when the persisted id is no longer valid.
    return tabGroups.firstOrNull { it.title == watWindow.name }?.id
  }

  private suspend fun rememberGroup(watWindow: WatWindow, groupId: Int) {
    groupIdsByWatWindowId[watWindow.id] = groupId
    if (watWindow.systemTabGroupId != groupId) {
      onSystemTabGroupIdChanged(watWindow.id, groupId)
    }
  }

  private fun colorFor(name: String): String {
    // Use ushr 1 because hashCode can be negative
    return groupColors[(name.hashCode() ushr 1) % groupColors.size]
  }

  private fun updateProperties(title: String? = null, color: String? = null): dynamic {
    val result = js("{}")
    if (title != null) result.title = title
    if (color != null) result.color = color
    return result
  }
}
