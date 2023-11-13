package com.simiacryptus.skyenet.apps.outline

import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.deepClone

open class OutlineManager {

    data class Node(
        val data: String,
        val outline: OutlineActors.Outline,
    )

    data class Relationship(
        val from: Node,
        val to: Node,
        val name: String
    )

    var root: Node? = null
    val relationships = mutableListOf<Relationship>()
    val nodes = mutableListOf<Node>()
    val expandedOutlineNodeMap = mutableMapOf<OutlineActors.Item, Node>()

    private fun explode(item: OutlineActors.Item): List<OutlineActors.Item> {
        val size = item.children?.items?.size ?: 0
        if(size > 1) return item.children?.items?.map {
            OutlineActors.Item(
                section_name = it.section_name,
                text = it.text,
                children = OutlineActors.Outline(listOf(it.deepClone())),
            )
        } ?: listOf() else if (size == 0) {
            return listOf(item)
        } else {
            // size == 1
            val child = item.children?.items?.first() ?: return listOf(item)
            val explodedChild = explode(child)
            return explodedChild.map {
                OutlineActors.Item(
                    section_name = it.section_name,
                    text = it.text,
                    children = OutlineActors.Outline(listOf(it.deepClone())),
                )
            }
        }
    }

    fun explode(outline: OutlineActors.Outline): List<OutlineActors.Outline>? {
        val size = outline.items?.size ?: 0
        return when {
            size == 0 -> listOf(outline)
            size > 1 -> outline.items?.map {OutlineActors.Outline(listOf(it.deepClone())) }
            else -> {
                val child = outline.items?.first() ?: return listOf(outline)
                explode(child).map { OutlineActors.Outline(listOf(it.deepClone())) }
            }
        }
    }

    fun getAllItems(outline: OutlineActors.Outline): List<String>  = outline.items?.flatMap { getAllItems(it) } ?: listOf()

    private fun getAllItems(outline: OutlineActors.Item): List<String> = listOf(outline.text ?: "") + (outline.children?.items?.flatMap { getAllItems(it) } ?: listOf())

    fun buildFinalOutline(): OutlineActors.Outline {
        var clonedRoot = root?.outline?.deepClone()
        if(null != clonedRoot) clonedRoot = replaceWithExpandedNodes(clonedRoot)
        return clonedRoot ?: OutlineActors.Outline()
    }

    private fun replaceWithExpandedNodes(node: OutlineActors.Outline?): OutlineActors.Outline? {
        return OutlineActors.Outline(items = node?.items?.map { item: OutlineActors.Item ->
            val expandedOutline = expandedOutlineNodeMap[item]?.outline?.deepClone()
            when {
                expandedOutline == node -> item.deepClone()
                node == item.children -> item.deepClone()
                expandedOutline == null -> item.deepClone()
                else -> {
                    var children = getOutlineForSubstitution(item.children, expandedOutline)
                    if (null != children) children = replaceWithExpandedNodes(children)
                    item.deepClone().copy(children = children)
                }
            }
        } ?: return null)
    }

    private fun getOutlineForSubstitution(
        prior: OutlineActors.Outline?,
        expanded: OutlineActors.Outline
    ): OutlineActors.Outline? {
        return if (1 == (expanded.items?.size ?: 0)) {
            expanded.items?.first()?.children ?: prior
        } else if ((expanded.items?.size ?: 0) > 1) {
            expanded
        } else {
            prior
        }
    }
}