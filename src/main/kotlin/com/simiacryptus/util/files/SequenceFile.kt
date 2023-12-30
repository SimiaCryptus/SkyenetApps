package com.simiacryptus.util.files

import com.simiacryptus.util.files.IntArrayAppendFile.Companion.toBytes
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

class SequenceFile(file: File) {

  private val channel by lazy { FileChannel.open(file.toPath(), StandardOpenOption.READ) }
  private val mappedByteBuffer by lazy { channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length()) }
  private val bufferedOutputStream by lazy { file.outputStream().buffered() }

  private var read = false
  private var write = false
  private var pos = 0L

  fun append(str: ByteArray): Int {
    bufferedOutputStream.write(str.size.toBytes())
    bufferedOutputStream.write(str)
    write = true
    return pos++.toInt()
  }

  fun get(pos: Int) : ByteArray? {
    read = true
    var curPos = 0
    var curIdx = 0
    val capacity = mappedByteBuffer.capacity()
    while(curIdx < pos) {
      if(curPos >= capacity) return null
      curPos += mappedByteBuffer.getInt(curPos) + 4
      curIdx += 1
    }
    val length = mappedByteBuffer.getInt(curPos)
    curPos += 4
    val result = ByteArray(length)
    if (curPos + length > capacity) return null
    mappedByteBuffer.get(curPos, result)
    return result
  }

  fun read() : Array<ByteArray> {
    val result = mutableListOf<ByteArray>()
    var curPos = 0
    val capacity = mappedByteBuffer.capacity()
    while(curPos < capacity) {
      val length = mappedByteBuffer.getInt(curPos)
      curPos += 4
      if (curPos + length > capacity) {
        throw IllegalStateException()
      }
      val str = ByteArray(length)
      mappedByteBuffer.get(curPos, str)
      result.add(str)
      curPos += length
    }
    return result.toTypedArray()
  }

  fun close() {
    if (write) {
      bufferedOutputStream.close()
    }
    if (read) {
      channel.close()
    }
  }

}
