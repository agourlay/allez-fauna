lazy val compilerOptions = Seq(
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-explaintypes",                     // Explain type errors in more detail.
  "-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
  "-language:experimental.macros",     // Allow macro definition (besides implementation and application)
  "-language:higherKinds",             // Allow higher-kinded types
  "-language:implicitConversions",     // Allow definition of implicit functions called views
  "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
  "-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
  "-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
  "-Xlint:constant",                   // Evaluation of a constant arithmetic expression results in an error.
  "-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
  "-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
  "-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
  "-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
  "-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
  "-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
  "-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
  "-Xlint:option-implicit",            // Option.apply used implicit view.
  "-Xlint:package-object-classes",     // Class or object defined in package object. (got a macro there)
  "-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
  "-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
  "-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
  "-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
  "-Ywarn-dead-code",                  // Warn when dead code is identified.
  "-Ywarn-extra-implicit",             // Warn when more than one implicit parameter section is defined.
  "-Ywarn-numeric-widen",              // Warn when numerics are widened.
  "-Ywarn-unused",
  "-Ywarn-unused:implicits",           // Warn if an implicit parameter is unused.
  "-Ywarn-unused:imports",             // Warn if an import selector is not referenced.
  "-Ywarn-unused:locals",              // Warn if a local definition is unused.
  "-Ywarn-unused:params",              // Warn if a value parameter is unused.
  "-Ywarn-unused:patvars",             // Warn if a variable bound in a pattern is unused.
  "-Ywarn-unused:privates",            // Warn if a private member is unused.
  "-Ywarn-value-discard",              // Warn when non-Unit expression results are unused.
  "-Ypartial-unification"              // Needed for advanced http4s things
)

lazy val commonSettings = Seq(
  description := "An API for climbers",
  scalaVersion := "2.12.13",
  Test / fork := true,
  IntegrationTest / parallelExecution  := false,
  scalacOptions ++= compilerOptions,
  resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
)

lazy val allezFauna =
  project
    .in(file("."))
    .aggregate(server, integrationTesting)
    .settings(commonSettings)

lazy val server =
  project
    .in(file("./server"))
    .settings(commonSettings)
     .settings(
        name := "server",
        Test / testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-verbosity", "1"),
        testFrameworks += new TestFramework("utest.runner.Framework"),
        libraryDependencies ++= Seq(
           library.http4sCirce,
           library.http4sServer,
           library.http4sDsl,
           library.http4sMetrics,
           library.circeCore,
           library.circeGeneric,
           library.circeParser,
           library.circeExtra,
           library.monixExec,
           library.monixReactive,
           library.logback,
           library.faunaDriver,
           library.pureConfig,
        )
     )

lazy val integrationTesting =
  project
    .in(file("./integration-testing"))
    .settings(commonSettings)
    .configs(IntegrationTest)
    .settings(Defaults.itSettings : _*)
    .settings(
      name := "integration-testing",
      testFrameworks += new TestFramework("com.github.agourlay.cornichon.framework.CornichonFramework"),
      libraryDependencies ++= Seq(
        library.cornichon % IntegrationTest
      )
    )

lazy val library =
  new {
    object Version {
      val circe          = "0.13.0"
      val monix          = "3.3.0"
      val http4s         = "0.21.22"
      val cornichon      = "0.19.6"
      val logback        = "1.3.0-alpha5"
      val faunaDriver    = "4.1.0"
      val pureConfig     = "0.14.1"
    }
    val circeCore      = "io.circe"              %% "circe-core"                  % Version.circe
    val circeGeneric   = "io.circe"              %% "circe-generic"               % Version.circe
    val circeParser    = "io.circe"              %% "circe-parser"                % Version.circe
    val circeExtra     = "io.circe"              %% "circe-generic-extras"        % Version.circe
    val monixExec      = "io.monix"              %% "monix-execution"             % Version.monix
    val monixReactive  = "io.monix"              %% "monix-reactive"              % Version.monix
    val http4sServer   = "org.http4s"            %% "http4s-blaze-server"         % Version.http4s
    val http4sCirce    = "org.http4s"            %% "http4s-circe"                % Version.http4s
    val http4sDsl      = "org.http4s"            %% "http4s-dsl"                  % Version.http4s
    val http4sMetrics  = "org.http4s"            %% "http4s-prometheus-metrics"   % Version.http4s
    val cornichon      = "com.github.agourlay"   %% "cornichon-test-framework"    % Version.cornichon
    val logback        = "ch.qos.logback"         % "logback-classic"             % Version.logback
    val faunaDriver    = "com.faunadb"           %% "faunadb-scala"               % Version.faunaDriver
    val pureConfig     = "com.github.pureconfig" %% "pureconfig"                  % Version.pureConfig
  }
