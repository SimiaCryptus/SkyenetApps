package com.simiacryptus.skyenet.apps.outline

import com.simiacryptus.skyenet.apps.outline.OutlineActors.Companion.deepClone

open class OutlineManager() {

    data class Node(
        val data: String,
        val outline: OutlineActors.Outline,
    )

    data class Relationship(
        val from: Node,
        val to: Node,
        val name: String
    )

    fun OutlineActors.Outline.setAllParents(parent: OutlineActors.Item? = null): OutlineActors.Outline {
        this.items?.forEach { item ->
            if(parent != null) parentMap[item] = parent
            item.children?.setAllParents(item)
        }
        return this
    }

    var root: Node? = null
    val relationships = mutableListOf<Relationship>()
    val nodes = mutableListOf<Node>()
    val expandedOutlineNodeMap = mutableMapOf<OutlineActors.Item, Node>()
    private val parentMap: MutableMap<OutlineActors.Item, OutlineActors.Item> = mutableMapOf()

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
        if(size > 1) return outline.items?.map { item ->
            OutlineActors.Outline(listOf(item.deepClone()))
        }
        else if (size == 0) {
            return listOf(outline)
        } else {
            // size == 1
            val child = outline.items?.first() ?: return listOf(outline)
            val explodedChild = explode(child)
            return explodedChild.map { item ->
                OutlineActors.Outline(listOf(item.deepClone()))
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

    private fun replaceWithExpandedNodes(node: OutlineActors.Outline): OutlineActors.Outline {
        val items = node.items?.map { item: OutlineActors.Item ->
            val expandedNode = expandedOutlineNodeMap[item]
            val expandedOutline = expandedNode?.outline?.deepClone()
            if (expandedOutline == node) item.deepClone()
            else if (node == item.children) item.deepClone()
            else if (expandedOutline == null) item.deepClone()
            else {
                var children = item.children
                if (1 == expandedOutline.items?.size) {
                    children = expandedOutline.items.first().children
                } else if ((expandedOutline.items?.size ?: 0) > 1) {
                    children = expandedOutline
                } else {
                    // No expansion
                }
                if (null != children) children = replaceWithExpandedNodes(children)
                item.deepClone().copy(children = children)
            }
        }
        return OutlineActors.Outline(items = items)
    }
}