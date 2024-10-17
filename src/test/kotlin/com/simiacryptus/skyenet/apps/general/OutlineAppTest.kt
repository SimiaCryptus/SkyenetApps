package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.util.JsonUtil
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf

class OutlineAppTest {
    @Test
    fun test() {
        @Language("JSON") val fromJson = JsonUtil.fromJson<OutlineManager.NodeList>(
            """
            |{
            |  "children": [
            |    {
            |      "name": "Story Introduction",
            |      "description": "Once upon a time in a quaint little village nestled between rolling hills and vast fields of golden wheat, there lived an old toymaker named Elias, known for his exquisite handmade toys.",
            |      "children": [
            |        {
            |          "name": "Elias's Background",
            |          "description": "Elias was a lonely soul with no children and his wife had passed away many years ago. His only companion was a small dog named Patch."
            |        },
            |        {
            |          "name": "Elias's Wish",
            |          "description": "One evening, Elias expressed a wish for his toys to talk to alleviate his loneliness."
            |        }
            |      ]
            |    },
            |    {
            |      "name": "The Knock at the Door",
            |      "description": "A sudden knock at the door reveals a little girl named Lucy, who asks Elias to fix her broken doll.",
            |      "children": [
            |        {
            |          "name": "Lucy's Request",
            |          "description": "Lucy, a poor girl with a broken doll, seeks help from Elias to fix the doll, which is a keepsake of her mother."
            |        },
            |        {
            |          "name": "Elias's Response",
            |          "description": "Moved by Lucy's situation, Elias invites her in and agrees to fix the doll without charge."
            |        }
            |      ]
            |    },
            |    {
            |      "name": "Bonding Over Toys",
            |      "description": "As Elias repairs the doll, he offers Lucy a chance to help in the workshop, which she gladly accepts.",
            |      "children": [
            |        {
            |          "name": "The Workshop's Transformation",
            |          "description": "Lucy's presence brings joy and laughter to the workshop, transforming it from a place of solitude to one of happiness."
            |        },
            |        {
            |          "name": "Elias and Lucy's Relationship",
            |          "description": "Over the years, Elias and Lucy develop a close bond, akin to that of a grandfather and granddaughter."
            |        }
            |      ]
            |    },
            |    {
            |      "name": "Conclusion",
            |      "description": "Lucy eventually leaves the village, but the bond with Elias remains. Elias is never lonely again, and the toys seem to come alive, keeping him company.",
            |      "children": [
            |        {
            |          "name": "Elias's Later Years",
            |          "description": "Elias continues to feel the companionship of the toys and Lucy's impact, never feeling lonely again."
            |        },
            |        {
            |          "name": "Legacy of the Toymaker",
            |          "description": "After Elias's passing, villagers still hear the sounds of a music box from his workshop, suggesting a magical legacy."
            |        }
            |      ]
            |    }
            |  ]
            |}
            """.trimMargin(), typeOf<OutlineManager.NodeList>().javaType
        )
        Assertions.assertTrue(fromJson.validate() == null)
    }
}