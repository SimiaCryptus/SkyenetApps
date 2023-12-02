import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.models.OpenAIModel
import com.simiacryptus.skyenet.core.platform.*
import com.simiacryptus.skyenet.core.platform.file.DataStorage
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.*

open class DatabaseServices(
  private val jdbcUrl: String = "jdbc:h2:mem:skyenet;DB_CLOSE_DELAY=-1",
  private val username: String = "sa",
  private val password: String = ""
) {
  private fun getConnection(): Connection {
    return DriverManager.getConnection(jdbcUrl, username, password)
  }

  private fun executeSql(connection: Connection, sql: String) {
    connection.createStatement().use { statement ->
      statement.execute(sql)
    }
  }

  fun initializeSchema() {
    getConnection().use { connection ->
      connection.autoCommit = false
      try {
        executeSql(
          connection, """
                    CREATE TABLE users (
                        id VARCHAR(255) PRIMARY KEY,
                        email VARCHAR(255) NOT NULL UNIQUE,
                        name VARCHAR(255),
                        picture VARCHAR(255)
                    );
                """.trimIndent()
        )

        executeSql(
          connection, """
                    CREATE TABLE sessions (
                        session_id VARCHAR(255) PRIMARY KEY,
                        user_id VARCHAR(255),
                        FOREIGN KEY (user_id) REFERENCES users(id)
                    );
                """.trimIndent()
        )

        executeSql(
          connection, """
                    CREATE TABLE user_settings (
                        user_id VARCHAR(255) PRIMARY KEY,
                        api_key VARCHAR(255),
                        FOREIGN KEY (user_id) REFERENCES users(id)
                    );
                """.trimIndent()
        )

        executeSql(
          connection, """
                    CREATE TABLE usage (
                        usage_id INT AUTO_INCREMENT PRIMARY KEY,
                        session_id VARCHAR(255),
                        user_id VARCHAR(255),
                        model VARCHAR(255),
                        input_tokens INT,
                        output_tokens INT,
                        FOREIGN KEY (session_id) REFERENCES sessions(session_id),
                        FOREIGN KEY (user_id) REFERENCES users(id)
                    );
                """.trimIndent()
        )

        executeSql(
          connection, """
                    CREATE TABLE messages (
                        message_id INT AUTO_INCREMENT PRIMARY KEY,
                        session_id VARCHAR(255),
                        message_text TEXT,
                        FOREIGN KEY (session_id) REFERENCES sessions(session_id)
                    );
                """.trimIndent()
        )

        executeSql(
          connection, """
                    CREATE TABLE authorizations (
                        authorization_id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id VARCHAR(255),
                        application_class VARCHAR(255),
                        operation_type ENUM('Read', 'Write', 'Share', 'Execute', 'Delete', 'Admin', 'GlobalKey'),
                        FOREIGN KEY (user_id) REFERENCES users(id)
                    );
                """.trimIndent()
        )

        connection.commit()
      } catch (e: Exception) {
        connection.rollback()
        throw e
      }
    }
  }


  val usageManager: UsageInterface = object : UsageInterface {
    override fun incrementUsage(session: Session, user: User?, model: OpenAIModel, tokens: ApiModel.Usage) {
      getConnection().use { connection ->
        connection.autoCommit = false
        try {
          executeSql(
            connection, """
                    INSERT INTO usage (session_id, user_id, model, input_tokens, output_tokens)
                    VALUES (?, ?, ?, ?, ?);
                """.trimIndent()
          )
          connection.commit()
        } catch (e: Exception) {
          connection.rollback()
          throw e
        }
      }
    }

    override fun getUserUsageSummary(user: User): Map<OpenAIModel, ApiModel.Usage> {
      getConnection().use { connection ->
        connection.autoCommit = false
        try {
          executeSql(
            connection, """
                    SELECT model, SUM(input_tokens) AS input_tokens, SUM(output_tokens) AS output_tokens
                    FROM usage
                    WHERE user_id = ?
                    GROUP BY model;
                """.trimIndent()
          )
          connection.commit()
        } catch (e: Exception) {
          connection.rollback()
          throw e
        }
      }
      return mapOf()
    }

    override fun getSessionUsageSummary(session: Session): Map<OpenAIModel, ApiModel.Usage> {
      getConnection().use { connection ->
        connection.autoCommit = false
        try {
          executeSql(
            connection, """
                    SELECT model, SUM(input_tokens) AS input_tokens, SUM(output_tokens) AS output_tokens
                    FROM usage
                    WHERE session_id = ?
                    GROUP BY model;
                """.trimIndent()
          )
          connection.commit()
        } catch (e: Exception) {
          connection.rollback()
          throw e
        }
      }
      return mapOf()
    }
  }
  val userSettingsManager: UserSettingsInterface = object : UserSettingsInterface {
    override fun getUserSettings(user: User): UserSettingsInterface.UserSettings {
      getConnection().use { connection ->
        connection.autoCommit = false
        try {
          executeSql(
            connection, """
                    SELECT api_key
                    FROM user_settings
                    WHERE user_id = ?;
                """.trimIndent()
          )
          connection.commit()
        } catch (e: Exception) {
          connection.rollback()
          throw e
        }
      }
      return UserSettingsInterface.UserSettings()
    }

    override fun updateUserSettings(user: User, settings: UserSettingsInterface.UserSettings) {
      getConnection().use { connection ->
        connection.autoCommit = false
        try {
          executeSql(
            connection, """
                    INSERT INTO user_settings (user_id, api_key)
                    VALUES (?, ?)
                    ON DUPLICATE KEY UPDATE api_key = ?;
                """.trimIndent()
          )
          connection.commit()
        } catch (e: Exception) {
          connection.rollback()
          throw e
        }
      }
    }
  }
  val authenticationManager: AuthenticationInterface = object : AuthenticationInterface {
    override fun getUser(accessToken: String?): User? {
      getConnection().use { connection ->
        connection.autoCommit = false
        try {
          executeSql(
            connection, """
                    SELECT id, email, name, picture
                    FROM users
                    WHERE id = ?;
                """.trimIndent()
          )
          connection.commit()
        } catch (e: Exception) {
          connection.rollback()
          throw e
        }
      }
      return null
    }

    override fun putUser(accessToken: String, user: User): User {
      getConnection().use { connection ->
        connection.autoCommit = false
        try {
          executeSql(
            connection, """
                    INSERT INTO users (id, email, name, picture)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE email = ?, name = ?, picture = ?;
                """.trimIndent()
          )
          connection.commit()
        } catch (e: Exception) {
          connection.rollback()
          throw e
        }
      }
      return user
    }

    override fun logout(accessToken: String, user: User) {
      getConnection().use { connection ->
        connection.autoCommit = false
        try {
          executeSql(
            connection, """
                    DELETE FROM users
                    WHERE id = ?;
                """.trimIndent()
          )
          connection.commit()
        } catch (e: Exception) {
          connection.rollback()
          throw e
        }
      }
    }
  }
  val dataStorageFactory: (File) -> StorageInterface = { storageRoot ->
    object : DataStorage(storageRoot) {
      override fun <T> getJson(user: User?, session: Session, filename: String, clazz: Class<T>): T? {
        return super.getJson(user, session, filename, clazz)
      }

      override fun getMessages(user: User?, session: Session): LinkedHashMap<String, String> {
        return super.getMessages(user, session)
      }

      override fun getSessionDir(user: User?, session: Session): File {
        return super.getSessionDir(user, session)
      }

      override fun getSessionName(user: User?, session: Session): String {
        return super.getSessionName(user, session)
      }

      override fun getSessionTime(user: User?, session: Session): Date? {
        return super.getSessionTime(user, session)
      }

      override fun listSessions(user: User?): List<Session> {
        return super.listSessions(user)
      }

      override fun listSessions(dir: File): List<String> {
        return super.listSessions(dir)
      }

      override fun <T : Any> setJson(user: User?, session: Session, filename: String, settings: T): T {
        return super.setJson(user, session, filename, settings)
      }

      override fun updateMessage(user: User?, session: Session, messageId: String, value: String) {
        return super.updateMessage(user, session, messageId, value)
      }

      override fun userRoot(user: User?): File {
        return super.userRoot(user)
      }

      override fun deleteSession(user: User?, session: Session) {
        return super.deleteSession(user, session)
      }
    }
  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val databaseServices = DatabaseServices()
      databaseServices.initializeSchema()
      println("Database schema initialized successfully.")
    }

  }
}

