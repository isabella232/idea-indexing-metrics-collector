/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 * */

package co.elastic.idea.plugin.imc.model

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.indexing.diagnostic.TimeMillis

data class SimpleProjectIndexingEvent(
    val environment: Map<String, String>,
    val platform: PlatformInfo,
    val projectName: @NlsSafe String,
    val indexingReason: String?,
    val totalUpdatingTime: TimeMillis,
    val scanFilesDuration: TimeMillis,
    val indexDuration: TimeMillis,
    val updatingStart: Long,
    val updatingEnd: Long,
    val fullIndexing: Boolean,
    val interrupted: Boolean
)

data class PlatformInfo(val applicationName: String, val version: String, val buildNo: String)
