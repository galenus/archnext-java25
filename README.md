This repository contains the resources related to my "Java turns 25 - how is it faring and what is yet to come" talk at [CodeValue's](https://codevalue.net) [Architecture Next 2020](https://archnext.codevalue.com) conference.

### Links related to the technologies mentioned in the talk

Microservices:
- [Spring Boot](https://spring.io/projects/spring-boot) and its [Initializr](https://start.spring.io/) tool
- Oracle's [Helidon](https://helidon.io) framework
- [Ktor](https://ktor.io/) - framework for Kotlin
- [Micronaut](https://micronaut.io/) by OCI
- [Quarkus](https://quarkus.io/) from RedHat

Big Data:
- [Hadoop](https://hadoop.apache.org/)
- [Spark](https://spark.apache.org/)
- [Storm](https://storm.apache.org/)

Reactive frameworks:
- [Akka](https://akka.io/) - **the** actors implementation for JVM
- [Vert.x](https://vertx.io/) - reactive framework supporting multiple languages

UI:
- [JavaFX](https://openjfx.io/) - still alive and evolving
- [Gluon](https://gluonhq.com/) - commercial multi-platform framework using JavaFX

Languages:
- [Java syntax evolution](https://docs.oracle.com/en/java/javase/14/language/java-language-changes.html)
- [Groovy](https://groovy-lang.org/)
- [Scala](https://www.scala-lang.org/)
- [Kotlin](https://kotlinlang.org/)

Innovative projects:
- [Project Amber](https://openjdk.java.net/projects/amber/)
- [Project Valhalla](https://openjdk.java.net/projects/valhalla/)
- [GraalVM official site](https://www.graalvm.org/) and [its native image documentation](https://www.graalvm.org/docs/Native-Image/user/README)
- [TornadoVM](https://github.com/beehive-lab/TornadoVM/blob/master/assembly/src/docs/14_FAQ.md) - executing programs on CPU, GPU, FPGA

### Source code
The source code in this repository contains a demo Telegram trivia bot implemented in various JVM-based languages.

Each implementation is in a separate project subfolder:
- Java6 - the 'standard' Java 1.6 (JDK 8)
- JavaNext - Java with preview features enabled (v14 preview at the time of creation, JDK 14)
- Groovy - the same as Java* projects, just in Groovy (JDK 8)
- Scala - same functionality, Scala on Akka (JDK 14)
- Kotlin - same functionality, Kotlin, supports GraalVM native image output (uses different 3rd party libraries to make supporting the native version easier, GraalVM JDK 11)
 
