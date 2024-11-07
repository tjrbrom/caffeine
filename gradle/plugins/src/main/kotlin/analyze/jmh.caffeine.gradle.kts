/** Java microbenchmark harness: https://github.com/melix/jmh-gradle-plugin */
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.Library
import me.champeau.jmh.JmhBytecodeGeneratorTask
import me.champeau.jmh.JMHTask as JmhTask

plugins {
  idea
  eclipse
  `java-library`
  id("me.champeau.jmh")
  id("io.morethan.jmhreport")
}

configurations.jmh {
  extendsFrom(configurations["testImplementation"])
  exclude(module = "jazzer-junit")
  exclude(module = "slf4j-test")
}

dependencies {
  jmh(libs.bundles.slf4j.nop)
}

jmh {
  jmhVersion = libs.versions.jmh.asProvider()

  benchmarkMode.add("thrpt")
  warmupIterations = 3
  iterations = 3
  timeUnit = "s"

  jvmArgs = defaultJvmArgs()
  failOnError = true
  forceGC = true
  fork = 1

  resultsFile = layout.buildDirectory.file("reports/jmh/results.json")
  resultFormat = "json"

  val includePattern: String? by project
  if (includePattern != null) {
    includes = listOf(includePattern)
  }

  // Benchmark parameters: Separated by '&' for parameter types, and ',' for multiple values
  val params = findProperty("benchmarkParameters") as String?
  if (params != null) {
    for (token in params.split("&")) {
      val param = token.split("=")
      require(param.size == 2) { "Expected two parts but was ${param.size} in $token" }
      benchmarkParameters.put(param[0], objects.listProperty<String>().value(param[1].split(",")))
    }
  }
}

jmhReport {
  jmhResultPath = file(jmh.resultsFile).toString()
  jmhReportOutput = file(layout.buildDirectory.file("reports/jmh")).toString()
}

// Use --rerun for repeated executions
tasks.withType<JmhTask>().configureEach {
  group = "Benchmarks"
  description = "Executes a Java microbenchmark"
  incompatibleWithConfigurationCache()

  inputs.property("benchmarkParameters", jmh.benchmarkParameters)
  inputs.property("includes", includes)
  outputs.file(jmh.resultsFile)
  outputs.cacheIf { true }

  doFirst {
    if (!project.hasProperty("includePattern")) {
      throw GradleException("jmh: includePattern expected")
    }
  }
  finalizedBy(tasks.named("jmhReport"))
}

tasks.withType<JmhBytecodeGeneratorTask>().configureEach {
  javaLauncher = javaToolchains.launcherFor {
    languageVersion = java.toolchain.languageVersion
  }
}

tasks.named("jmhJar").configure {
  incompatibleWithConfigurationCache()
  outputs.cacheIf { true }
}

tasks.named("jmhReport").configure {
  incompatibleWithConfigurationCache()
}

idea.module {
  scopes["PROVIDED"]!!["plus"]!!.add(configurations["jmh"])
}

eclipse.classpath.file.whenMerged {
  if (this is Classpath) {
    entries.removeIf { (it is Library) && (it.moduleVersion?.name == "slf4j-nop") }
  }
}