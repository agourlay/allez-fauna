package agourlay.allez.features

import com.github.agourlay.cornichon.CornichonFeature

class MetricsFeature extends CornichonFeature {

  def feature = Feature("AllezFauna metrics API") {

    Scenario("expose /metrics for Prometheus") {
      Given I get("http://localhost:8080/metrics")
      Then assert status.is(200)
      And assert body.containsString("server_request_count") // app stuff
      And assert body.containsString("jvm_memory_bytes_used") // jvm stuff
    }
  }

}