package com.jay.innertube.models.body

import com.jay.innertube.models.Context
import com.jay.innertube.models.Continuation
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?
)
