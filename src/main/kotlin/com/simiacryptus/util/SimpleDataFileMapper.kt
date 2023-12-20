package com.simiacryptus.util

import java.io.File

class SimpleDataFileMapper(file: File) : DataFileMapper(file) {

  val indices by lazy { 0 until recordLength }
  override val recordLength: Long = run {
    val length = fileLength
    require(length > 0) { "Data file empty: $length" }
    require(length < Int.MAX_VALUE) { "Data file too large: $length" }
    length
  }

  override fun get(position: Long): () -> CharIterator = {
    object : CharIterator() {
      val buffer = ByteArray(1)
      var current = position
      override fun hasNext() = true
      override fun nextChar(): Char {
        read(current, buffer)
        current = (current + 1) % fileLength
        return buffer[0].toInt().toChar()
      }
    }
  }

}

