package com.simiacryptus.skyenet.apps.general

import com.fasterxml.jackson.annotation.JsonIgnore
import com.simiacryptus.jopenai.proxy.ValidatedObject

open class OutlineManager(val rootNode: OutlinedText) {

    data class NodeList(
      val children: List<Node>? = null,
    ) : ValidatedObject {
        override fun validate(): String? = when {
            children == null -> "children is required"
            !children.all { it.validate() == null } -> children.map { it.validate() }.filter { null != it }.joinToString("\n")
            children.size != children.map { it.name ?: "" }.distinct().size -> "children must have unique names"
            else -> null
        }

        fun deepClone(): NodeList =
            NodeList(this.children?.map { it.deepClone() })

        @JsonIgnore
        fun getTextOutline(): String {
            val sb = StringBuilder()
            children?.forEach { item ->
                sb.append(item.getTextOutline().trim())
                sb.append("\n")
            }
            return sb.toString()
        }

        @JsonIgnore
        fun getTerminalNodeMap(): Map<String, Node> {
            val nodeMap = children?.map { node ->
                val map = node.children?.getTerminalNodeMap()
                    ?.mapKeys { entry -> node.name + " / " + entry.key }
                if (map.isNullOrEmpty()) mapOf(node.name to node) else map
            }?.flatMap { it.entries }?.associate { (it.key ?: "") to it.value }
            return if (nodeMap.isNullOrEmpty()) {
                emptyMap()
            } else {
                nodeMap
            }
        }
    }

    data class Node(
      val name: String? = null,
      val children: NodeList? = null,
      val description: String? = null,
    ) : ValidatedObject {
        override fun validate(): String? = when {
            null == name -> "name is required"
            name.isEmpty() -> "name is required"
            else -> null
        }

        fun deepClone(): Node = Node(
            name = this.name,
            children = this.children?.deepClone(),
            description = this.description
        )

        @JsonIgnore
        fun getTextOutline(): String {
            val sb = StringBuilder()
            sb.append("* " + ((description?.replace("\n", "\\n") ?: name)?.trim() ?: ""))
            sb.append("\n")
            val childrenTxt = children?.getTextOutline()?.replace("\n", "\n  ")?.trim() ?: ""
            if (childrenTxt.isNotEmpty()) sb.append("  " + childrenTxt)
            return sb.toString()
        }

    }

    data class OutlinedText(
      val text: String,
      val outline: NodeList,
    )

    val nodes = mutableListOf<OutlinedText>()
    val expansionMap = mutableMapOf<Node, OutlinedText>()

    fun expandNodes(nodeList: NodeList): List<NodeList>? {
        val size = nodeList.children?.size ?: 0
        return when {
            size == 0 -> listOf(nodeList)
            size > 1 -> nodeList.children?.map { NodeList(listOf(it.deepClone())) }
            else -> {
                val child = nodeList.children?.first() ?: return listOf(nodeList)
                expandNodes(child).map { NodeList(listOf(it.deepClone())) }
            }
        }
    }

    private fun expandNodes(node: Node): List<Node> {
        val size = node.children?.children?.size ?: 0
        if (size > 1) return node.children?.children?.map {
            Node(
                name = it.name,
                description = it.description,
                children = NodeList(listOf(it.deepClone())),
            )
        } ?: listOf() else if (size == 0) {
            return listOf(node)
        } else {
            // size == 1
            val child = node.children?.children?.first() ?: return listOf(node)
            val expandSectionsdChild = expandNodes(child)
            return expandSectionsdChild.map {
                Node(
                    name = it.name,
                    description = it.description,
                    children = NodeList(listOf(it.deepClone())),
                )
            }
        }
    }

    fun getLeafDescriptions(nodeList: NodeList): List<String> =
        nodeList.children?.flatMap { getLeafDescriptions(it) } ?: listOf()

    private fun getLeafDescriptions(outline: Node): List<String> =
        listOf(outline.description ?: "") + (outline.children?.children?.flatMap { getLeafDescriptions(it) }
            ?: listOf())

    fun buildFinalOutline(): NodeList {
        return buildFinalOutline(rootNode?.outline?.deepClone() ?: return NodeList()) ?: NodeList()
    }

    private fun buildFinalOutline(outline: NodeList?, maxDepth: Int = 10): NodeList? {
        return NodeList(children = outline?.children?.map { node: Node ->
            val expanded = (expansionMap[node]?.outline ?: node.children)?.deepClone()
            when {
                expanded == null -> {
                    log.warn("No expansion for ${node.name}")
                    node.deepClone()
                }

                else -> {
                    var children = if (1 == (expanded.children?.size ?: 0)) {
                        expanded.children?.first()?.children ?: node.children
                    } else if ((expanded.children?.size ?: 0) > 1) {
                        expanded
                    } else {
                        node.children
                    }
                    if (null != children) {
                        if (maxDepth > 0) {
                            children = buildFinalOutline(children, maxDepth - 1)
                        } else {
                            log.warn("Max depth exceeded for ${node.name}")
                        }
                    }
                    node.deepClone().copy(children = children)
                }
            }
        } ?: return null)
    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(OutlineManager::class.java)
    }
}