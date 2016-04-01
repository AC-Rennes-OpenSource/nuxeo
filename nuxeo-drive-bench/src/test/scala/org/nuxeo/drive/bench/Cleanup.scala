package org.nuxeo.drive.bench

import io.gatling.core.Predef._
import io.gatling.core.config.GatlingFiles
import io.gatling.http.Predef._

import scala.io.Source

object Cleanup {

  def run = (userCount: Integer) => {
    feed(Feeders.admins)
      .exec(Actions.deleteFileDocumentAsAdmin(Constants.GAT_WS_PATH))
      .repeat(userCount.intValue(), "count") {
      feed(Feeders.usersQueue)
        .exec(Actions.deleteUser())
    }.exec(Actions.deleteGroup(Constants.GAT_GROUP_NAME))
  }
}

class CleanupSimulation extends Simulation {

  val httpProtocol = http
    .baseURL(Parameters.getBaseUrl())
    .disableWarmUp
    .acceptEncodingHeader("gzip, deflate")
    .connection("keep-alive")

  val userCount = Source.fromFile(GatlingFiles.dataDirectory + "/users.csv").getLines.size - 1
  val scn = scenario("Cleanup").exec(Cleanup.run(userCount))

  Feeders.clearTokens()
  setUp(scn.inject(atOnceUsers(1))).protocols(httpProtocol)
    .assertions(global.successfulRequests.percent.is(100))
}
