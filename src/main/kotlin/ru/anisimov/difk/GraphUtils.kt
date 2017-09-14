/*
 * Copyright 2017 Ivan Anisimov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.anisimov.difk

import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet


/**
 * @author Ivan Anisimov
 *         valter@yandex-team.ru
 *         14.09.17
 */
internal class Graph<V> {
    private val adjacencyList: Map<V, List<Edge>>
    private val parentNodes: MutableSet<V>
    private val childNodes: MutableSet<V>
    private val vertexes: Set<V>

    constructor(graph: Map<V, Set<V>>) {
        vertexes = HashSet()
        vertexes.addAll(graph.keys)
        graph.values.forEach { vertexes.addAll(it) }
        adjacencyList = graph.entries
                .map { e -> Pair(e.key, e.value.map { Edge(e.key, it) }.toList()) }
                .toMap()

        childNodes = HashSet()
        graph.values.forEach { childNodes.addAll(it) }
        parentNodes = HashSet()
        parentNodes.addAll(graph.keys)
        parentNodes.removeAll(childNodes)
    }

    fun parentVertexes(): Set<V> {
        return parentNodes
    }

    fun vertexes(): Set<V> {
        return vertexes
    }

    fun outgoingEdges(vertex: V): List<Edge> {
        return adjacencyList[vertex] ?: emptyList()
    }

    inner class Edge(val from: V, val to: V)
}

internal fun <V> hasCycles(graph: Graph<V>): Boolean {
    return cycles(graph).isNotEmpty()
}

// Tarjan algorithm
internal fun <V> cycles(graph: Graph<V>): List<List<V>> {
    val cycles = ArrayList<MutableList<V>>()
    val marked = HashSet<V>()
    val markedStack = ArrayDeque<V>()
    val pointStack = ArrayDeque<V>()
    val removed = HashMap<V, MutableSet<V>>()
    val vToI = HashMap<V, Int>()
    graph.vertexes().forEachIndexed { index, v -> vToI.put(v, index) }

    fun getRemoved(v: V): MutableSet<V> {
        var result = removed[v]
        if (result == null) {
            result = HashSet()
            removed.put(v, result)
        }
        return result
    }

    fun backtrack(start: V, vertex: V): Boolean {
        var foundCycle = false
        pointStack.push(vertex)
        marked.add(vertex)
        markedStack.push(vertex)

        for (currentEdge in graph.outgoingEdges(vertex)) {
            val currentVertex = currentEdge.to
            if (getRemoved(vertex).contains(currentVertex)) {
                continue
            }
            val comparison = vToI[currentVertex]!!.compareTo(vToI[start]!!)
            if (comparison < 0) {
                getRemoved(vertex).add(currentVertex)
            } else if (comparison == 0) {
                foundCycle = true
                val cycle = ArrayList<V>()
                val it = pointStack.descendingIterator()
                var v: V
                while (it.hasNext()) {
                    v = it.next()
                    if (start!! == v) {
                        break
                    }
                }
                cycle.add(start)
                while (it.hasNext()) {
                    cycle.add(it.next())
                }
                cycles.add(cycle)
            } else if (!marked.contains(currentVertex)) {
                val gotCycle = backtrack(start, currentVertex)
                foundCycle = foundCycle || gotCycle
            }
        }

        if (foundCycle) {
            while (markedStack.peek()!! != vertex) {
                marked.remove(markedStack.pop())
            }
            marked.remove(markedStack.pop())
        }

        pointStack.pop()
        return foundCycle
    }

    for (start in graph.vertexes()) {
        backtrack(start, start)
        while (!markedStack.isEmpty()) {
            marked.remove(markedStack.pop())
        }
    }

    return cycles
}

internal fun <V> bfs(graph: Graph<V>): List<V> {
    val queue = LinkedList<V>()
    val used = HashSet<V>()

    queue.addAll(graph.parentVertexes())
    used.addAll(graph.parentVertexes())

    while (!queue.isEmpty()) {
        val node = queue.poll()
        for (edge in graph.outgoingEdges(node)) {
            if (!used.contains(edge.to)) {
                queue.add(edge.to)
                used.add(edge.to)
            }
        }
    }

    return ArrayList(used)
}
