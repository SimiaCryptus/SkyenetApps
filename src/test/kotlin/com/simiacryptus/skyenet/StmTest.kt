package com.simiacryptus.skyenet

import com.simiacryptus.util.stm.TransactionRoot
import com.simiacryptus.util.stm.Pointer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class StmTest {

  @Test
  fun test() {
    val stm = TransactionRoot()
    stm.transact { stm ->
      val root = stm.root<Map<String, Pointer<*>>>()
      root.setValue(HashMap())
    }
    stm.transact { stm ->
      val root = stm.root<MutableMap<String, Pointer<*>>>()
      val newPointer = stm.newPointer<String>()
      val map = root.getValue<MutableMap<String, Pointer<*>>>()
      map["test"] = newPointer
      newPointer.setValue("foo")
    }
    stm.transact { stm ->
      val root = stm.root<MutableMap<String, Pointer<*>>>()
      val map = root.getValue<MutableMap<String, Pointer<*>>>()
      Assertions.assertEquals("foo", map["test"]?.getValue<String>())
    }
    try {
      stm.transact { stm ->
        val root = stm.root<MutableMap<String, Pointer<*>>>()
        val newPointer = stm.newPointer<String>()
        val map = root.getValue<MutableMap<String, Pointer<*>>>()
        map["test"] = newPointer
        newPointer.setValue("bar")
        throw RuntimeException("Rollback Transaction")
      }
      throw IllegalStateException("Expected RuntimeException")
    } catch (e : RuntimeException) {}
    stm.transact { stm ->
      val root = stm.root<MutableMap<String, Pointer<*>>>()
      val map = root.getValue<MutableMap<String, Pointer<*>>>()
      Assertions.assertEquals("foo", map["test"]?.getValue<String>())
    }
  }
}