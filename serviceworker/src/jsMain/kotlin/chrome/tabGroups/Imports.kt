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

@file:JsQualifier("chrome.tabGroups")

package chrome.tabGroups

import kotlin.js.Promise

external interface TabGroup {
  val id: Int
  val windowId: Int
  val title: String?
}

external fun query(queryInfo: QueryInfo): Promise<Array<TabGroup>>

external fun update(groupId: Int, updateProperties: UpdateProperties): Promise<TabGroup>

external val onCreated: OnCreated

external interface OnCreated {
  fun addListener(callback: (group: TabGroup) -> Unit)
}

external val onRemoved: OnRemoved

external interface OnRemoved {
  fun addListener(callback: (group: TabGroup) -> Unit)
}
