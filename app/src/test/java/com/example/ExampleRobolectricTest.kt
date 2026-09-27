package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ColorDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Nuancier & Mélange", appName)
  }

  @Test
  fun testDatabase() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = ColorDatabase.getDatabase(context)
    assertNotNull(database)
    val dao = database.colorDao()
    assertNotNull(dao)
  }
}
