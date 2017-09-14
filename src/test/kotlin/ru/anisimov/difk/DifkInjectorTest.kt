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

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Ivan Anisimov
 * *         valter@yandex-team.ru
 * *         11.06.17
 */
class DifkInjectorTest {
    @Test
    fun loadProperties() {
        val injector = DifkInjector()
        injector.loadPropertiesFromClasspath("test.properties")
        injector.init()
        assertEquals("my_url", injector.getProperty("db.url"))
    }

    @Test(expected = RuntimeException::class)
    fun loadPropertiesAfterInit() {
        val injector = DifkInjector()
        injector.init()
        injector.loadPropertiesFromClasspath("test.properties")
    }

    @Test
    fun setProperty() {
        val injector = DifkInjector()
        injector.setProperty("db.url.2", "my_url_2")
        injector.init()
        assertEquals("my_url_2", injector.getProperty("db.url.2"))
    }

    @Test(expected = RuntimeException::class)
    fun setPropertyAfterInit() {
        val injector = DifkInjector()
        injector.init()
        injector.setProperty("my.property", "my_value")
    }

    @Test
    fun getPropertyOrNull() {
        val injector = DifkInjector()
        injector.loadPropertiesFromClasspath("test.properties")
        injector.init()
        assertEquals(null, injector.getPropertyOrNull("db.url.2"))
    }

    /*@Test
    fun getInstances() {
        val injector = DifkInjector()
        val first = Any()
        val second = Any()
        injector.addSingleton("first", { first })
        injector.addSingleton("second", { second })
        injector.init()
        val instances: List<Any> = injector.getInstances(Any::class)
        assertEquals(2, instances.size)
        assertTrue(first === instances[0])
        assertTrue(second === instances[1])
    }*/

    @Test
    fun singletonIsAlwaysTheSame() {
        val injector = DifkInjector()
        injector.addSingleton("single", { Any() })
        injector.init()
        val instance: Any = injector.getInstance("single")
        for (i in 99 downTo 0) {
            assertEquals(instance, injector.getInstance("single"))
        }
    }

    @Test
    fun prototypeIsAlwaysNew() {
        val injector = DifkInjector()
        injector.addPrototype("proto", { Any() })
        injector.init()
        val set: MutableSet<Any> = HashSet()
        for (i in 99 downTo 0) {
            set.add(injector.getInstance("proto"))
        }
        assertEquals(100, set.size)
    }

    @Test
    fun destructorRunOnClose() {
        val injector = DifkInjector()
        val destructed = AtomicBoolean(false)
        injector.addDestructor { destructed.set(true) }
        injector.init()
        injector.close()
        assertTrue(destructed.get())
    }

    @Test
    fun registerShutdownHookRegisterHookForDestructors() {
        val injector = DifkInjector()
        val destructed = AtomicBoolean(false)
        injector.addDestructor { destructed.set(true) }
        assertNull(injector.shutdownHook)
        injector.registerShutdownHook()
        assertNotNull(injector.shutdownHook)
        assertFalse(destructed.get())
        injector.shutdownHook!!.start()
        injector.shutdownHook!!.join()
        assertTrue(destructed.get())
    }

    @Test
    fun registerShutdownHookRegistersInRuntime() {
        val injector = DifkInjector()
        injector.registerShutdownHook()
        assertTrue(Runtime.getRuntime().removeShutdownHook(injector.shutdownHook))
    }

    @Test
    fun notExistingBeanReturnsNull() {
        val injector = DifkInjector()
        injector.init()
        assertNull(injector.getInstanceOrNull("not_existing"))
    }

    @Test(expected = RuntimeException::class)
    fun notExistingBeanThrowsException() {
        val injector = DifkInjector()
        injector.init()
        assertNull(injector.getInstance("not_existing"))
    }

    @Test
    fun threadLocalIsUniqueForEachThread() {
        val injector = DifkInjector()
        val counter = AtomicInteger(-1)
        injector.addThreadLocal("counter", { counter.incrementAndGet() })
        injector.init()

        val values = Array(100, { 0 })
        val threads = ArrayList<Thread>()
        (99 downTo 0).mapTo(threads) {
            object : Thread() {
                override fun run() {
                    values[it] = injector.getInstance("counter")
                }
            }
        }
        threads.forEach {
            it.start()
            it.join()
        }
        assertEquals(99, counter.get())
        values.sort()
        for(i in 99 downTo 0) {
            assertEquals(i, values[i])
        }
    }

    @Test
    fun threadLocalIsSingletonInsideThread() {
        val injector = DifkInjector()
        val counter = AtomicInteger(-1)
        injector.addThreadLocal("counter", { counter.incrementAndGet() })
        injector.init()
        for (i in 99 downTo 0) {
            assertEquals(0, injector.getInstance("counter"))
        }
    }

    @Test
    fun simpleScenario() {
        val injector = DifkInjector()
        injector.loadPropertiesFromClasspath("test.properties")
        injector.addSingleton("dataSource", { DataSource(
                injector.getProperty("db.url"),
                injector.getProperty("db.driver.class.name"),
                injector.getProperty("db.username"),
                injector.getProperty("db.password")
        ) })
        injector.addSingleton("dao", { Dao(injector.getInstance("dataSource")) })
        injector.addSingleton("service", { val s = Service(injector.getInstance("dao")); s.init(); s })
        injector.addDestructor {
            val service: Service = injector.getInstance("service")
            service.close()
        }
        injector.init()

        val service: Service = injector.getInstance("service")
        assertFalse(service.closed!!)
        val difkService: Service = injector.getInstance("service")
        assertTrue(service === difkService)
        assertEquals("my_url", difkService.dao.dataSource.url)
        assertEquals("my_class", difkService.dao.dataSource.driverClassName)
        assertEquals("my_user", difkService.dao.dataSource.username)
        assertEquals("my_password", difkService.dao.dataSource.password)

        injector.close()
        assertTrue(service.closed!!)
    }

    @Test
    fun sortRelations() {
        val injector = DifkInjector()
        val graph: MutableMap<String, MutableSet<String>> = HashMap()
        graph.put("1", HashSet(listOf("2", "3")))
        graph.put("2", HashSet(listOf("4", "5")))
        graph.put("3", HashSet(listOf("6", "7")))
        assertEquals("1234567".split("").subList(1, 8), injector.sortRelations(graph))
    }

    @Test(expected = RuntimeException::class)
    fun sortRelationsWithCycle() {
        val injector = DifkInjector()
        val graph: MutableMap<String, MutableSet<String>> = HashMap()
        graph.put("1", HashSet(listOf("2", "3")))
        graph.put("2", HashSet(listOf("4", "5")))
        graph.put("3", HashSet(listOf("6", "1")))
        injector.sortRelations(graph)
    }

    class DataSource(val url: String, val driverClassName: String, val username: String, val password: String)

    class Dao(val dataSource: DataSource)

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
