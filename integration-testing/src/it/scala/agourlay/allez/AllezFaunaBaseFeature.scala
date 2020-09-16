package agourlay.allez

import com.github.agourlay.cornichon.CornichonFeature
import com.github.agourlay.cornichon.core.Step
import java.nio.charset.StandardCharsets
import java.util.Base64

trait AllezFaunaBaseFeature extends CornichonFeature {

  // Our REST API
  override lazy val baseUrl: String = "http://localhost:8080/api"

  // The database is shared by all tests, running sequentially to avoid interference
  override lazy val executeScenariosInParallel = false

  afterEachScenario {
    Attach {
      Given I delete("/gyms")
      And assert status.is(200)
      And I delete("/routes")
      And assert status.is(200)
      And I delete("/users")
      And assert status.is(200)
      And I delete("/suggestedGrades")
      And assert status.is(200)
    }
  }

  def create_a_gym(): Step = Attach {
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
    And I save_body("gym")
  }

  def create_a_route(): Step = Attach {
    Given I post("/routes").withBody(
      """
        {
          "gymId" : "<gym-id>",
          "name" : "<random-alphanum-string>",
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
    And I save_body("route")
  }

  def create_a_user(): Step = Attach {
    Given I save("user-password" -> "<random-alphanum-string>")
    Given I save("user-email" -> "john.<random-alphanum-string>@mail.se")
    Given I post("/users").withBody(
      """
        {
          "firstName" : "John",
          "surname" : "Doe",
          "country" : "Sweden",
          "email" : "<user-email>",
          "password" : "<user-password>"
        }
         """
    )
    Then assert status.is(201)
    And I save_body_path("id" -> "user-id")
    And I save_body("user")
  }

  def login_user(): Step = Attach {
    Given I post("/user-login").withBody(
      """
        {
          "email" : "<user-email>",
          "password" : "<user-password>"
        }
        """
    )
    And assert status.is(200)
    And I save_body("user-secret")
  }

  def prepare_user_header(): Step = Attach {
    And I save("auth-header" -> "<user-secret>")
    // HTTP Basic Auth wants credentials in the form "username:password".
    // Since we’re using a secret that represents both, we just add a colon (:) to the end of the secret.
    And I transform_session("auth-header")(s => Base64.getEncoder.encodeToString(s"$s:".getBytes(StandardCharsets.UTF_8)))
  }

  def create_suggested_grade(routeId: String): Step = Attach {
    When I post("/suggestedGrades").withBody(
      s"""
          {
            "routeId" : "$routeId",
            "grade" : {
              "label" : "6b",
              "scale" : "Fontainebleau"
            },
            "comment": "nasty crux midway"
          }
          """
    )
    Then assert status.is(201)
    And I save_body_path("id" -> "suggested-grade-id")
    And I save_body("suggested-grade")
  }
}
