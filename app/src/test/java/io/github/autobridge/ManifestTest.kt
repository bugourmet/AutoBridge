package io.github.autobridge

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import io.github.autobridge.listener.RelayListenerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManifestTest {
    private val app: AutoBridgeApp = ApplicationProvider.getApplicationContext()
    private val pm = app.packageManager

    @Test
    fun `app does not request INTERNET`() {
        val requested = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(Manifest.permission.INTERNET in requested)
    }

    @Test
    fun `declares Android Auto notification support`() {
        val meta = pm.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA).metaData
        assertNotEquals(0, meta.getInt("com.google.android.gms.car.application"))
    }

    @Test
    fun `listener service is protected by the bind permission`() {
        val info = pm.getServiceInfo(ComponentName(app, RelayListenerService::class.java), 0)
        assertEquals(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE, info.permission)
    }
}
