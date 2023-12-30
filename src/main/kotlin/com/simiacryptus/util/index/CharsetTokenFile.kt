package com.simiacryptus.util.index

import java.io.File
import java.nio.ByteBuffer

class CharsetTokenFile(
  file: File,
  charsetName: String = "UTF-8",
  val maxCharSize: Int = 8
) : TokenFile(file) {
  private val charset = java.nio.charset.Charset.forName(charsetName)
  override var tokenCount: Long = file.length()
  override val indices by lazy {
    indexArray.asIterable()
  }

  private val indexArray by lazy {
    (0 until fileLength).runningFold(0L) { position, index ->
      val buffer = ByteArray(maxCharSize)
      read(position, buffer)
      val first = charset.decode(ByteBuffer.wrap(buffer)).first()
      val size = first.toString().encodeToByteArray().size
      position + size
    }.takeWhile { it < fileLength }.toLongArray()
  }

  init {
    tokenCount = indexArray.size.toLong()
  }

  override fun readString(position: Long, n: Int, skip: Int): String {
    val buffer = ByteArray(((n + skip) * maxCharSize).coerceAtMost(fileLength.toInt()))
    read(indexArray[position.toInt()], buffer)
    return charset.decode(ByteBuffer.wrap(buffer)).drop(skip).take(n).toString()
  }

  override fun charIterator(position: Long): () -> CharIterator {
    val initialBuffer = readString(position, 16.coerceAtMost(tokenCount.toInt() - 1))
    return {
      object : CharIterator() {
        var buffer = initialBuffer
        var nextPos = position + initialBuffer.length
        var pos = 0
        override fun hasNext() = true
        override fun nextChar(): Char {
          val char = buffer.get(pos++)
          if (pos >= buffer.length) {
            buffer = readString(nextPos, 16)
            nextPos = nextPos + buffer.length
            pos = 0
          }
          return char
        }
      }
    }
  }

}

