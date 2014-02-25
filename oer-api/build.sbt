name := "oer-api"

version := "0.1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  cache,
  "org.elasticsearch" % "elasticsearch" % "0.90.7" withSources()
)     

play.Project.playJavaSettings
