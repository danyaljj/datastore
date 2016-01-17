import Dependencies._

name := "datastore"

libraryDependencies ++= Seq(
  awsJavaSdk exclude("commons-logging", "commons-logging"),
  commonsIO,
//  allenAiCommon,
  allenAiTestkit % "test,it",
  Logging.logbackClassic,
  Logging.logbackCore,
  Logging.slf4jApi,
  "org.slf4j" % "jcl-over-slf4j" % Logging.slf4jVersion,
  "org.allenai.common" % "common-core_2.11" % "2015.04.01-0",
  "org.apache.httpcomponents" % "httpclient" % "4.3.6"
)

fork in IntegrationTest := true
