package agourlay.allez.features

import agourlay.allez.AllezFaunaBaseFeature
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.concurrent.duration._

class ApiFeature extends AllezFaunaBaseFeature {

  def feature = Feature("AllezFauna API") {

    Scenario("create, get & delete a gym") {
      Given I post("/gyms").withBody(
        """
        {
          "name" : "MagicMountain",
          "address" : "Böttgerstrasse 20, Berlin, DE 13357",
          "website" : "https://www.magicmountain.de/"
        }
         """
      )
      Then assert status.is(201)
      And assert body.is(
        """
          {
            "id" : *any-string*,
            "name" : "MagicMountain",
            "address" : "Böttgerstrasse 20, Berlin, DE 13357",
            "website" : "https://www.magicmountain.de/",
            "createdAt" : *any-date-time*
          }
          """
      )
      And I save_body_path("id" -> "gym-id")

      And I get("/gyms/<gym-id>")
      Then assert status.is(200)
      Then assert body.path("id").is("<gym-id>")

      And I delete("/gyms/<gym-id>")
      Then assert status.is(200)

      And I get("/gyms/<gym-id>")
      Then assert status.is(404)
    }

    Scenario("update a gym (does not update createdAt)") {
      Given I post("/gyms").withBody(
        """
        {
          "name" : "Mountain",
          "address" : "Böttgerstrasse 20, DE 13357",
          "website" : "http://www.magicmountain.de/"
        }
        """
      )
      Then assert status.is(201)
      And I save_body_path("id" -> "gym-id")
      And I save_body_path("createdAt" -> "created-at") // make sure it is not updated

      Then I post("/gyms/<gym-id>").withBody(
        """
        {
          "name" : "MagicMountain",
          "address" : "Böttgerstrasse 20, Berlin, DE 13357",
          "website" : "https://www.magicmountain.de/"
        }
        """
      )
      Then assert status.is(200)
      And assert body.is(
        """
          {
             "id" : "<gym-id>",
             "name" : "MagicMountain",
             "address" : "Böttgerstrasse 20, Berlin, DE 13357",
             "website" : "https://www.magicmountain.de/",
             "createdAt" : "<created-at>"
          }
          """
      )
    }

    Scenario("retrieve all gyms") {
      Given I get("/gyms")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      Then I create_a_gym()
      Given I get("/gyms")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
    }

    Scenario("retrieve and paginate all gyms") {
      Given I get("/gyms")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      Repeat(12, "gym-index") {
        Given I post("/gyms").withBody(
          """
          {
            "name" : "Gym <gym-index>",
            "address" : "an address"
          }
           """
        )
        Then assert status.is(201)
      }
      Given I get("/gyms")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(12)

      // First page
      Given I get("/gyms").withParams("pageSize" -> "5")
      And assert body.path("data").asArray.hasSize(5)
      And assert body.path("data[*].name").asArray.containsExactly("Gym 1", "Gym 2", "Gym 3", "Gym 4", "Gym 5")
      And assert body.path("before").isNull
      And assert body.path("after").isPresent
      And I save_body_path("after" -> "next-id")

      // Second page
      Given I get("/gyms").withParams("pageSize" -> "5", "pageAfter" -> "<next-id>")
      And assert body.path("data").asArray.hasSize(5)
      And assert body.path("data[*].name").asArray.containsExactly("Gym 6", "Gym 7", "Gym 8", "Gym 9", "Gym 10")
      And assert body.path("before").is("<next-id>")
      And assert body.path("after").isPresent
      And I save_body_path("after" -> "next-id")

      // Third page
      Given I get("/gyms").withParams("pageSize" -> "5", "pageAfter" -> "<next-id>")
      And assert body.path("data").asArray.hasSize(2)
      And assert body.path("data[*].name").asArray.containsExactly("Gym 11", "Gym 12")
      And assert body.path("before").is("<next-id>")
      And assert body.path("after").isNull
    }

    Scenario("retrieve single gym by id & name") {
      Given I get("/gyms")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      Given I post("/gyms").withBody(
        """
        {
          "name" : "MagicMountain",
          "address" : "Böttgerstrasse 20, Berlin, DE 13357",
          "website" : "https://www.magicmountain.de/"
        }
         """
      )
      Then assert status.is(201)
      And I save_body_path("id" -> "gym-id")

      Given I get("/gyms")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)

      And I get("/gyms/<gym-id>")
      Then assert status.is(200)
      And I save_body_path("name" -> "gym-name")

      And I get("/gyms").withParams("name" -> "<gym-name>")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
      And I body.path("data[0].id").is("<gym-id>")

      // partial match search
      And I get("/gyms").withParams("name" -> "magic")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(0)

      And I get("/gyms?partialMatch").withParams("name" -> "magic")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
      And I body.path("data[0].id").is("<gym-id>")
    }

    Scenario("can't delete a gym if there are routes linked to it") {
      Given I create_a_gym()
      And I create_a_route()
      Then I delete("/gyms/<gym-id>")
      Then assert status.is(400)
      And assert body.is("transaction aborted: gymId <gym-id> can't be deleted as it has routes attached")

      // delete route first
      Then I get("/routes/<route-id>")
      Then assert status.is(200)

      Then I get("/gyms/<gym-id>")
      Then assert status.is(200)
    }

    Scenario("retrieve all routes") {
      Given I get("/routes")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      Then I create_a_gym()
      And I create_a_route()
      Given I get("/routes")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
    }

    Scenario("retrieve and paginate all routes") {
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

    Scenario("retrieve single route by id & name") {
      Given I get("/routes")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      Given I create_a_gym()
      Given I post("/routes").withBody(
        """
        {
          "gymId" : "<gym-id>",
          "name" : "immer bereit!",
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
      And I save_body_path("id" -> "route-id")

      Given I get("/routes")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)

      And I get("/routes/<route-id>")
      Then assert status.is(200)
      And I save_body_path("name" -> "route-name")

      And I get("/routes").withParams("name" -> "<route-name>")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
      And I body.path("data[0].id").is("<route-id>")

      // partial match search
      And I get("/routes").withParams("name" -> "immer")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(0)

      And I get("/routes?partialMatch").withParams("name" -> "immer")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
      And I body.path("data[0].id").is("<route-id>")
    }

    Scenario("retrieve all routes for a given gym") {
      Given I create_a_gym()
      And I get("/gyms/<gym-id>/routes")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      And I create_a_route()
      And I get("/gyms/<gym-id>/routes")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
      And assert body.path("data").asArray.containsExactly("<route>")
    }

    Scenario("retrieve all routes for a given gym (filtering by profile, climbingType & grade)") {
      Given I create_a_gym()
      And I get("/gyms/<gym-id>/routes")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      Given I post("/routes").withBody(
        """
        {
          "gymId" : "<gym-id>",
          "name" : "immer bereit!",
          "climbingType" : "TopRope",
          "grade" : {
            "label" : "6a",
            "scale" : "Fontainebleau"
          },
          "profile" : [ "Dynamic", "Reach" ],
          "gripsColor" : "Blue",
          "setAt" : "2020-09-08T13:51:40.126573Z"
        }
         """
      )
      Then assert status.is(201)
      And I save_body_path("id" -> "route-id")
      And I save_body("route")

      Given I post("/routes").withBody(
        """
        {
          "gymId" : "<gym-id>",
          "name" : "tchuss!",
          "climbingType" : "Lead",
          "grade" : {
            "label" : "6b",
            "scale" : "Fontainebleau"
          },
          "profile" : [ "Dynamic", "Overhang" ],
          "gripsColor" : "Blue",
          "setAt" : "2020-09-08T13:51:40.126573Z"
        }
         """
      )
      Then assert status.is(201)
      And I save_body("route")

      // ALL
      And I get("/gyms/<gym-id>/routes")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(2)

      // Only Dynamic
      And I get("/gyms/<gym-id>/routes?profile=Dynamic")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(2)
      And assert body.path("data").asArray.containsExactly("<route[0]>", "<route[1]>")

      // Only Overhang
      And I get("/gyms/<gym-id>/routes?profile=Overhang")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
      And assert body.path("data").asArray.containsExactly("<route[1]>")

      // Only Crimp
      And I get("/gyms/<gym-id>/routes?profile=Crimp")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(0)

      // Only Lead
      And I get("/gyms/<gym-id>/routes?type=Lead")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
      And assert body.path("data").asArray.containsExactly("<route[1]>")

      // Only TopRope
      And I get("/gyms/<gym-id>/routes?type=TopRope")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
      And assert body.path("data").asArray.containsExactly("<route[0]>")

      // Only Bouldering?
      And I get("/gyms/<gym-id>/routes?type=Bouldering")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      // Only 6a grade
      And I get("/gyms/<gym-id>/routes?grade=6a")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
      And assert body.path("data").asArray.containsExactly("<route[0]>")

      // Only 6b grade
      And I get("/gyms/<gym-id>/routes?grade=6b")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
      And assert body.path("data").asArray.containsExactly("<route[1]>")

      // Only 6c grade
      And I get("/gyms/<gym-id>/routes?grade=6c")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      // Only Lead with crimp
      And I get("/gyms/<gym-id>/routes?type=Lead&profile=Crimp")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      // Only Lead with overhang in 6b
      And I get("/gyms/<gym-id>/routes?type=Lead&profile=Overhang&grade=6b")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
      And assert body.path("data").asArray.containsExactly("<route[1]>")

      // Only Lead with overhang in 6a
      And I get("/gyms/<gym-id>/routes?type=Lead&profile=Overhang&grade=6a")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty
    }

    Scenario("create, get & delete a route") {
      Given I post("/gyms").withBody(
        """
        {
          "name" : "MagicMountain",
          "address" : "Böttgerstrasse 20, Berlin, DE 13357",
          "website" : "https://www.magicmountain.de/"
        }
         """
      )
      Then assert status.is(201)
      And I save_body_path("id" -> "gym-id")

      Given I post("/routes").withBody(
        """
        {
          "gymId" : "<gym-id>",
          "name" : "immer bereit!",
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
      And assert body.is(
        """
          {
            "id" : *any-string*,
            "gymId" : "<gym-id>",
            "name" : "immer bereit!",
            "climbingType" : "TopRope",
            "grade" : {
              "label" : "6a",
              "scale" : "Fontainebleau"
            },
            "profile" : [ "Dynamic" ],
            "gripsColor" : "Blue",
            "setAt" : "2020-09-08T13:51:40.126573Z",
            "closedAt" : null,
            "createdAt" : *any-date-time*
          }
          """
      )

      And I save_body_path("id" -> "route-id")

      And I get("/routes/<route-id>")
      Then assert status.is(200)
      Then assert body.path("id").is("<route-id>")

      And I delete("/routes/<route-id>")
      Then assert status.is(200)

      And I get("/routes/<route-id>")
      Then assert status.is(404)
    }

    Scenario("create a route on an gym which does not exist") {
      Given I save("random-gym-id" -> "<random-positive-integer>")
      Given I post("/routes").withBody(
        """
        {
          "gymId" : "<random-gym-id>",
          "name" : "name",
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
      Then assert status.is(400)
      And assert body.is("transaction aborted: gymId <random-gym-id> does not exist")
    }

    Scenario("update a route (does not update createdAt)") {
      Given I create_a_gym()
      Given I post("/routes").withBody(
        """
        {
          "gymId" : "<gym-id>",
          "name" : "nicht bereit :(",
          "climbingType" : "Bouldering",
          "grade" : {
            "label" : "5c",
            "scale" : "Fontainebleau"
          },
          "profile" : [ "Dynamic" ],
          "gripsColor" : "Blue",
          "setAt" : "2020-09-18T13:55:40.126573Z"
        }
         """
      )
      Then assert status.is(201)
      And I save_body_path("id" -> "route-id")
      And I save_body_path("createdAt" -> "created-at")

      Given I post("/routes/<route-id>").withBody(
        """
        {
          "gymId" : "<gym-id>",
          "name" : "immer bereit!",
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
      Then assert status.is(200)
      And assert body.is(
        """
          {
            "id" : "<route-id>",
            "gymId" : "<gym-id>",
            "name" : "immer bereit!",
            "climbingType" : "TopRope",
            "grade" : {
              "label" : "6a",
              "scale" : "Fontainebleau"
            },
            "profile" : [ "Dynamic" ],
            "gripsColor" : "Blue",
            "setAt" : "2020-09-08T13:51:40.126573Z",
            "closedAt" : null,
            "createdAt" : "<created-at>"
          }
          """
      )
    }

    Scenario("support concurrent writes on gyms and routes") {
      Given I get("/gyms")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      Given I get("/routes")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      RepeatConcurrently(times = 100, parallelism = 10, maxTime = 5.seconds){
        Given I create_a_gym()
        And I get("/gyms/<gym-id>")
        Then assert body.is("<gym>")
        And I create_a_route()
        And I get("/routes/<route-id>")
        Then assert body.is("<route>")
      }

      Given I get("/gyms").withParams("pageSize" -> "500")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(100)

      Given I get("/routes").withParams("pageSize" -> "500")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(100)

    }

    Scenario("create, get & delete a user") {
      Given I post("/users").withBody(
        """
        {
          "firstName" : "John",
          "surname" : "Doe",
          "country" : "Sweden",
          "email" : "john.first.doe@mail.se",
          "password" : "johnpassword"
        }
         """
      )
      Then assert status.is(201)
      And assert body.path("password").isAbsent
      And assert body.is(
        """
          {
            "id" : *any-string*,
            "firstName" : "John",
            "surname" : "Doe",
            "country" : "Sweden",
            "email" : "john.first.doe@mail.se",
            "createdAt" : *any-date-time*
          }
          """
      )

      And I save_body_path("id" -> "user-id")
      And I save_body("user")

      Given I get("/users/<user-id>")
      Then assert status.is(200)
      Then assert body.is("<user>")

      Given I delete("/users/<user-id>")
      Then assert status.is(200)

      Given I get("/users/<user-id>")
      Then assert status.is(404)
    }

    Scenario("can't create two users with the same email") {
      Given I post("/users").withBody(
        """
        {
          "firstName" : "John",
          "surname" : "Doe",
          "country" : "Sweden",
          "email" : "another.john.doe@mail.se",
          "password" : "johnpassword"
        }
         """
      )
      Then assert status.is(201)
      Given I post("/users").withBody(
        """
        {
          "firstName" : "Another John",
          "surname" : "Doe",
          "country" : "Sweden",
          "email" : "another.john.doe@mail.se",
          "password" : "johnpassword"
        }
         """
      )
      Then assert status.is(400)
      Then assert body.is("A user already exists with this email")
    }

    Scenario("user login") {
      Given I create_a_user()
      Given I post("/user-login").withBody(
        """
        {
          "email" : "<user-email>",
          "password" : "<user-password>"
        }
        """
      )
      And assert status.is(200)
    }

    Scenario("user login failures") {
      Given I create_a_user()
      Given I post("/user-login").withBody(
        """
        {
          "email" : "<user-email>",
          "password" : "wrongpassword"
        }
        """
      )
      And assert status.is(400)
      Then assert body.is("authentication failed: The document was not found or provided password was incorrect.")

      Given I post("/user-login").withBody(
        """
        {
          "email" : "wrongemail",
          "password" : "<user-password>"
        }
        """
      )
      And assert status.is(400)
      Then assert body.is("authentication failed: The document was not found or provided password was incorrect.")
    }

    Scenario("create suggested grade requires authentication") {
      Given I create_a_gym()
      And I create_a_route()
      And I create_a_user()
      And I login_user()

      When I post("/suggestedGrades").withBody(
        """
        {
          "routeId" : "<route-id>",
          "grade" : {
            "label" : "6b",
            "scale" : "Fontainebleau"
          },
          "comment": "nasty crux midway"
        }
        """
      )
      Then assert status.is(401)
      And I body.is("")
      And assert headers.contain("WWW-Authenticate" -> """Basic realm="secure site"""")

      And I save("auth-header" -> "<user-secret>")
      // HTTP Basic Auth wants credentials in the form "username:password".
      // Since we’re using a secret that represents both, we just add a colon (:) to the end of the secret.
      And I transform_session("auth-header")(s => Base64.getEncoder.encodeToString(s"$s:".getBytes(StandardCharsets.UTF_8)))

      WithHeaders("Authorization" -> "Basic <auth-header>"){
        When I post("/suggestedGrades").withBody(
          """
          {
            "routeId" : "<route-id>",
            "grade" : {
              "label" : "6b",
              "scale" : "Fontainebleau"
            },
            "comment": "nasty crux midway"
          }
          """
        )
        Then assert status.is(201)
        And assert body.is(
          """
          {
            "id" : *any-string*,
            "routeId" : "<route-id>",
            "userId" : "<user-id>",
            "grade" : {
              "label" : "6b",
              "scale" : "Fontainebleau"
            },
            "comment": "nasty crux midway",
            "createdAt" : *any-date-time*
          }
          """
        )
      }
    }

    Scenario("a user can suggest a grade only once per route") {
      Given I create_a_gym()
      And I create_a_route()
      And I create_a_user()
      And I login_user()

      And I prepare_user_header()

      WithHeaders("Authorization" -> "Basic <auth-header>"){
        When I post("/suggestedGrades").withBody(
          """
          {
            "routeId" : "<route-id>",
            "grade" : {
              "label" : "6b",
              "scale" : "Fontainebleau"
            },
            "comment": "nasty crux midway"
          }
          """
        )
        Then assert status.is(201)
        When I post("/suggestedGrades").withBody(
          """
          {
            "routeId" : "<route-id>",
            "grade" : {
              "label" : "6b",
              "scale" : "Fontainebleau"
            },
            "comment": "nasty crux midway"
          }
          """
        )
        Then assert status.is(400)
        Then assert body.is("The user already suggested a rating for this route")
      }
    }

    Scenario("a user can't add a suggested grade on a route that does not exist") {
      Given I create_a_gym()
      And I create_a_route()
      And I create_a_user()
      And I login_user()

      And I prepare_user_header()

      WithHeaders("Authorization" -> "Basic <auth-header>"){
        When I post("/suggestedGrades").withBody(
          """
          {
            "routeId" : "123",
            "grade" : {
              "label" : "6b",
              "scale" : "Fontainebleau"
            },
            "comment": "nasty crux midway"
          }
          """
        )
        Then assert status.is(400)
        Then assert body.is("transaction aborted: routeId 123 does not exist")
      }
    }

    Scenario("view user's suggested grades") {
      Given I create_a_gym()
      And I create_a_route()
      And I create_a_route()
      And I create_a_user()
      And I login_user()

      And I prepare_user_header()

      And I get("/users/<user-id>/suggestedGrades")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      WithHeaders("Authorization" -> "Basic <auth-header>"){
        Given I create_suggested_grade("<route-id[0]>")
        Then I create_suggested_grade("<route-id[1]>")
      }

      And I get("/users/<user-id>/suggestedGrades")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(2)
      And assert body.path("data").asArray.containsExactly("<suggested-grade[0]>", "<suggested-grade[1]>")
    }

    Scenario("view route's suggested grades") {
      Given I create_a_gym()
      And I create_a_route()
      And I create_a_route()
      And I create_a_user()
      And I login_user()

      And I prepare_user_header()

      And I get("/routes/<route-id[0]>/suggestedGrades")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      And I get("/routes/<route-id[1]>/suggestedGrades")
      Then assert status.is(200)
      And assert body.path("data").asArray.isEmpty

      WithHeaders("Authorization" -> "Basic <auth-header>"){
        Given I create_suggested_grade("<route-id[0]>")
        Then I create_suggested_grade("<route-id[1]>")
      }

      And I get("/routes/<route-id[0]>/suggestedGrades")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
      And assert body.path("data").asArray.containsExactly("<suggested-grade[0]>")

      And I get("/routes/<route-id[1]>/suggestedGrades")
      Then assert status.is(200)
      And assert body.path("data").asArray.hasSize(1)
      And assert body.path("data").asArray.containsExactly("<suggested-grade[1]>")
    }
  }
}
