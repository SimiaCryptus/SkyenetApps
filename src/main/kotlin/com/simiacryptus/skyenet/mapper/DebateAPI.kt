package com.simiacryptus.skyenet.mapper

import com.simiacryptus.openai.proxy.ValidatedObject
import com.simiacryptus.util.describe.Description

interface DebateAPI {

    @Description("Dissect debate arguments into a recursive outline of the main ideas and supporting details.")
    fun toDebateSetup(text: String): DebateSetup

    data class DebateSetup (
        val debators: Debators? = null,
        val questions: Questions? = null,
    )

    data class Debators(
        val list: List<Debator>? = null,
    )

    data class Questions(
        val list: List<Question>? = null,
    )

    data class Debator(
        val name: String? = null,
        val description: String? = null,
    )

    data class Question(
        val text: String? = null,
    )


    @Description("Dissect debate arguments into a recursive outline of the main ideas and supporting details.")
    fun toOutline(text: String): Outline

    data class Outline(
        val arguments: List<Argument>? = null,
    ) : ValidatedObject {
        override fun validate() = arguments?.all { it.validate() } ?: false


        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Outline

            return arguments == other.arguments
        }

        override fun hashCode(): Int {
            return arguments?.hashCode() ?: 0
        }
    }

    data class Argument(
        val point_name: String? = null,
        val text: String? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            null == point_name -> false
            point_name.isEmpty() -> false
            else -> true
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Argument

            if (point_name != other.point_name) return false
            if (text != other.text) return false

            return true
        }

        override fun hashCode(): Int {
            var result = point_name?.hashCode() ?: 0
            result = 31 * result + (text?.hashCode() ?: 0)
            return result
        }


    }

    companion object {
        fun Outline.deepClone(): Outline =
            Outline(this.arguments?.map { it.deepClone() })

        fun Argument.deepClone(): Argument = Argument(
            point_name = this.point_name,
            text = this.text,
        )

    }
}