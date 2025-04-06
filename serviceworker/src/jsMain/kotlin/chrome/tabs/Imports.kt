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

@file:JsQualifier("chrome.tabs")

package chrome.tabs

import kotlin.js.Promise

external fun get(tabId: Int): Promise<Tab?>

external interface Tab {
  val id: Int
  val windowId: Int
  val active: Boolean
  val url: String
  val title: String
  val favIconUrl: String?
}

external val onActivated: OnActivated

external interface OnActivated {
  fun addListener(callback: (activeInfo: ActiveInfo) -> Unit)
}

external interface ActiveInfo {
  val tabId: Int
  val windowId: Int
}


external val onAttached: OnAttached

external interface OnAttached {
  fun addListener(callback: (tabId: Int, attachInfo: AttachInfo) -> Unit)
}

external interface AttachInfo {
  val newPosition: Int
  val newWindowId: Int
}


external val onCreated: OnCreated

external interface OnCreated {
  fun addListener(callback: (tab: Tab) -> Unit)
}


external val onDetached: OnDetached

external interface OnDetached {
  fun addListener(callback: (tabId: Int, detachInfo: DetachInfo) -> Unit)
}

external interface DetachInfo {
  val oldPosition: Int
  val oldWindowId: Int
}


external val onHighlighted: OnHighlighted

external interface OnHighlighted {
  fun addListener(callback: (highlightInfo: HighlightInfo) -> Unit)
}

external interface HighlightInfo {
  val tabIds: Array<Int>
  val windowId: Int
}


external val onMoved: OnMoved

external interface OnMoved {
  fun addListener(callback: (tabId: Int, moveInfo: MoveInfo) -> Unit)
}

external interface MoveInfo {
  val fromIndex: Int
  val toIndex: Int
  val windowId: Int
}


external val onRemoved: OnRemoved

external interface OnRemoved {
  fun addListener(callback: (tabId: Int, removeInfo: RemoveInfo) -> Unit)
}

external interface RemoveInfo {
  val isWindowClosing: Boolean
  val windowId: Int
}


external val onReplaced: OnReplaced

external interface OnReplaced {
  fun addListener(callback: (addedTabId: Int, removedTabId: Int) -> Unit)
}


external val onUpdated: OnUpdated

external interface OnUpdated {
  fun addListener(callback: (tabId: Int, changeInfo: ChangeInfo, tab: Tab) -> Unit)
}

external interface ChangeInfo {
  val url: String?
  val title: String?
  val favIconUrl: String?
  val discarded: Boolean?
  val status: String?
}

external fun update(tabId: Int, updateProperties: UpdateProperties): Promise<Tab>
