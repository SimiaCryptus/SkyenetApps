package com.simiacryptus.util.files

import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

class LongArrayMappedFile(file: File, count: Long) {

  private val mappedByteBuffer by lazy {
    channel.map(FileChannel.MapMode.READ_WRITE, 0, 4 * length)
  }

  private var length : Long = -1
  fun getLength() = length

  private val channel by lazy {
    length = if (!file.exists()) {
      initialize(file, count)
      count
    } else {
      file.length() / 4
    }
    FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.READ)
  }

  fun get(position: Long): Long {
    require(position >= 0) { "Index out of bounds: $position" }
    require(position < length) { "Index out of bounds: $position / $length" }
    val idx = 4 * position
    val value = mappedByteBuffer.getInt(idx.toInt()).toLong()
    require(value >= 0) { "Index out of bounds: $value @$position" }
    require(value < length) { "Index out of bounds: $value / $length @$position" }
    return value
  }

  fun set(position: Long, value: Long) {
    require(position >= 0) { "Index out of bounds: $position" }
    require(position < length) { "Index out of bounds: $position / $length" }
    mappedByteBuffer.putInt(4 * position.toInt(), value.toInt())
  }

  fun close() {
    mappedByteBuffer.force()
    channel.close()
  }

  companion object {
    fun initialize(file: File, count: Long) {
      file.createNewFile()
      file.setWritable(true)
      file.setReadable(true)
      file.setExecutable(false)
      file.outputStream().buffered().use { out ->
        val byteArray = ByteArray(4)
        val wrap = ByteBuffer.wrap(byteArray)
        (0 until count).forEach { i ->
          wrap.clear()
          wrap.putInt(-1)
          out.write(byteArray)
        }
      }
    }
  }

}