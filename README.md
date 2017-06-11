DIFK (Dependency Injection For Kotlin)
====

Latest release: [0.1](/releases/difk-0.1.jar)

CI: [![Build Status](https://travis-ci.org/valeter/difk.svg?branch=master)](https://travis-ci.org/valeter/difk)

Email: [ivananisimov2010@gmail.com](mailto:ivananisimov2010@gmail.com)

License: [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0)


Simple, lightweight and functional IoC framework for Kotlin

- Tired of long XML/JSON gibberish configs?

- Fed up with reflection/byte code magic?

- Want super simple but still functional dependency injection?

Choose DIFK


**Example**
```
import DifkInjector
import DifkInjector.addDestructor
import DifkInjector.addPropertiesFromClasspath
import DifkInjector.addSingleton
import DifkInjector.getProperty
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

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
    val difkService: Service = getInstance("service")
    assertTrue(service === difkService)
    assertEquals("my_url", difkService.dao.dataSource.url)
    assertEquals("my_class", difkService.dao.dataSource.driverClassName)
    assertEquals("my_user", difkService.dao.dataSource.username)
    assertEquals("my_password", difkService.dao.dataSource.password)
    
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
```

**Features**

- 3 types of instances supported: singletons (single instance), prototypes (new instance for every call) and thread locals (new instance for every thread)
- property management support (`addPropertiesFromFile`, `addPropertiesFromClasspath`)
- init and destroy methods support (look at example)
- simple explicit configuration with kotlin code
- preserving your favourite kotlin style
- minimum external dependencies (look at build.gradle)
- no reflection/byte code magic (you're fully in control of your code)
- self-explaining API

**Useful tips**
 
- Use `registerShutdownHook` method to finish your application gracefully (invoke all destroyers) on jvm shutdown 
