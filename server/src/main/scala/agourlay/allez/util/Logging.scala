package agourlay.allez.util

import org.slf4j.{ Logger, LoggerFactory }

trait Logging {
  val logger: Logger = LoggerFactory.getLogger(getClass.getName)
}