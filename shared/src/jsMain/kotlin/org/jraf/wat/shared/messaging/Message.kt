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

package org.jraf.wat.shared.messaging

import kotlinx.serialization.Serializable
import org.jraf.wat.shared.model.WatWindow

@Serializable
sealed class Message

@Serializable
data object RequestPublishWatWindows : Message()

@Serializable
class PublishWatWindows(val watWindows: List<WatWindow>) : Message()

@Serializable
class FocusOrCreateWatWindowMessage(val watWindow: WatWindow) : Message()

@Serializable
class SaveWatWindowMessage(val watWindowId: String, val windowName: String) : Message()

@Serializable
class UnsaveWatWindowMessage(val watWindowId: String) : Message()
