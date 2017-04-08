package models.daos.actors

/**
  * Created by COULIBALY Mamadou on 08/04/2017.
 */
import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import com.google.inject.Inject
import play.api.Logger
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.json.JsValue

class TransacActor(userID: String, event: String, outChannel: ActorRef, transacSupervisor: ActorRef) extends Actor {

  val log = Logger("IDP." + this.getClass.getSimpleName);

  override def preStart() = {
    transacSupervisor ! Join(userID, event, outChannel)
  }

  override def postStop() = {
    log.debug(s"KILL transac actor -> [ $userID ]")
    transacSupervisor ! Quit(userID, event, outChannel)
  }

  def receive = {
    case json: JsValue => {
      val sendTo = (json \ "userID").asOpt[String].getOrElse("")
      val msg = (json \ "text").asOpt[String].getOrElse("")
      transacSupervisor ! Talk(sendTo, msg, event)
    }
  }

}

class TransacActorFactory @Inject() (actorSystem: ActorSystem) {
  lazy val transacSupervisor = actorSystem.actorOf(Props[TransacSupervisor])

  def props(userID: String, event: String, outChannel: ActorRef) = {
    Props(new TransacActor(userID, event, outChannel, transacSupervisor))
  }
}
