/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2020-present Benoit 'BoD' Lubek (BoD@JRAF.org)
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

package org.jraf.wat.shared.logging

enum class LogLevel {
  DEBUG,
  INFO,
  WARN,
}

fun log(
  level: LogLevel,
  format: String,
  vararg params: Any?,
) {
  when (level) {
    LogLevel.DEBUG -> console.log(format, *params)
    LogLevel.INFO -> console.info(format, *params)
    LogLevel.WARN -> console.warn(format, *params)
  }
}

fun logd(file: String, member: String, format: String, vararg params: Any?) {
  log(
    level = LogLevel.DEBUG,
    format = "$file $member - $format",
    params = params,
  )
}

fun logd(file: String, member: String) {
  log(
    level = LogLevel.DEBUG,
    format = "$file $member",
  )
}

fun logd(format: String, vararg params: Any?) {
  log(
    level = LogLevel.DEBUG,
    format = format,
    params = params,
  )
}

fun logi(format: String, vararg params: Any?) {
  log(
    level = LogLevel.INFO,
    format = format,
    params = params,
  )
}

fun logw(format: String, vararg params: Any?) {
  log(
    level = LogLevel.WARN,
    format = format,
    params = params,
  )
}
