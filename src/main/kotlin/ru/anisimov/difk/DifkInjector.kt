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

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.KClass
import kotlin.reflect.full.cast

typealias DifkSupplier<T> = () -> T
typealias DifkDestructor = () -> Unit

object DifkInjector {
    val shutdownLock = Any()
    var shutdownHook: Thread? = null

    val buildersByName: MutableMap<String, DifkSupplier<Any>> = HashMap()
    val buildersByClass: MutableMap<KClass<*>, MutableList<DifkSupplier<Any>>> = HashMap()
    val destructors: MutableList<DifkDestructor> = ArrayList()
    val properties = Properties()


    fun <T : Any> addSingleton(name: String, builder: DifkSupplier<T>): T {
        val instance = builder()
        addInstance(name, { instance }, instance)
        return instance
    }

    fun <T : Any> addPrototype(name: String, builder: DifkSupplier<T>): T {
        val instance = builder()
        addInstance(name, builder, instance)
        return instance
    }

    fun <T : Any> addThreadLocal(name: String, builder: DifkSupplier<T>): T {
        val instance = ThreadLocal.withInitial(builder)
        addInstance(name, { instance.get() }, instance)
        return instance.get()
    }

    fun addDestructor(destructor: DifkDestructor) {
        destructors.add(destructor)
    }

    fun <T> getInstance(name: String): T {
        return buildersByName[name]?.invoke() as T
    }

    fun <T : Any> getInstance(name: String, clazz: KClass<T>): T? {
        return buildersByName[name]?.invoke()
                ?.let { clazz.cast(it) }
    }

    fun <T : Any> getInstances(clazz: KClass<T>): List<T> {
        return buildersByClass.getOrDefault(clazz, mutableListOf<DifkSupplier<Any>>())
                .map { clazz.cast(it()) }
                .toList()
    }

    fun addPropertiesFromClasspath(fileName: String) {
        properties.load(BufferedInputStream(this.javaClass.classLoader.getResourceAsStream(fileName)))
    }

    fun addPropertiesFromFile(fileName: String) {
        properties.load(BufferedInputStream(FileInputStream(fileName)))
    }

    fun getProperty(name: String): String? {
        return properties.getProperty(name)
    }

    fun getProperty(name: String, default: String): String {
        return properties.getProperty(name, default)
    }

    fun setProperty(name: String, value: String) {
        properties.setProperty(name, value)
    }

    fun registerShutdownHook() {
        if (shutdownHook == null) {
            shutdownHook = object : Thread() {
                override fun run() {
                    synchronized(shutdownLock) {
                        doClose()
                    }
                }
            }
            Runtime.getRuntime().addShutdownHook(this.shutdownHook)
        }
    }

    fun close() {
        synchronized(shutdownLock) {
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
        buildersByName.clear()
        buildersByClass.clear()
        destructors.clear()
        properties.clear()
        shutdownHook = null
    }

    private fun <T : Any> addInstance(name: String, builder: DifkSupplier<T>, instance: T) {
        buildersByName.put(name, builder)
        buildersByClass.compute(instance::class) { _, v ->
            if (v == null)
                mutableListOf(builder)
            else {
                v.add(builder)
                v
            }}
    }
}
