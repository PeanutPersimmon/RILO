package io.github.peanutpersimmon.rilo.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import io.github.peanutpersimmon.rilo.backup.BackupOperationCoordinator

private val Context.planViewOrderStore by preferencesDataStore("rilo_plan_view_order")

/**
 * Ordered plan-view names, comma-joined. This store stays enum-agnostic: the presentation
 * layer maps names to PlanViewMode and pads any missing or unknown entries.
 */
class PlanViewOrderPreferences(context: Context) {
    private val store = context.applicationContext.planViewOrderStore
    private val key = stringPreferencesKey("order")
    private val values = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val orderNames: Flow<List<String>> = values.map { preferences ->
        preferences[key]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    }

    suspend fun setOrderNames(names: List<String>) = BackupOperationCoordinator.mutation {
        store.edit { it[key] = names.joinToString(",") }
    }

    /** First launch: persist the default sequence so the settings list and cold start agree. */
    suspend fun ensureDefaultOrder(defaultNames: List<String>) = BackupOperationCoordinator.mutation {
        store.edit { preferences ->
            val current = preferences[key]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            if (current.isEmpty()) preferences[key] = defaultNames.joinToString(",")
        }
    }
}
