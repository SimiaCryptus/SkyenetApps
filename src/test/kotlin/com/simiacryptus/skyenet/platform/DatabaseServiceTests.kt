package com.simiacryptus.skyenet.platform

import AuthenticationInterfaceTest
import DatabaseServices
import com.simiacryptus.skyenet.core.platform.test.StorageInterfaceTest
import com.simiacryptus.skyenet.core.platform.test.UsageTest
import com.simiacryptus.skyenet.core.platform.test.UserSettingsTest
import java.nio.file.Files

class DatabaseServiceTests : DatabaseServices() {

  inner class AuthenticationManagerTest : AuthenticationInterfaceTest(authenticationManager) {}

  inner class DataStorageTest : StorageInterfaceTest(dataStorageFactory.invoke(
    Files.createTempDirectory("dataStorageTest").toFile())) {}

  inner class UsageManagerTest : UsageTest(usageManager) {}

  inner class UserSettingsManagerTest : UserSettingsTest(userSettingsManager) {}
}