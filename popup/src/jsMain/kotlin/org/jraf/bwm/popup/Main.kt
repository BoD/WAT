/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2024-present Benoit 'BoD' Lubek (BoD@JRAF.org)
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

@file:OptIn(DelicateCoroutinesApi::class, ExperimentalUuidApi::class)

package org.jraf.bwm.popup

import kotlinx.browser.document
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jraf.bwm.shared.messaging.Messenger
import kotlin.uuid.ExperimentalUuidApi

private val messenger = Messenger()

// This is executed every time popup.html is opened.
fun main() {
  console.log("Popup opened")
  document.addEventListener(
    type = "DOMContentLoaded",
    callback = {
      document.querySelector("button")?.addEventListener(
        type = "click",
        callback = { helloWorld() },
      )
    },
  )
}

fun helloWorld() {
  console.log("Hello World!")
  GlobalScope.launch {
    val savedWindows = messenger.sendGetSavedWindowsMessage()
    messenger.sendOpenOrFocusSavedWindowMessage(savedWindows.first().id)
  }
}
