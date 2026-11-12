package com.postpole.gharkhata.data

import androidx.room.Embedded
import androidx.room.Relation

/** A provider with its rate history, used for billing. */
data class ProviderWithRates(
    @Embedded val provider: ProviderEntity,
    @Relation(parentColumn = "id", entityColumn = "providerId")
    val rates: List<RateChangeEntity>,
)
