package agourlay.allez.features

import agourlay.allez.AllezFaunaBaseFeature
import scala.concurrent.duration._

class PerformanceFeature extends AllezFaunaBaseFeature {

  def feature = Feature("AllezFauna performance API") {

    Scenario("support concurrent pagination of routes") {
      Given I get("/routes")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      Then I create_a_gym()
      Repeat(12, "route-index") {
        Given I post("/routes").withBody(
          """
          {
            "gymId" : "<gym-id>",
            "name" : "Route <route-index>",
            "climbingType" : "TopRope",
            "grade" : {
              "label" : "6a",
              "scale" : "Fontainebleau"
            },
            "profile" : [ "Dynamic" ],
            "gripsColor" : "Blue",
            "setAt" : "2020-09-08T13:51:40.126573Z"
          }
           """
        )
        Then assert status.is(201)
      }

      RepeatConcurrently(times = 500, parallelism = 20, maxTime = 20.seconds) {
        Given I get("/routes")
        Then assert status.is(200)
        And assert body.path("data").asArray.hasSize(12)

        // First page
        Given I get("/routes").withParams("pageSize" -> "5")
        And assert body.path("data").asArray.hasSize(5)
        And assert body.path("data[*].name").asArray.containsExactly("Route 1", "Route 2", "Route 3", "Route 4", "Route 5")
        And assert body.path("before").isNull
        And assert body.path("after").isPresent
        And I save_body_path("after" -> "next-id")

        // Second page
        Given I get("/routes").withParams("pageSize" -> "5", "pageAfter" -> "<next-id>")
        And assert body.path("data").asArray.hasSize(5)
        And assert body.path("data[*].name").asArray.containsExactly("Route 6", "Route 7", "Route 8", "Route 9", "Route 10")
        And assert body.path("before").is("<next-id>")
        And assert body.path("after").isPresent
        And I save_body_path("after" -> "next-id")

        // Third page
        Given I get("/routes").withParams("pageSize" -> "5", "pageAfter" -> "<next-id>")
        And assert body.path("data").asArray.hasSize(2)
        And assert body.path("data[*].name").asArray.containsExactly("Route 11", "Route 12")
        And assert body.path("before").is("<next-id>")
        And assert body.path("after").isNull
      }
    }

    Scenario("support concurrent writes on gyms and routes") {
      Given I get("/gyms")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      Given I get("/routes")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      RepeatConcurrently(times = 500, parallelism = 20, maxTime = 10.seconds){
        Given I create_a_gym()
        And I get("/gyms/<gym-id>")
        Then assert body.is("<gym>")
        And I create_a_route()
        And I get("/routes/<route-id>")
        Then assert body.is("<route>")
      }

      Given I get("/gyms").withParams("pageSize" -> "1000")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(500)

      Given I get("/routes").withParams("pageSize" -> "1000")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(500)
    }

    Scenario("support concurrent writes on users, login and suggested routes") {
      Given I create_a_gym()
      And I create_a_route()
      And I create_a_route()

      RepeatConcurrently(times = 500, parallelism = 20, maxTime = 10.seconds){
        And I create_a_user()
        And I login_user()
        And I prepare_user_header()

        WithHeaders("Authorization" -> "Basic <auth-header>"){
          Then I create_a_suggested_grade("<route-id[0]>")
          Then I create_a_suggested_grade("<route-id[1]>")
        }
      }

      And I get("/routes/<route-id[0]>/suggestedGrades").withParams("pageSize" -> "1000")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(500)

      And I get("/routes/<route-id[1]>/suggestedGrades").withParams("pageSize" -> "1000")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(500)
    }
  }

}
