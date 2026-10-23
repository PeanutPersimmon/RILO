package io.github.peanutpersimmon.rilo.data.local.room

import androidx.room.TypeConverter
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType

class RidoTypeConverters {
    @TypeConverter
    fun sessionKindToStorage(value: SessionKind): String = value.name

    @TypeConverter
    fun sessionKindFromStorage(value: String): SessionKind = SessionKind.valueOf(value)

    @TypeConverter
    fun sessionTemporalTypeToStorage(value: SessionTemporalType): String = value.name

    @TypeConverter
    fun sessionTemporalTypeFromStorage(value: String): SessionTemporalType = SessionTemporalType.valueOf(value)
}

