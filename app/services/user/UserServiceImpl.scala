package services.user

import java.util.UUID
import javax.inject.Inject

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import models.daos.user.{User, UserDAO}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}
import reactivemongo.bson.BSONDocument
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Handles actions to users.
 *
 * @param userDAO The user DAO implementation.
 */
class UserServiceImpl @Inject() (userDAO: UserDAO) extends UserService {

  val logger = Logger("WRTC." + this.getClass.getSimpleName);
  /**
   * Retrieves a user that matches the specified login info.
   *
   * @param loginInfo The login info to retrieve a user.
   * @return The retrieved user or None if no user could be retrieved for the given login info.
   */

  def retrieve(loginInfo: LoginInfo, active: Option[Boolean] = None): Future[Option[User]] = userDAO.find(loginInfo, active)
  def retrieve(loginInfo: LoginInfo): Future[Option[User]] = userDAO.find(loginInfo, None)

  /**
   * Saves a user.
   *
   * @param user The user to save.
   * @return The saved user.
   */
  def save(user: User) = userDAO.save(user)

  def getByIds(userIds: List[String]): Future[Seq[User]] = userDAO.getByIds(userIds)

  def update(selector: BSONDocument, modifier: BSONDocument): Future[Option[User]] = userDAO.update(selector, modifier)

  def count(selector: BSONDocument): Future[Int] = userDAO.count(selector)

  def find(selector: BSONDocument, sort: JsObject = Json.obj("updated_date" -> -1), page: Int = 1, numberPerPage: Int = 25): Future[List[User]] = userDAO.find(selector, sort, page, numberPerPage)
  def findAndModify(selector: BSONDocument, modifier: User, fields: BSONDocument): Future[Option[User]] = userDAO.findAndModify(selector, modifier, fields)
  def getList(page: Int, numberByPage: Int): Future[List[User]] = userDAO.getList(page, numberByPage)
  override def update(selector: BSONDocument, modifier: BSONDocument, upsertIn: Boolean = false): Future[UpdateWriteResult] = userDAO.update(selector, modifier)
  override def remove(selector: BSONDocument): Future[WriteResult] = userDAO.remove(selector)

  def save(profile: CommonSocialProfile) = {
    userDAO.find(profile.loginInfo, None).flatMap {
      case Some(user) => // Update user with profile
        userDAO.save(
          user.copy(
            firstName = profile.firstName.getOrElse("No fisrtName from SN"),
            lastName = profile.lastName.getOrElse("No lastName from SN"),
            email = profile.email.getOrElse("No Email from SN")
          )
        )
      case None => // Insert a new user
        logger.debug(s"fb datas $profile")
        userDAO.save(
          User(
            userID = Some(UUID.randomUUID()),
            loginInfo = Some(profile.loginInfo),
            firstName = profile.firstName.getOrElse("no firstName from SN"),
            lastName = profile.lastName.getOrElse("no lastName from SN"),
            email = profile.email.getOrElse("No Email from SN"),
            avatar_path = profile.avatarURL,
            sex = "Unkown",
            roles = List("NormalUser"),
            config = None
          )
        )
    }
  }
}
