
organization in ThisBuild := "com.pellucid"

licenses in ThisBuild += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

version in ThisBuild := "0.6.1"

scalaVersion in ThisBuild := "2.11.4"

crossScalaVersions in ThisBuild := Seq("2.10.4", "2.11.4")

scalacOptions in ThisBuild ++= Seq("-feature", "-deprecation", "-unchecked")

shellPrompt in ThisBuild := CustomShellPrompt.customPrompt

resolvers in ThisBuild ++= Seq(
    "typesafe" at "http://repo.typesafe.com/typesafe/releases",
    "sonatype" at "http://oss.sonatype.org/content/repositories/releases"
  )



lazy val awsWrap = project in file(".")

name := "aws-wrap-kg"

libraryDependencies ++= Seq(
  Dependencies.Compile.awsJavaSDK % "provided",
  Dependencies.Compile.slf4j
)


bintray.Plugin.bintrayPublishSettings

bintray.Keys.bintrayOrganization in bintray.Keys.bintray := Some("pellucid")

bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("aws", "dynamodb", "s3", "ses", "simpledb", "sns", "sqs", "async", "future")



lazy val awsWrapTest = project.
  in(file("integration")).
  configs(IntegrationTest).
  dependsOn(awsWrap)


lazy val scratch = project.
  in(file("scratch")).
  dependsOn(awsWrap)
