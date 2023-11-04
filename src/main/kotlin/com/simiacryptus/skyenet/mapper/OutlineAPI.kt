package com.simiacryptus.skyenet.mapper

import com.simiacryptus.openai.proxy.ValidatedObject
import com.simiacryptus.util.describe.Description

interface OutlineAPI {

    @Description("Break down the text into a recursive outline of the main ideas and supporting details.")
    fun toOutline(text: String): Outline

    data class Outline(
        val items: List<Item>? = null,
    ) : ValidatedObject {
        override fun validate() = items?.all { it.validate() } ?: false


        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Outline

            return items == other.items
        }

        override fun hashCode(): Int {
            return items?.hashCode() ?: 0
        }
    }

    data class Item(
        val section_name: String? = null,
        var children: Outline? = null,
        val text: String? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            null == section_name -> false
            section_name.isEmpty() -> false
            else -> true
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Item

            if (section_name != other.section_name) return false
            if (children != other.children) return false
            if (text != other.text) return false

            return true
        }

        override fun hashCode(): Int {
            var result = section_name?.hashCode() ?: 0
            result = 31 * result + (children?.hashCode() ?: 0)
            result = 31 * result + (text?.hashCode() ?: 0)
            return result
        }
    }
    companion object {
        fun Outline.deepClone(): Outline =
             Outline(this.items?.map { it.deepClone() })

        fun Item.deepClone(): Item = Item(
            section_name = this.section_name,
            children = this.children?.deepClone(),
            text = this.text
        )

        fun Outline.getTextOutline(): String {
            val sb = StringBuilder()
            items?.forEach { item ->
                sb.append(item.getTextOutline().trim())
                sb.append("\n")
            }
            return sb.toString()
        }

        fun Item.getTextOutline(): String {
            val sb = StringBuilder()
            sb.append("* " + ((text?.replace("\n", "\\n") ?: section_name)?.trim() ?: ""))
            sb.append("\n")
            val childrenTxt = children?.getTextOutline()?.replace("\n", "\n\t")?.trim() ?: ""
            if(childrenTxt.isNotEmpty()) sb.append("\t" + childrenTxt)
            return sb.toString()
        }

        fun Outline.getTerminalNodeMap(): Map<String, Item> = items?.flatMap { item ->
            val children = item.children
            if(children?.items?.isEmpty() != false) listOf(item.section_name!! to item)
            else children.getTerminalNodeMap().map { (key, value) -> item.section_name + " / " + key to value }
        }?.toMap() ?: emptyMap()
    }
}