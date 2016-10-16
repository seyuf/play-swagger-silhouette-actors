package controllers

import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.PasswordHasher
import com.mohiva.play.silhouette.api.{LoginInfo, Silhouette}
import models.daos.user.User
import org.joda.time.DateTime
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, Controller}
import reactivemongo.bson.BSONDocument
import services.user.UserService
import utils.DefaultEnv
import utils.auth.{WithRoles, IsOwner}

import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

/**
 * Created by madalien on 23/05/16.
 */

class UserCtrl @Inject() (
                           val messagesApi: MessagesApi,
                           val userService: UserService,
                           application: play.api.Application,
                           passwordHasher: PasswordHasher,
                           authInfoRepository: AuthInfoRepository,

                           silhouette: Silhouette[DefaultEnv]
) extends Controller with I18nSupport {
  val logger = Logger("WRTC." + this.getClass.getSimpleName);

  import play.api.libs.json.Json
  import reactivemongo.play.json.BSONFormats._
  def updateUser(email: String) = silhouette.SecuredAction(IsOwner(email)).async(parse.json[User]) {

    implicit request =>
      {

        implicit val loginInfoFormat = Json.format[LoginInfo]
        val loginInfo = request.identity.loginInfo.get
        val selector = BSONDocument("loginInfo" -> Json.toJson(loginInfo))
        userService.find(selector).flatMap {
          curUser =>
            {
              val userInfos = curUser.head
              val elemToUpdate = request.body.copy(updated_date = Some(new DateTime()), roles = userInfos.roles)
              logger.debug(s"User update: $elemToUpdate")
              userService.findAndModify(selector, elemToUpdate.copy(), BSONDocument()) map {
                mayOldUsr =>
                  {
                    mayOldUsr match {
                      case Some(oldUsr: User) => {
                        Ok(Json.obj("res" -> Json.toJson(oldUsr)))
                      }
                      case _ => InternalServerError(Json.obj("res" -> s"error while updating user $elemToUpdate"))
                    }
                  }
              }
            }
        }
      }
  }

  def getByEmail(email: String) = silhouette.SecuredAction(WithRoles("Administrator")).async {
    implicit request =>
      {
        userService.find(BSONDocument("email" -> email)).map {
          usrRes => Ok(Json.toJson(usrRes))
        }
      }
  }

  def count() = silhouette.SecuredAction(WithRoles("Administrator")).async {
    implicit request =>
      {
        userService.count(BSONDocument()).map {
          nbUsr => Ok(Json.obj("res" -> nbUsr))
        }
      }
  }

  def updateUserAdmin(id: String) = silhouette.SecuredAction(WithRoles("Administrator")).async(parse.json[User]) {

    implicit request =>
      {

        val elemToUpdate = request.body.copy(updated_date = Some(new DateTime()))
        val selector = BSONDocument("userID" -> id);
        logger.debug(s"$elemToUpdate")
        userService.findAndModify(selector, elemToUpdate, BSONDocument()) map {
          mayOldUsr =>
            {
              mayOldUsr match {
                case Some(oldUsr: User) => {
                  Ok(Json.obj("res" -> Json.toJson(oldUsr)))
                }
                case _ => InternalServerError(Json.obj("res" -> s"error while updating user $elemToUpdate"))
              }
            }
        }
      }
  }

  def userList(page: Int, numberByPage: Int) = silhouette.SecuredAction(WithRoles("Administrator")).async {
    implicit request =>
      {

        logger.debug(s"USER LIST")
        userService.getList(page, numberByPage) map {
          userArr =>
            {
              Ok(Json.toJson(userArr))
            }
        }
      }
  }

  def getUserByIds(ids: List[String]) = silhouette.SecuredAction(WithRoles("NormalUser")).async {
    implicit request =>
      {
        logger.info(s"$ids")
        userService.getByIds(ids).map {
          subArr =>
            {

              var userWithoutEmail: Seq[User] = List()
              if (request.identity.roles.contains("Administrator")) {
                userWithoutEmail = subArr
              } else {
                userWithoutEmail = subArr.map {
                  user =>
                    {
                      val configOut = user.config.fold(BSONDocument()) {

                        config =>
                          {
                            config.add(BSONDocument("isAdmin" -> user.roles.contains("Administrator")))
                          }
                      }
                      val rolesOut = List[String]()
                      user.copy(roles = rolesOut, config = Some(configOut), email = "", loginInfo = None)
                    }
                }

              }
              Ok(Json.toJson(userWithoutEmail))
            }
        }
      }
  }

  def userByIdsIDP(ids: List[String]) = Action.async {
    implicit request =>
      {
        logger.info(s"$ids")
        userService.getByIds(ids).map {
          subArr =>
            {

              val userWithoutEmail = subArr.map {
                user =>
                  {

                    val configOut = user.config.fold(BSONDocument()) {

                      config =>
                        {
                          config.add(BSONDocument("isAdmin" -> user.roles.contains("Administrator")))
                        }
                    }
                    val rolesOut = List[String]()
                    user.copy(roles = rolesOut, config = Some(configOut), email = "", loginInfo = None)
                  }
              }
              Ok(Json.toJson(userWithoutEmail))
            }
        }
      }
  }

  // refactoring in findone

  /*


def resetPassword(ids: List[String]) = silhouette.SecuredAction(WithRoles("NormalUser")).async {
    implicit request =>
      {
        logger.info(s"$ids")
        userService.getByIds(ids).map {
          subArr => Ok(Json.toJson(subArr))
        }
      }
  }

  def removeByIds(ids: List[String]) = silhouette.SecuredAction(WithRoles("NormalUser")).async {
    implicit request =>
      {
        val query = BSONDocument("userID" -> BSONDocument("$in" -> ids))
        userService.remove(query).map {
          res =>
            res.inError match {
              case true => {
                InternalServerError(Json.obj("res" -> s" ${res.message}  code: ${res.code}"))
              }
              case false => {
                Ok(Json.obj("res" -> s" ${res.message} Remove Done"))
              }
            }
        }
      }
  }


def findUserById(id: String) = silhouette.SecuredAction(WithRoles("NormalUser")).async {
implicit request =>
{
  val query = BSONDocument("userID" -> id)
  userService.find(query).map {
    subArr => Ok(Json.toJson(subArr))
  }
        Future.successful(Ok(s"nun nun"))
      }

  }

  def byAtelier(id: String) = silhouette.SecuredAction(WithRoles("NormalUser")).async {
    implicit request =>
      {
        val query = BSONDocument("ateliers" -> id)
        userService.find(query).map {
          subArr => Ok(Json.toJson(subArr))
        }
      }
  }

  */

  def validate(email: String) = silhouette.SecuredAction(WithRoles("NormalUser")).async {
    implicit request =>
      {
        val query = BSONDocument("email" -> email)
        userService.find(query).map {
          subArr => if (subArr.isEmpty) NotFound else Ok
        }
      }
  }

  def uploadLogo(user_id: String) = silhouette.SecuredAction(WithRoles("NormalUser")).async(parse.multipartFormData) {
    request =>
      request.body.file("file").map { file =>
        import java.io.File
        val currentPath = application.path.getAbsolutePath
        val filename = file.filename.replace(' ', '_')
        val filePath = currentPath + "/user/" + user_id + "/" + filename;
        val fileUrl = "logos/user/" + user_id + "/" + filename;
        val contentType = file.contentType
        //val title = request.body.asFormUrlEncoded.get("title").get(0);
        val dir = new File(currentPath + "/user/" + user_id)
        dir.mkdirs()
        logger.debug(s"Application cur path [$currentPath]")
        file.ref.moveTo(new File(filePath))
        val updatedAtelier = BSONDocument(
          "$set" -> BSONDocument(
            "avatar_path" -> fileUrl
          )
        )

        val selec = BSONDocument("userID" -> user_id);
        userService.update(selec, updatedAtelier, upsertIn = false) map {
          res =>
            res.inError match {
              case true => {
                InternalServerError(Json.obj("res" -> s" ${res.message}  code: ${res.code}"))
              }
              case false => {
                Ok(Json.obj("res" -> Json.obj("avatar_path" -> fileUrl)))
              }
            }
        }
      }.getOrElse {
        Future.successful(BadRequest(Json.obj("result" -> "atelier error: missing file or existing file")))
      }
  }

  def getLogo(filename: String) = Action {
    implicit request =>
      {
        Ok.sendFile(new java.io.File(application.path.getAbsolutePath + "/" + filename))
      }
  }

}
