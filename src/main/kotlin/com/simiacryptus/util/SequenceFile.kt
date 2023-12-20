package com.simiacryptus.util

import com.simiacryptus.util.IntArrayFile.Companion.toBytes
import com.simiacryptus.util.IntArrayFile.Companion.toInt
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

  fun get(pos: Int) : ByteArray {
    read = true
    val buffer = ByteArray(4)
    // Seek to the position of the length field
    var curPos = 0
    var curIdx = 0
    while(curPos < pos) {
      mappedByteBuffer.get(buffer, curPos, 4)
      curPos += buffer.toInt() + 4
      curIdx += 1
    }
    // Read the length field
    mappedByteBuffer.get(buffer, curPos, 4)
    val length = buffer.toInt()
    // Read the string
    val result = ByteArray(length)
    mappedByteBuffer.get(result, curPos + 4, length)
    return result
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
