package org.matrix.vector.daemon

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VectorServiceRobolectricTest {

    private lateinit var shadowPm: ShadowPackageManager
    private lateinit var ccMock: MockedStatic<org.matrix.vector.daemon.data.ConfigCache>

    @Before
    fun setUp() {
        val pm = RuntimeEnvironment.getApplication().packageManager
        shadowPm = Shadows.shadowOf(pm)

        // Inject PackageManager into VectorService
        val field = VectorService::class.memberProperties
            .first { it.name == "packageManager" }
            .apply { isAccessible = true }
        (field as kotlin.reflect.KMutableProperty<*>).setter.call(VectorService, pm)

        // Mock ConfigCache.getModuleApkPath to always return null (not a module)
        ccMock = mockStatic(org.matrix.vector.daemon.data.ConfigCache::class.java)
        ccMock.`when`<Any> { org.matrix.vector.daemon.data.ConfigCache.getModuleApkPath(any()) }
            .thenReturn(null)
    }

    @After
    fun tearDown() {
        ccMock.close()
    }

    @Test
    fun `isXposedModule false for null packageName`() {
        assertFalse(VectorService.isXposedModule(null))
    }

    @Test
    fun `isXposedModule false for empty packageName`() {
        assertFalse(VectorService.isXposedModule(""))
    }

    @Test
    fun `isXposedModule false when package not installed`() {
        assertFalse(VectorService.isXposedModule("com.nonexistent.app"))
    }

    @Test
    fun `isXposedModule false when package has no xposed metadata`() {
        val pkgInfo = PackageInfo().apply {
            packageName = "com.normal.app"
            applicationInfo = shadowPm.addPackage("com.normal.app").apply {
                metaData = Bundle()
            }
        }
        shadowPm.addPackage(pkgInfo)

        assertFalse(VectorService.isXposedModule("com.normal.app"))
    }

    @Test
    fun `isXposedModule true when package has xposedminversion metadata`() {
        val pkgInfo = PackageInfo().apply {
            packageName = "com.xposed.module"
            applicationInfo = shadowPm.addPackage("com.xposed.module").apply {
                metaData = Bundle().apply {
                    putString("xposedminversion", "82")
                }
            }
        }
        shadowPm.addPackage(pkgInfo)

        assertTrue(VectorService.isXposedModule("com.xposed.module"))
    }

    @Test
    fun `handleFullyRemoved no-ops on null moduleName`() {
        // Should not throw
        VectorService.handleFullyRemoved(null, 0, false)
        VectorService.handleFullyRemoved(null, 0, true)
    }

    @Test
    fun `autoIncludeModule no-ops when auto-include list is empty`() {
        // ConfigCache.getAutoIncludeModules returns empty list (default mock)
        ccMock.`when`<Any> { org.matrix.vector.daemon.data.ConfigCache.getAutoIncludeModules() }
            .thenReturn(emptyList())

        // Should not throw
        VectorService.autoIncludeModule("com.some.app", 0)
    }
}
