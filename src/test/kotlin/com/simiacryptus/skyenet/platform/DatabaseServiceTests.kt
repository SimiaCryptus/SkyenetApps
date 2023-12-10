package com.simiacryptus.skyenet.platform

import AuthenticationInterfaceTest
import com.simiacryptus.skyenet.core.platform.test.StorageInterfaceTest
import com.simiacryptus.skyenet.core.platform.test.UsageTest
import com.simiacryptus.skyenet.core.platform.test.UserSettingsTest
import org.junit.jupiter.api.Nested
import java.nio.file.Files
import java.util.*

class DatabaseServiceTests  {

  val databaseServices: DatabaseServices
    get() {
    val databaseServices = DatabaseServices("jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1")
    databaseServices.initializeSchema()
    return databaseServices
  }

  @Nested
  inner class AuthenticationManagerTest : AuthenticationInterfaceTest(databaseServices.authenticationManager) {}

  @Nested
  inner class DataStorageTest : StorageInterfaceTest(databaseServices.dataStorageFactory.invoke(
    Files.createTempDirectory("dataStorageTest").toFile())) {}

  @Nested
  inner class UsageManagerTest : UsageTest(databaseServices.usageManager) {}

  @Nested
  inner class UserSettingsManagerTest : UserSettingsTest(databaseServices.userSettingsManager) {}
}