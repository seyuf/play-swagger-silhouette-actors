package modules

import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Provides}
import com.mohiva.play.silhouette.api.actions.{SecuredErrorHandler, UnsecuredErrorHandler}
import com.mohiva.play.silhouette.api.crypto.{Crypter, CrypterAuthenticatorEncoder}
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.services.{AuthenticatorService, AvatarService}
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.api.{Environment, EventBus, Silhouette, SilhouetteProvider}
import com.mohiva.play.silhouette.crypto.{JcaCrypter, JcaCrypterSettings}
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth2.state.DummyStateProvider
import com.mohiva.play.silhouette.impl.providers.oauth2.{FacebookProvider, GoogleProvider}
import com.mohiva.play.silhouette.impl.services.GravatarService
import com.mohiva.play.silhouette.impl.util._
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.repositories.CacheAuthenticatorRepository
import com.typesafe.config.{Config, ConfigFactory}
import models.daos.token.{TokenDAO, TokenDAOImpl}
import models.daos.user.{UserDAO, UserDAOImpl}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}
import reactivemongo.api._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONInteger
import reactivemongo.play.json.collection.JSONCollection
import services.token.{TokenServiceImpl, TokenService}
import services.user.{UserService, UserServiceImpl}
import utils.DefaultEnv
import utils.auth.{CustomSecuredErrorHandler, CustomUnsecuredErrorHandler}

/**
 * The Guice module which wires all Silhouette dependencies.
 */
class SilhouetteModule extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  val log = Logger("WRTC." + this.getClass.getSimpleName);
  /**
   * Configures the module.
   */
  def configure() {

    bind[Silhouette[DefaultEnv]].to[SilhouetteProvider[DefaultEnv]]
    bind[UnsecuredErrorHandler].to[CustomUnsecuredErrorHandler]
    bind[SecuredErrorHandler].to[CustomSecuredErrorHandler]
    bind[OAuth2StateProvider].to[DummyStateProvider]
    bind[UserService].to[UserServiceImpl]
    bind[CacheLayer].to[PlayCacheLayer]
    bind[IDGenerator].toInstance(new SecureRandomIDGenerator())
    bind[PasswordHasher].toInstance(new BCryptPasswordHasher())
    bind[FingerprintGenerator].toInstance(new DefaultFingerprintGenerator(false))
    bind[EventBus].toInstance(EventBus())
    bind[Clock].toInstance(Clock())
    bind[UserService].to[UserServiceImpl]
    bind[UserDAO].to[UserDAOImpl]
    bind[TokenDAO].to[TokenDAOImpl]
    bind[TokenService].to[TokenServiceImpl]
    bind[CacheLayer].to[PlayCacheLayer]
    initMongodbConnection()
  }


  @Provides
  def initMongodbConnection() = {

    import com.typesafe.config.ConfigFactory

    import scala.collection.JavaConversions._

    val config = ConfigFactory.load
    val driver = new MongoDriver
    val connection = driver.connection(
      config.getStringList("mongodb.servers"),
      MongoConnectionOptions(),
      Seq()
    )


    connection.database(config.getString("mongodb.db"), FailoverStrategy.default).map(
      db => {

        db.collection[JSONCollection]("user")
          .indexesManager
          .ensure(
            new Index(
              Seq("userID" -> IndexType(BSONInteger(1))),
              Some("userID"),
              unique = true,
              background = true,
              dropDups = true,
              sparse = false
            )
          ).map(log info "Index on userID = " + _.toString)

      }
    )

  }

  /**
   * Provides the HTTP layer implementation.
   *
   * @param client Play's WS client.
   * @return The HTTP layer implementation.
   */
  @Provides
  def provideHTTPLayer(client: WSClient): HTTPLayer = new PlayHTTPLayer(client)

  /**
   * Provides the Silhouette environment.
   *
   * @param userService The user service implementation.
   * @param authenticatorService The authentication service implementation.
   * @param eventBus The event bus instance.
   * @return The Silhouette environment.
   * def provideEnvironment(
   * userService: UserService,
   * authenticatorService: AuthenticatorService[CookieAuthenticator],
   * eventBus: EventBus): Environment[User, CookieAuthenticator] = {
   *
   * Environment[User, CookieAuthenticator](
   * userService,
   * authenticatorService,
   * Seq(),
   * eventBus
   * )
   * }
   *
   */

  @Provides
  def provideEnvironment(
    userService: UserService,
    authenticatorService: AuthenticatorService[JWTAuthenticator],
    eventBus: EventBus
  ): Environment[DefaultEnv] = {


    Environment[DefaultEnv](
      userService,
      authenticatorService,
      Seq(),
      eventBus
    )
  }

  @Provides @Named("authenticator-crypter")
  def provideAuthenticatorCrypter(configuration: Configuration): Crypter = {
    val config = configuration.underlying.as[JcaCrypterSettings]("silhouette.authenticator.crypter")
    new JcaCrypter(config)
  }

  @Provides
  def provideAuthenticatorService(
    @Named("authenticator-crypter") crypter: Crypter,
    cacheLayer: CacheLayer,
    idGenerator: IDGenerator,
    configuration: Configuration,
    clock: Clock
  ): AuthenticatorService[JWTAuthenticator] = {

    // custom reader for requestPart config None for all type of requests
    implicit val requestPartReader = new ValueReader[Option[Option[Seq[RequestPart.Value]]]] {
      def read(config: Config, path: String): Option[Nothing] = {
        None
      }
    }

    val config = ConfigFactory.load().as[JWTAuthenticatorSettings]("silhouette.authenticator")
    val encoder = new CrypterAuthenticatorEncoder(crypter)

    //new JWTAuthenticatorService(config, Some(new CacheAuthenticatorRepository[JWTAuthenticator](cacheLayer)), encoder, idGenerator, clock)

    //new JWTAuthenticatorService(config, None, encoder, idGenerator, clock = clock)
    new JWTAuthenticatorService(config, Some(new CacheAuthenticatorRepository[JWTAuthenticator](cacheLayer)), encoder, idGenerator, clock = clock)
    //new JWTAuthenticatorService(config, Some(new CacheAuthenticatorDAO[JWTAuthenticator](cacheLayer)), idGenerator, clock)
  }
  /**
   * Provides the avatar service.
   *
   * @param httpLayer The HTTP layer implementation.
   * @return The avatar service implementation.
   */
  @Provides
  def provideAvatarService(httpLayer: HTTPLayer): AvatarService = new GravatarService(httpLayer)

  /**
   * Provides the password hasher registry.
   *
   * @param passwordHasher The default password hasher implementation.
   * @return The password hasher registry.
   */
  @Provides
  def providePasswordHasherRegistry(passwordHasher: PasswordHasher): PasswordHasherRegistry = {
    new PasswordHasherRegistry(passwordHasher)
  }

  /**
   * Provides the credentials provider.
   *
   * @param authInfoRepository The auth info repository implementation.
   * @param passwordHasherRegistry The password hasher registry.
   * @return The credentials provider.
   */
  @Provides
  def provideCredentialsProvider(
    authInfoRepository: AuthInfoRepository,
    passwordHasherRegistry: PasswordHasherRegistry
  ): CredentialsProvider = {
    new CredentialsProvider(authInfoRepository, passwordHasherRegistry)
  }

  /* *
   * Provides the social provider registry.
   * @param facebookProvider The Facebook provider implementation.
   * @return The Silhouette environment.
   **/
  @Provides
  def provideSocialProviderRegistry(
    facebookProvider: FacebookProvider,
    googleProvider: GoogleProvider
  ): SocialProviderRegistry = {

    SocialProviderRegistry(Seq(
      facebookProvider,
      googleProvider
    ))
  }

  /**
   * Provides the Facebook provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The OAuth2 state provider implementation.
   * @param configuration The Play configuration.
   * @return The Facebook provider.
   */
  @Provides
  def provideFacebookProvider(
    httpLayer: HTTPLayer,
    stateProvider: OAuth2StateProvider,
    configuration: Configuration
  ): FacebookProvider = {

    new FacebookProvider(httpLayer, stateProvider, configuration.underlying.as[OAuth2Settings]("silhouette.facebook"))
  }

  /**
   * Provides the Google provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The OAuth2 state provider implementation.
   * @param configuration The Play configuration.
   * @return The Google provider.
   */
  @Provides
  def provideGoogleProvider(
    httpLayer: HTTPLayer,
    stateProvider: OAuth2StateProvider,
    configuration: Configuration
  ): GoogleProvider = {

    new GoogleProvider(httpLayer, stateProvider, configuration.underlying.as[OAuth2Settings]("silhouette.google"))
  }

}
