package com.simiacryptus.skyenet.platform
import com.simiacryptus.jopenai.ApiModel
import com.simiacryptus.jopenai.models.OpenAIModel
import com.simiacryptus.skyenet.core.platform.*
import com.simiacryptus.skyenet.core.platform.file.DataStorage
import com.simiacryptus.skyenet.core.util.getModel
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.text.SimpleDateFormat
import java.util.*

open class DatabaseServices(
  private val jdbcUrl: String,
  private val username: String = "sa",
  private val password: () -> String = { "" }
) {

  private val connectionPool = mutableSetOf<Connection>()

  private fun <T> useConnection(fn: (Connection) -> T): T {
    var connection: Connection? = null
    synchronized(connectionPool) {
      while (connectionPool.isNotEmpty()) {
        connection = connectionPool.first()
        connectionPool.remove(connection)
        if (connection?.isClosed == false) break
        else connection = null
      }
    }
    if(null == connection) {
      connection = DriverManager.getConnection(jdbcUrl, username, password())
    }
    try {
      val result = connection!!.use(fn)
      connectionPool.add(connection!!)
      return result
    } catch (e: Exception) {
      connection?.close()
      throw e
    }
  }

  fun register() {
    ApplicationServices.authenticationManager = authenticationManager
    ApplicationServices.dataStorageFactory = dataStorageFactory
    ApplicationServices.usageManager = usageManager
    ApplicationServices.userSettingsManager = userSettingsManager
    if(System.getProperty("DBRESET", "false").toBoolean()) teardownSchema()
    initializeSchema()
  }

  fun initializeSchema() {
    useConnection { connection ->
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
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE
                );
            """.trimIndent()
          )
        }

        connection.createStatement().use { statement ->
          statement.execute(
            """
                CREATE TABLE IF NOT EXISTS sessions (
                    session_id VARCHAR(255) PRIMARY KEY,
                    apiKey VARCHAR(255)
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
                    api_base VARCHAR(255),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE
                );
            """.trimIndent()
          )
        }

        connection.createStatement().use { statement ->
          statement.execute(
            """
              CREATE TABLE IF NOT EXISTS usage (
                  usage_id SERIAL PRIMARY KEY,
                  date_window VARCHAR(255),
                  session_id VARCHAR(255),
                  apiKey VARCHAR(255),
                  model VARCHAR(255),
                  input_tokens INT,
                  output_tokens INT,
                  cost DOUBLE PRECISION,
                  FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE ON UPDATE CASCADE
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
                CREATE INDEX IF NOT EXISTS usage_user_model ON usage(apiKey, date_window, model);
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
                    FOREIGN KEY (session_id) REFERENCES sessions(session_id) ON DELETE CASCADE ON UPDATE CASCADE
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

  fun teardownSchema() {
    useConnection { connection ->
      connection.autoCommit = false
      try {
        connection.createStatement().use { statement ->
          statement.execute(
            """
                DROP TABLE IF EXISTS messages;
            """.trimIndent()
          )
        }
        connection.createStatement().use { statement ->
          statement.execute(
            """
                DROP TABLE IF EXISTS usage;
            """.trimIndent()
          )
        }
        connection.createStatement().use { statement ->
          statement.execute(
            """
                DROP TABLE IF EXISTS user_settings;
            """.trimIndent()
          )
        }
        connection.createStatement().use { statement ->
          statement.execute(
            """
                DROP TABLE IF EXISTS authentication;
            """.trimIndent()
          )
        }
        connection.createStatement().use { statement ->
          statement.execute(
            """
                DROP TABLE IF EXISTS sessions;
            """.trimIndent()
          )
        }
        connection.createStatement().use { statement ->
          statement.execute(
            """
                DROP TABLE IF EXISTS users;
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
    override fun incrementUsage(session: Session, apiKey: String?, model: OpenAIModel, tokens: ApiModel.Usage) {
      useConnection { connection ->
        connection.autoCommit = false
        try {
          upsertSession(connection, session, apiKey)
          connection.prepareStatement(
            """
                INSERT INTO usage (session_id, apiKey, model, input_tokens, output_tokens, cost, date_window)
                VALUES (?, ?, ?, ?, ?, ?, ?);
            """.trimIndent()
          ).apply {
            setString(1, session.toString())
            setString(2, apiKey)
            setString(3, model.modelName)
            setInt(4, tokens.prompt_tokens)
            setInt(5, tokens.completion_tokens)
            setDouble(6, tokens.cost ?: 0.0)
            setString(7, getDateWindow())
            execute()
          }
          connection.commit()
        } catch (e: Exception) {
          connection.rollback()
          throw e
        }
      }
    }

    private fun getDateWindow(): String {
      return SimpleDateFormat("yyyy-MM").format(Date())
    }

    override fun getUserUsageSummary(apiKey: String) = useConnection { connection ->
      if(apiKey.isNullOrEmpty()) return@useConnection emptyMap()
      connection.autoCommit = false
      connection.prepareStatement(
        """
            SELECT 
                model, 
                SUM(input_tokens) AS input_tokens, 
                SUM(output_tokens) AS output_tokens, 
                SUM(cost) AS cost
            FROM usage
            WHERE apiKey = ? AND date_window = ?
            GROUP BY model;
        """.trimIndent()
      ).apply {
        setString(1, apiKey)
        setString(2, getDateWindow())
        executeQuery().use { resultSet ->
          val map = HashMap<OpenAIModel, ApiModel.Usage>()
          while (resultSet.next()) {
            val modelName = resultSet.getString("model")
            val inputTokens = resultSet.getInt("input_tokens")
            val outputTokens = resultSet.getInt("output_tokens")
            val cost = resultSet.getDouble("cost")
            map[getModel(modelName) ?: continue] = ApiModel.Usage(
              prompt_tokens = inputTokens,
              completion_tokens = outputTokens,
              cost = cost
            )
          }
          return@useConnection map
        }
      }
      return@useConnection emptyMap()
    }

    override fun getSessionUsageSummary(session: Session) =
      useConnection { connection ->
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
              map[getModel(modelName) ?: continue] = ApiModel.Usage(
                prompt_tokens = inputTokens,
                completion_tokens = outputTokens,
                cost = cost
              )
            }
            return@useConnection map
          }
        }
        @Suppress("UNREACHABLE_CODE")
        return@useConnection emptyMap()
      }

    override fun clear() {
      throw UnsupportedOperationException()
    }
  }
  val userSettingsManager: UserSettingsInterface = object : UserSettingsInterface {
    override fun getUserSettings(user: User) = useConnection { connection ->
      connection.autoCommit = false
      try {
        connection.prepareStatement("SELECT * FROM user_settings WHERE user_id = ?").apply {
          setString(1, user.id)
          executeQuery().use { resultSet ->
            if (resultSet.next()) {
              return@useConnection UserSettingsInterface.UserSettings(
                apiKey = resultSet.getString("api_key"),
                apiBase = resultSet.getString("api_base"),
              )
            }
          }
        }
        return@useConnection  UserSettingsInterface.UserSettings()
      } catch (e: Exception) {
        throw e
      }
    }

    override fun updateUserSettings(user: User, settings: UserSettingsInterface.UserSettings) {
      useConnection { connection ->
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
      return useConnection { connection ->
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
                    return@useConnection User(
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
        return@useConnection null
      }
    }

    override fun putUser(accessToken: String, user: User): User {
      useConnection { connection ->
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
      useConnection { connection ->
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
        ON CONFLICT (email) DO UPDATE
        SET id = EXCLUDED.id,
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


  fun upsertSession(connection: Connection, session: Session, apiKey: String?) {
    connection.prepareStatement("""
      INSERT INTO sessions (session_id, apiKey)
      VALUES (?, ?)
      ON CONFLICT (session_id) DO UPDATE
      SET apiKey = EXCLUDED.apiKey
      """.trimIndent()).apply {
      setString(1, session.toString())
      setString(2, apiKey)
      execute()
    }
  }

  val dataStorageFactory: (File) -> StorageInterface = { storageRoot -> DataStorage(storageRoot)  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val databaseServices = DatabaseServices("jdbc:h2:file:skyenet;DB_CLOSE_DELAY=-1")
      databaseServices.initializeSchema()
      println("Database schema initialized successfully.")
    }

  }
}

