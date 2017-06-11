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

import DifkInjector
import DifkInjector.addDestructor
import DifkInjector.addPropertiesFromClasspath
import DifkInjector.addPrototype
import DifkInjector.addSingleton
import DifkInjector.addThreadLocal
import DifkInjector.getInstance
import DifkInjector.getProperty
import DifkInjector.setProperty
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Ivan Anisimov
 * *         valter@yandex-team.ru
 * *         11.06.17
 */
class DifkInjectorTest {
    @Before
    fun init() {
        DifkInjector.close()
    }

    @Test
    fun getProperty() {
        addPropertiesFromClasspath("test.properties")
        assertEquals("my_url", getProperty("db.url"))
        setProperty("db.url", "my_url_1")
        assertEquals("my_url_1", getProperty("db.url", "none"))
        setProperty("db.url.2", "my_url_2")
        assertEquals("my_url_1", getProperty("db.url", "none"))
        assertEquals("my_url_2", getProperty("db.url.2", "none"))
    }

    @Test
    fun getInstances() {
        val first = Any()
        val second = Any()
        addSingleton("first", { first })
        addSingleton("second", { second })
        val instances: List<Any> = DifkInjector.getInstances(Any::class)
        assertEquals(2, instances.size)
        assertTrue(first === instances[0])
        assertTrue(second === instances[1])
    }

    @Test
    fun singletonIsAlwaysTheSame() {
        addSingleton("single", { Any() })
        val instance = getInstance("single", Any::class)
        for (i in 99 downTo 0) {
            assertEquals(instance, getInstance("single"))
        }
    }

    @Test
    fun prototypeIsAlwaysNew() {
        addPrototype("proto", { Any() })
        val set: MutableSet<Any> = HashSet()
        for (i in 99 downTo 0) {
            set.add(getInstance("proto"))
        }
        assertEquals(100, set.size)
    }

    @Test
    fun destructorRunOnClose() {
        val destructed = AtomicBoolean(false)
        addDestructor { destructed.set(true) }
        DifkInjector.close()
        assertTrue(destructed.get())
    }

    @Test
    fun registerShutdownHookRegisterHookForDestructors() {
        val destructed = AtomicBoolean(false)
        addDestructor { destructed.set(true) }
        assertNull(DifkInjector.shutdownHook)
        DifkInjector.registerShutdownHook()
        assertNotNull(DifkInjector.shutdownHook)
        assertFalse(destructed.get())
        DifkInjector.shutdownHook!!.start()
        DifkInjector.shutdownHook!!.join()
        assertTrue(destructed.get())
    }

    @Test
    fun registerShutdownHookRegistersInRuntime() {
        DifkInjector.registerShutdownHook()
        assertTrue(Runtime.getRuntime().removeShutdownHook(DifkInjector.shutdownHook))
    }

    @Test
    fun notExistingBeanReturnsNull() {
        assertNull(getInstance("not_existing", this::class))
    }

    @Test
    fun threadLocalIsUniqueForEachThread() {
        val counter = AtomicInteger(-1)
        addThreadLocal("counter", { counter.getAndIncrement() })
        val values = Array(100, { 0 })
        val threads = ArrayList<Thread>()
        (99 downTo 0).mapTo(threads) {
            object : Thread() {
                override fun run() {
                    values[it] = getInstance("counter")
                }
            }
        }
        threads.forEach {
            it.start()
            it.join()
        }
        assertEquals(100, counter.get())
        values.sort()
        for(i in 99 downTo 0) {
            assertEquals(i, values[i])
        }
    }

    @Test
    fun threadLocalIsSingletonInsideThread() {
        val counter = AtomicInteger(-1)
        val value = addThreadLocal("counter", { counter.getAndIncrement() })
        for (i in 99 downTo 0) {
            assertEquals(value, getInstance("counter"))
        }
    }

    @Test
    fun simpleScenario() {
        addPropertiesFromClasspath("test.properties")
        val dataSource = addSingleton("dataSource", { DataSource(
                getProperty("db.url")!!,
                getProperty("db.driver.class.name")!!,
                getProperty("db.username")!!,
                getProperty("db.password")!!
                ) })
        val dao = addSingleton("dao", { Dao(dataSource) })
        val service = addSingleton("service", { val s = Service(dao); s.init(); s })
        addDestructor { service.close() }

        assertFalse(service.closed!!)
        assertNotNull(service.dao.dataSource)
        assertEquals("my_url", service.dao.dataSource.url)
        assertEquals("my_class", service.dao.dataSource.driverClassName)
        assertEquals("my_user", service.dao.dataSource.username)
        assertEquals("my_password", service.dao.dataSource.password)

        DifkInjector.close()
        assertTrue(service.closed!!)
    }

    class DataSource(val url: String, val driverClassName: String, val username: String, val password: String)

    class Dao(var dataSource: DataSource)

    class Service(val dao: Dao) {
        var closed: Boolean? = null

        fun init() {
            closed = false
        }

        fun close() {
            closed = true
        }
    }
}
