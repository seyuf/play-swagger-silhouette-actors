package utils

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator
import models.daos.user.User

/**
 * Created by madalien on 09/05/16.
 */
trait DefaultEnv extends Env {
  type I = User
  type A = JWTAuthenticator
}

