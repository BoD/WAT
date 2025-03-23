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

package chrome.windows

import kotlinx.js.JsPlainObject

@JsPlainObject
external interface QueryOptions {
  val populate: Boolean?
  val windowTypes: Array</*WindowType*/String>?
}

interface WindowType {
  companion object {
    const val normal = "normal"
    const val popup = "popup"
    const val panel = "panel"
    const val app = "app"
    const val devtools = "devtools"
  }
}

@JsPlainObject
external interface CreateData {
  val url: Array<String>?
  val type: /*CreateType*/ String?
  val focused: Boolean?
  val top: Int?
  val left: Int?
  val width: Int?
  val height: Int?
  val state: String?
}

interface CreateType {
  companion object {
    const val normal = "normal"
    const val popup = "popup"
    const val panel = "panel"
    const val detached_panel = "detached_panel"
  }
}

@JsPlainObject
external interface UpdateInfo {
  val focused: Boolean?
}
