# play-swagger-silhouette-actors

Play 2.5 & akka actor backend in order to handle websocketing (room based scheme), requiring authentication and authorization rights for uers to join.

Requirements 
------------------------------------------------------------
- MongoDB (< v3.0)
- Java 8

Apis
------------------------------------------------------------
 - [Play-ReactiveMongo](https://github.com/ReactiveMongo/Play-ReactiveMongo)
 - [play-silhouette](https://github.com/mohiva/play-silhouette)
 - [heartradio/play-swagger](https://github.com/iheartradio/play-swagger)
 - [Akka actor](http://doc.akka.io/docs/akka/snapshot/scala/actors.html)
 
Installation
-------------------
```
Activator run
```
 1. Go to ***http://localhost:7000/docs/swagger-ui/index.html?url=/docs/swagger.json#!/routes/post_auth_login*** in your browser. 
 
 2. Create admin user example:
    ```
      POST /install/{secret}
      - secret: tobechanged
      - body: {     
           "roles": [
              "Administrator"
            ],
  			"email": "test@test.loc",
  			"sex": "Mr",
  			"firstName": "John",
  			"lastName": "Doe",
  			"active": true,
  			"newsletter": true,
  			"password": "test"
       }
    ```
 3.Login Example:

```
 	  POST /auth/login
 	  -body: {
  		  "email": "test@test.loc",
  		  "password": "test"
 	  }
 ```
