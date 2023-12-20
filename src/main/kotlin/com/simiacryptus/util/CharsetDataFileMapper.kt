package com.simiacryptus.util

import java.io.File
import java.nio.ByteBuffer

class CharsetDataFileMapper(file: File, charsetName: String = "UTF-8", val maxCharSize: Int = 8) :
  DataFileMapper(file) {
  private val charset = java.nio.charset.Charset.forName(charsetName)
  override var recordLength: Long = file.length()
  val indices by lazy {
    (0 until fileLength).runningFold(0L) { position, index ->
      val buffer = ByteArray(maxCharSize)
      read(position, buffer)
      val first = charset.decode(ByteBuffer.wrap(buffer)).first()
      val size = first.toString().encodeToByteArray().size
      position + size
    }.takeWhile { it < fileLength }.toLongArray()
  }
  init {
    recordLength = indices.size.toLong()
  }


  override fun readString(position: Long, n: Int, skip: Int): String {
    val buffer = ByteArray(((n+skip)*maxCharSize).coerceAtMost(fileLength.toInt()))
    read(indices[position.toInt()], buffer)
    return charset.decode(ByteBuffer.wrap(buffer)).drop(skip).take(n).toString()
  }

  override fun get(position: Long): () -> CharIterator {
    val initialBuffer = readString(position, 16.coerceAtMost(recordLength.toInt()-1))
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