package com.simiacryptus.util.stm

import org.slf4j.LoggerFactory

class BlobDB : BlobStorage {
  private val data = mutableMapOf<Int, ByteArray>()
  override fun write(json: ByteArray): Int {
    val id = data.count()
    log.debug("Writing $id: ${json?.size} bytes: ${String(json)}")
    data[id] = json
    return id
  }

  override fun read(id: Int): ByteArray? {
    val bytes = data[id]
    log.debug("Reading $id: ${bytes?.size} bytes: ${String(bytes!!)}")
    return bytes
  }


  companion object {
    val log = LoggerFactory.getLogger(BlobDB::class.java)
  }
}