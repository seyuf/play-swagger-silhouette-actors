package modules

import java.util.UUID

import com.google.inject.AbstractModule
import models.daos.actors.{ChatActorFactory, TransacActorFactory}
import net.codingwell.scalaguice.ScalaModule
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.concurrent.Execution.Implicits._
import reactivemongo.api._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONInteger
import reactivemongo.play.json.collection.JSONCollection

/**
 * Provides Guice bindings for the persistence module.
 */

class MainBootModule extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  val logger = Logger("IDP." + this.getClass.getSimpleName);
  /**
   * Configures the module.
   */
  def configure() {
    bind(classOf[ChatActorFactory]).asEagerSingleton()
    bind(classOf[TransacActorFactory]).asEagerSingleton()
    applicationBootstrap()
  }

  def applicationBootstrap(): Unit = {
    import com.typesafe.config.ConfigFactory

    val config = ConfigFactory.load
    val driver = new MongoDriver
    val uri = MongoConnection.parseURI(config.getString("mongodb.uri")).get
    val connection = driver.connection(uri)

    connection.database(config.getString("mongodb.db"), FailoverStrategy.default).map(
      db => {

        db.collection[JSONCollection]("user")
          .indexesManager
          .ensure(
            Index(
              Seq("userID" -> IndexType(BSONInteger(1))),
              Some("userID"),
              unique = true,
              background = true,
              dropDups = true,
              sparse = false
            )
          ).map(logger info "Index on userID = " + _.toString)

        db.collection[JSONCollection]("user")
          .indexesManager
          .ensure(
            Index(
              Seq("email" -> IndexType(BSONInteger(1))),
              Some("email"),
              unique = false,
              background = true,
              dropDups = false,
              sparse = false
            )
          ).map(logger info "Index on email = " + _.toString)

      }
    )
  }

}
