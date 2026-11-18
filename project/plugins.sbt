// the JVM for the tests and a host, Scala.js for a Worker
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.4.0")
// a node's runnable jar (`sbt host/assembly`)
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.0")
