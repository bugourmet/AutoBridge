package io.github.autobridge.car

import android.content.Context
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Reports whether the phone is projecting to Android Auto. */
class CarStateMonitor(context: Context) {
    private val connection = CarConnection(context.applicationContext)

    val projecting: Flow<Boolean> = connection.type.asFlow()
        .map { it == CarConnection.CONNECTION_TYPE_PROJECTION }
        .distinctUntilChanged()
}
