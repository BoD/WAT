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

@file:JsQualifier("chrome.windows")

package chrome.windows

import chrome.tabs.Tab
import kotlin.js.Promise

external val onFocusChanged: OnFocusChanged

external interface OnFocusChanged {
  fun addListener(callback: (windowId: Int) -> Unit)
}

external val onCreated: OnCreated

external interface OnCreated {
  fun addListener(callback: (window: Window) -> Unit)
}


external val onRemoved: OnRemoved

external interface OnRemoved {
  fun addListener(callback: (windowId: Int) -> Unit)
}

external fun getAll(queryOptions: QueryOptions?): Promise<Array<Window>>

external interface Window {
  val id: Int?
  val focused: Boolean
  val incognito : Boolean
  val tabs: Array<Tab>?
  val type: /*WindowType*/ String?
}

external fun create(createData: CreateData): Promise<Window>

external fun update(windowId: Int, updateInfo: UpdateInfo): Promise<Window>
