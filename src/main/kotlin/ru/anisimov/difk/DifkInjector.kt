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

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

internal typealias DifkSupplier<T> = () -> T
internal typealias DifkAction = () -> Unit

/**
 * @author Ivan Anisimov
 *         valter@yandex-team.ru
 *         14.09.17
 */
class DifkInjector {
    internal var shutdownHook: Thread? = null

    private val wrappersByName: MutableMap<String, InstanceWrapper<*>> = HashMap()
    private val initializers: MutableList<DifkAction> = ArrayList()
    private val destructors: MutableList<DifkAction> = ArrayList()
    private val dependencyRelations: MutableMap<String, MutableSet<String>> = HashMap()
    private val properties = Properties()

    private val initInProcess: AtomicBoolean = AtomicBoolean(false)
    private val initialized: AtomicBoolean = AtomicBoolean(false)

    fun <T : Any> addSingleton(id: String, builder: DifkSupplier<T>): DifkInjector {
        throwExceptionIfInitialized()
        wrappersByName.put(id, SingletonWrapper(builder, false))
        return this
    }

    fun <T : Any> addSingleton(id: String, builder: DifkSupplier<T>, lazy: Boolean): DifkInjector {
        throwExceptionIfInitialized()
        wrappersByName.put(id, SingletonWrapper(builder, lazy))
        return this
    }

    fun <T : Any> addPrototype(id: String, builder: DifkSupplier<T>): DifkInjector {
        throwExceptionIfInitialized()
        wrappersByName.put(id, PrototypeWrapper(builder))
        return this
    }

    fun <T : Any> addThreadLocal(id: String, builder: DifkSupplier<T>): DifkInjector {
        throwExceptionIfInitialized()
        wrappersByName.put(id, ThreadLocalWrapper(builder))
        return this
    }

    fun addSingletonDependency(idDependant: String, idProvider: String) {
        throwExceptionIfInitialized()
        if (idDependant == idProvider) {
            throw RuntimeException("Instance can't depend on itself")
        }
        dependencyRelations.putIfAbsent(idProvider, HashSet())
        dependencyRelations[idProvider]?.add(idDependant)
    }

    fun addInitializer(initializer: DifkAction) {
        throwExceptionIfInitialized()
        initializers.add(initializer)
    }

    fun addDestructor(destructor: DifkAction) {
        throwExceptionIfInitialized()
        destructors.add(destructor)
    }

    fun <T> getInstanceOrNull(id: String): T? {
        throwExceptionIfNotInitialized()
        return wrappersByName[id]?.instance() as T?
    }

    fun <T> getInstance(id: String): T {
        return getInstanceOrNull(id) ?: throw RuntimeException("Bean with id {id} not found")
    }

    fun loadPropertiesFromClasspath(fileName: String) {
        throwExceptionIfInitialized()
        properties.load(BufferedInputStream(this.javaClass.classLoader.getResourceAsStream(fileName)))
    }

    fun loadPropertiesFromFile(fileName: String) {
        throwExceptionIfInitialized()
        properties.load(BufferedInputStream(FileInputStream(fileName)))
    }

    fun getPropertyOrNull(name: String): String? {
        throwExceptionIfNotInitialized()
        return properties.getProperty(name)
    }

    fun getProperty(name: String): String {
        return getPropertyOrNull(name) ?: throw RuntimeException("Property with name {name} does not exists")
    }

    fun getProperty(name: String, default: String): String {
        throwExceptionIfNotInitialized()
        return properties.getProperty(name, default)
    }

    fun setProperty(name: String, value: String) {
        throwExceptionIfInitialized()
        properties.setProperty(name, value)
    }

    fun registerShutdownHook() {
        throwExceptionIfInitialized()
        if (shutdownHook == null) {
            shutdownHook = object : Thread() {
                override fun run() {
                    synchronized(this) {
                        doClose()
                    }
                }
            }
            Runtime.getRuntime().addShutdownHook(this.shutdownHook)
        }
    }

    fun init() {
        synchronized(this) {
            initInProcess.set(true)
            throwExceptionIfInitialized()
            createSingletons()
            doInitialize()
            initialized.set(true)
            initInProcess.set(false)
        }
    }

    private fun createSingletons() {
        checkDependencyRelations()
        val initOrder = instanceInitOrder()
        initOrder
                .map { { id: String -> wrappersByName[id] } }
                .filter { it::class == SingletonWrapper::class }
                .map { it as SingletonWrapper<*> }
                .filterNot { it.lazy }
                .forEach { it.instance() }
    }

    private fun instanceInitOrder(): List<String> {
        val instancesWithDefinedDependencies = sortRelations(dependencyRelations)
        val instancesWithoutDefinedDependencies = HashSet(wrappersByName.keys)
        instancesWithoutDefinedDependencies.removeAll(instancesWithDefinedDependencies)
        instancesWithDefinedDependencies.addAll(instancesWithoutDefinedDependencies)
        return instancesWithDefinedDependencies
    }

    internal fun sortRelations(dependencies: MutableMap<String, MutableSet<String>>): MutableList<String> {
        val graph = Graph(dependencies)
        if (hasCycles(graph)) {
            throw RuntimeException("Instances dependency graph has cycles")
        }
        return ArrayList(bfs(graph))
    }

    private fun checkDependencyRelations() {
        val instances: MutableSet<String> = HashSet()
        for ((key, value) in dependencyRelations) {
            instances.add(key)
            instances.addAll(value)
        }
        instances
                .filterNot { singletonExists(it) }
                .forEach { throw RuntimeException("One of singletons in relation does not exists: [{key} -> {value}]") }
    }

    private fun singletonExists(id: String): Boolean {
        val wrapper = wrappersByName[id]
        return if (wrapper == null) false else wrapper::class == SingletonWrapper::class
    }

    private fun doInitialize() {
        for (initializer in initializers) {
            initializer()
        }
    }

    private fun throwExceptionIfInitialized() {
        if (initialized.get()) {
            throw RuntimeException("Context is already initialized")
        }
    }

    private fun throwExceptionIfNotInitialized() {
        if (!initialized.get() && !initInProcess.get()) {
            throw RuntimeException("Context is not initialized")
        }
    }

    fun close() {
        synchronized(this) {
            throwExceptionIfNotInitialized()

            doClose()

            if (this.shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(this.shutdownHook)
                } catch (ignored: IllegalStateException) {
                }
            }

            clear()
        }
    }

    private fun doClose() {
        for (destructor in destructors) {
            try {
                destructor()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun clear() {
        wrappersByName.clear()
        destructors.clear()
        properties.clear()
        shutdownHook = null
    }

    private abstract class InstanceWrapper<out T>(val builder: DifkSupplier<T>) {
        abstract fun instance(): T
    }

    private class SingletonWrapper<T>(builder: DifkSupplier<T>, val lazy: Boolean) : InstanceWrapper<T>(builder) {
        val holder: InstanceWrapper<T>

        override fun instance(): T {
            return holder.instance()
        }

        init {
            this.holder = object : InstanceWrapper<T>(builder) {
                val instance: T by lazy {
                    builder.invoke()
                }

                override fun instance(): T {
                    return instance
                }
            }
        }
    }

    private class PrototypeWrapper<out T>(builder: DifkSupplier<T>) : InstanceWrapper<T>(builder) {
        override fun instance(): T {
            return builder()
        }
    }

    private class ThreadLocalWrapper<T>(builder: DifkSupplier<T>) : InstanceWrapper<T>(builder) {
        val threadLocalBuilder: ThreadLocal<T> = ThreadLocal.withInitial(builder)

        override fun instance(): T {
            return threadLocalBuilder.get()
        }
    }
}
