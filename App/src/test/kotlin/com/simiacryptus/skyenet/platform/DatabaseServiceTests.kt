package com.simiacryptus.skyenet.platform

import AuthenticationInterfaceTest
import com.simiacryptus.skyenet.core.platform.test.StorageInterfaceTest
import com.simiacryptus.skyenet.core.platform.test.UsageTest
import com.simiacryptus.skyenet.core.platform.test.UserSettingsTest
import org.junit.jupiter.api.Nested
import java.nio.file.Files

class DatabaseServiceTests {

    val databaseServices: DatabaseServices
        get() {
            val databaseServices = DatabaseServices(
                "jdbc:postgresql://localhost:5432/postgres",
                "postgres",
            ) { "password" }
            databaseServices.teardownSchema()
            databaseServices.initializeSchema()
            return databaseServices
        }

    @Nested
    inner class AuthenticationManagerTest : AuthenticationInterfaceTest(databaseServices.authenticationManager)

    @Nested
    inner class DataStorageTest : StorageInterfaceTest(
        databaseServices.dataStorageFactory.invoke(
            Files.createTempDirectory("dataStorageTest").toFile()
        )
    )

    @Nested
    inner class UsageManagerTest : UsageTest(databaseServices.usageManager)

    @Nested
    inner class UserSettingsManagerTest : UserSettingsTest(databaseServices.userSettingsManager)
}