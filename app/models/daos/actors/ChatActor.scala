package models.daos.actors

/**
 * Created by Mamadou Coulibaly on 08/04/2017.
 */
import akka.actor._
import com.google.inject.Inject
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsValue



class ChatActor(username: String, event: String, outChannel: ActorRef, roomSupervisor: ActorRef) extends Actor {

  val log = Logger("IDP." + this.getClass.getSimpleName);

  override def preStart() = {
    roomSupervisor ! JoinChat(username, event, outChannel)
  }

  override def postStop() = {
    log.debug(s"KILL chat actor -> [ $username ]")
    roomSupervisor ! QuitChat(username, event, outChannel)
  }

  def receive = {
    case json: JsValue => {
      roomSupervisor ! TalkChat(username, (json \ "text").get, event)
    }
  }

}

class ChatActorFactory @Inject() (actorSystem: ActorSystem) {
  lazy val roomSupervisor = actorSystem.actorOf(Props[ChatSupervisor])

  def props(username: String, event: String, outChannel: ActorRef) = {
    Props(new ChatActor(username, event, outChannel, roomSupervisor))
  }
}

