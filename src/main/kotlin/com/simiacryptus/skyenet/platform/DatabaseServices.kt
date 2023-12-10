package com.simiacryptus.skyenet.platform
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.models.ChatModels
import com.simiacryptus.jopenai.models.OpenAIModel
import com.simiacryptus.skyenet.core.platform.*
import com.simiacryptus.skyenet.core.platform.file.DataStorage
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.*

open class DatabaseServices(
  private val jdbcUrl: String,
  private val username: String = "sa",
  private val password: String = ""
) {
  private fun getConnection(): Connection {
    return DriverManager.getConnection(jdbcUrl, username, password)
  }

  fun register() {
    ApplicationServices.authenticationManager = authenticationManager
    ApplicationServices.dataStorageFactory = dataStorageFactory
    ApplicationServices.usageManager = usageManager
    ApplicationServices.userSettingsManager = userSettingsManager
    initializeSchema()
  }

  fun initializeSchema() {
    getConnection().use { connection ->
      connection.autoCommit = false
      try {
        connection.createStatement().use { statement ->
          statement.execute(
            """
                CREATE TABLE IF NOT EXISTS users (
                    id VARCHAR(255) PRIMARY KEY,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    name VARCHAR(255),
                    picture VARCHAR(255)
                );
            """.trimIndent()
          )
        }

        connection.createStatement().use { statement ->
          statement.execute(
            """
                CREATE TABLE IF NOT EXISTS authentication (
                    token_id VARCHAR(255) PRIMARY KEY,
                    user_id VARCHAR(255),
                    FOREIGN KEY (user_id) REFERENCES users(id)
                );
            """.trimIndent()
          )
        }

        connection.createStatement().use { statement ->
          statement.execute(
            """
                CREATE TABLE IF NOT EXISTS sessions (
                    session_id VARCHAR(255) PRIMARY KEY,
                    user_id VARCHAR(255),
                    FOREIGN KEY (user_id) REFERENCES users(id)
                );
            """.trimIndent()
          )
        }

        connection.createStatement().use { statement ->
          statement.execute(
            """
                CREATE TABLE IF NOT EXISTS user_settings (
                    user_id VARCHAR(255) PRIMARY KEY,
                    api_key VARCHAR(255),
                    FOREIGN KEY (user_id) REFERENCES users(id)
                );
            """.trimIndent()
          )
        }

        connection.createStatement().use { statement ->
          statement.execute(
            """
              CREATE TABLE IF NOT EXISTS usage (
                  usage_id SERIAL PRIMARY KEY,
                  session_id VARCHAR(255),
                  user_id VARCHAR(255),
                  model VARCHAR(255),
                  input_tokens INT,
                  output_tokens INT,
                  cost DOUBLE,
                  FOREIGN KEY (session_id) REFERENCES sessions(session_id),
                  FOREIGN KEY (user_id) REFERENCES users(id)
              );
          """.trimIndent()
          )
          // session_id, model index
          statement.execute(
            """
                CREATE INDEX IF NOT EXISTS usage_session_model ON usage(session_id, model);
            """.trimIndent()
          )
          // user_id, model index
          statement.execute(
            """
                CREATE INDEX IF NOT EXISTS usage_user_model ON usage(user_id, model);
            """.trimIndent()
          )
        }

        connection.createStatement().use { statement ->
          statement.execute(
            """
                CREATE TABLE IF NOT EXISTS messages (
                    message_id SERIAL PRIMARY KEY,
                    session_id VARCHAR(255),
                    message_text TEXT,
                    FOREIGN KEY (session_id) REFERENCES sessions(session_id)
                );
            """.trimIndent()
          )
          // session_id index
          statement.execute(
            """
                CREATE INDEX IF NOT EXISTS messages_session ON messages(session_id);
            """.trimIndent()
          )
        }
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
          upsertUser(connection, user!!)
          upsertSession(connection, session, user)
          connection.prepareStatement(
            """
                INSERT INTO usage (session_id, user_id, model, input_tokens, output_tokens, cost)
                VALUES (?, ?, ?, ?, ?, ?);
            """.trimIndent()
          ).apply {
            setString(1, session.toString())
            setString(2, user?.id)
            setString(3, model.modelName)
            setInt(4, tokens.prompt_tokens)
            setInt(5, tokens.completion_tokens)
            setDouble(6, tokens.cost)
            execute()
          }
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
        connection.prepareStatement(
          """
              SELECT model, SUM(input_tokens) AS input_tokens, SUM(output_tokens) AS output_tokens, SUM(cost) AS cost
              FROM usage
              WHERE user_id = ?
              GROUP BY model;
          """.trimIndent()
        ).apply {
          setString(1, user.id)
          executeQuery().use { resultSet ->
            val map = HashMap<OpenAIModel, ApiModel.Usage>()
            while (resultSet.next()) {
              val modelName = resultSet.getString("model")
              val inputTokens = resultSet.getInt("input_tokens")
              val outputTokens = resultSet.getInt("output_tokens")
              val cost = resultSet.getDouble("cost")
              val model = ChatModels.entries.find { it.modelName == modelName }
              if (null != model) map[model] = ApiModel.Usage(
                prompt_tokens = inputTokens,
                completion_tokens = outputTokens,
                cost = cost
              )
            }
            return map
          }
        }
      }
    }

    override fun getSessionUsageSummary(session: Session): Map<OpenAIModel, ApiModel.Usage> {
      getConnection().use { connection ->
        connection.autoCommit = false
        connection.prepareStatement(
          """
          SELECT model, SUM(input_tokens) AS input_tokens, SUM(output_tokens) AS output_tokens, SUM(cost) AS cost
          FROM usage
          WHERE session_id = ?
          GROUP BY model;
          """.trimIndent()
        ).apply {
          setString(1, session.toString())
          executeQuery().use { resultSet ->
            val map = HashMap<OpenAIModel, ApiModel.Usage>()
            while (resultSet.next()) {
              val modelName = resultSet.getString("model")
              val inputTokens = resultSet.getInt("input_tokens")
              val outputTokens = resultSet.getInt("output_tokens")
              val cost = resultSet.getDouble("cost")
              val model = ChatModels.entries.find { it.modelName == modelName }
              if (null != model) map[model] = ApiModel.Usage(
                prompt_tokens = inputTokens,
                completion_tokens = outputTokens,
                cost = cost
              )
            }
            return map
          }
        }
      }
    }
  }
  val userSettingsManager: UserSettingsInterface = object : UserSettingsInterface {
    override fun getUserSettings(user: User): UserSettingsInterface.UserSettings {
      getConnection().use { connection ->
        connection.autoCommit = false
        try {
          connection.prepareStatement("SELECT * FROM user_settings WHERE user_id = ?").apply {
            setString(1, user.id)
            executeQuery().use { resultSet ->
              if (resultSet.next()) {
                return UserSettingsInterface.UserSettings(
                  apiKey = resultSet.getString("api_key")
                )
              }
            }
          }
        } catch (e: Exception) {
          throw e
        }
      }
      return UserSettingsInterface.UserSettings()
    }

    override fun updateUserSettings(user: User, settings: UserSettingsInterface.UserSettings) {
      getConnection().use { connection ->
        connection.autoCommit = false
        try {
          upsertUser(connection, user)
          connection.prepareStatement("""
            INSERT INTO user_settings (user_id, api_key)
            VALUES (?, ?)
            ON CONFLICT (user_id) DO UPDATE
            SET api_key = EXCLUDED.api_key
            """.trimIndent()).apply {
            setString(1, user.id)
            setString(2, settings.apiKey)
            execute()
          }
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
        connection.prepareStatement("SELECT * FROM authentication WHERE token_id = ?").apply {
          setString(1, accessToken)
          executeQuery().use { resultSet ->
            if (resultSet.next()) {
              val userId = resultSet.getString("user_id")
              connection.prepareStatement("SELECT * FROM users WHERE id = ?").apply {
                setString(1, userId)
                executeQuery().use { resultSet ->
                  if (resultSet.next()) {
                    return User(
                      id = resultSet.getString("id"),
                      email = resultSet.getString("email"),
                      name = resultSet.getString("name"),
                      picture = resultSet.getString("picture")
                    )
                  }
                }
              }
            }
          }
        }
      }
      return null
    }

    override fun putUser(accessToken: String, user: User): User {
      getConnection().use { connection ->
        connection.autoCommit = false
        try {
          upsertUser(connection, user)
          upsertToken(connection, accessToken, user)
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
          connection.prepareStatement("DELETE FROM authentication WHERE token_id = ?").apply {
            setString(1, accessToken)
            execute()
          }
          connection.commit()
        } catch (e: Exception) {
          connection.rollback()
          throw e
        }
      }
    }
  }

  private fun upsertToken(
    connection: Connection,
    accessToken: String,
    user: User
  ) {
    connection.prepareStatement("""
      INSERT INTO authentication (token_id, user_id)
      VALUES (?, ?)
      ON CONFLICT (token_id) DO UPDATE
      SET user_id = EXCLUDED.user_id
      """.trimIndent()).apply {
      setString(1, accessToken)
      setString(2, user.id)
      execute()
    }
  }

  fun upsertUser(connection: Connection, user: User) {
    connection.prepareStatement("""
        INSERT INTO users (id, email, name, picture)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE
        SET email = EXCLUDED.email,
            name = EXCLUDED.name,
            picture = EXCLUDED.picture
      """.trimIndent()).apply {
      setString(1, user.id)
      setString(2, user.email)
      setString(3, user.name)
      setString(4, user.picture)
      execute()
    }
  }


  fun upsertSession(connection: Connection, session: Session, user: User?) {
    connection.prepareStatement("""
      INSERT INTO sessions (session_id, user_id)
      VALUES (?, ?)
      ON CONFLICT (session_id) DO UPDATE
      SET user_id = EXCLUDED.user_id
      """.trimIndent()).apply {
      setString(1, session.toString())
      setString(2, user?.id)
      execute()
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
      val databaseServices = DatabaseServices("jdbc:h2:file:skyenet;DB_CLOSE_DELAY=-1")
      databaseServices.initializeSchema()
      println("Database schema initialized successfully.")
    }

  }
}

