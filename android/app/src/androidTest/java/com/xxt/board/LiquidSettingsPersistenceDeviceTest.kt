package com.xxt.board

import android.test.InstrumentationTestCase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.xxt.board.data.SettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File

/** Isolated DataStore file: never reads or edits the user settings file. */
@Suppress("DEPRECATION")
class LiquidSettingsPersistenceDeviceTest:InstrumentationTestCase() {
    fun testIndependentOpticsSurviveReopening()=runBlocking {
        val context=instrumentation.targetContext
        val file=File(context.cacheDir,"liquid-optics-test-"+System.nanoTime()+".preferences_pb")
        var job=SupervisorJob()
        try {
            var store=SettingsStore(context,PreferenceDataStoreFactory.create(scope=CoroutineScope(Dispatchers.IO+job),produceFile={file}))
            store.setLiquidParameters(72,1.35f)
            store.setLiquidTransparency(83)
            assertEquals(1.35f,store.flow.first().liquidRefraction,0f)
            store.setLiquidRefraction(0.45f)
            assertEquals(83,store.flow.first().liquidTransparency)
            store.setBlurRadius(34)
            job.cancelAndJoin()
            job=SupervisorJob()
            store=SettingsStore(context,PreferenceDataStoreFactory.create(scope=CoroutineScope(Dispatchers.IO+job),produceFile={file}))
            val reopened=store.flow.first()
            assertEquals(83,reopened.liquidTransparency)
            assertEquals(0.45f,reopened.liquidRefraction,0f)
            assertEquals(34,reopened.blurRadius)
            assertEquals("default",reopened.materialStyle)
        } finally {job.cancelAndJoin();file.delete()}
    }
}
