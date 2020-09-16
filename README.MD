# Allez-Fauna

Allez-Fauna is a collaborative platform for climbers to share suggested grades for their favorite routes.

It is built in Scala and uses FaunaDB for data storage.

> Allez allez! is usually a form of encouragement used by people watching the climbers.

## Tech. inside

- Scala
- FaunaDB through the [fauna-jvm driver](https://github.com/fauna/faunadb-jvm)
- [http4s](https://github.com/http4s/http4s) for serving the API
- [cornichon](https://github.com/agourlay/cornichon) for integration testing <3

## Problem statement

Routes are grades according to different official scales depending on the geography and type of climbing.
Those grades act a reference point to enable climbers to pick appropriately which route to tackle next.
Climbers often look for things that are both challenging and fun therefore a route should not be too easy nor too hard.

Unfortunately grading systems are not following a strict scientific method as the difficulty of a route is mostly a subjective feeling.

This is where `Allez-Fauna` comes in :)

`Allez-Fauna` is offering climbers to share their suggested grades for all their favourite routes.
This way, grades become clearer through collaborative grading.

## Data model

### Gyms

```json
  {
    "id" : *any-string*,
    "name" : "MagicMountain",
    "address" : "Böttgerstrasse 20, Berlin, DE 13357",
    "website" : "https://www.magicmountain.de/",
    "createdAt" : *any-date-time*
  }
```

`Gyms` have `Routes`.

### Routes

```json
  {
    "gymId" : "<gym-id>",
    "name" : "shredder",
    "climbingType" : "TopRope",
    "grade" : {
      "label" : "6a",
      "scale" : "Fontainebleau"
    },
    "profile" : [ "Dynamic" ],
    "gripsColor" : "Blue",
    "setAt" : "2020-09-08T13:51:40.126573Z"
  }
```


Authenticated `Users` submit `SuggestedGrades` on `Routes`.

### User

```json
  {
    "id" : *any-string*,
    "firstName" : "John",
    "surname" : "Doe",
    "country" : "USA",
    "email" : "john.doe@mail.com",
    "createdAt" : *any-date-time*
  }   
```

### SuggestedGrade

```json
  {
    "id" : *any-string*,
    "routeId" : "<route-id>",
    "userId" : "<user-id>",
    "grade" : {
      "label" : "6b",
      "scale" : "Fontainebleau"
    },
    "comment": "nasty crux midway",
    "createdAt" : *any-date-time*
  }
```

## API

All aggregates can be queried in various form through the API.

The API definition can be found [here](https://github.com/fauna-arnaud/allez-fauna/blob/master/server/src/main/scala/agourlay/allez/api/RestAPI.scala)

The [integration tests](https://github.com/fauna-arnaud/allez-fauna/blob/master/integration-testing/src/it/scala/agourlay/allez/features/ApiFeature.scala) are also a great resource.

Here is a non exhaustive list (query parameters non included for pagination and search):

```
GET    /gyms
GET    /gyms/<id>
GET    /gyms/<id>/routes
DELETE /gyms
DELETE /gyms/id
POST   /gyms
POST   /gyms/<id>
GET    /routes
GET    /routes/<id>
GET    /routes/<id>/suggestedGrades
DELETE /routes
DELETE /routes/<id>
POST   /routes
POST   /routes/<id>
POST   /users
GET    /users/<id>
GET    /users/<id>/suggestedGrades
DELETE /users/<id>
DELETE /users
POST   /user-login
POST   /suggestedGrades (requires user basic auth)
GET    /suggestedGrades/<id>
DELETE /suggestedGrades/<id>
DELETE /suggestedGrades/
```

## Local development env.

Run FaunaDB locally:

```docker run --rm --name faunadb -p 8443:8443 fauna/faunadb```

Install [FaunaShell]:

Configure the local endpoint to talk to the Docker container:

```
fauna add-endpoint http://localhost:8443
fauna default-endpoint localhost
```

Setup schema and initial data using:

```
fauna eval allez-fauna --file=./scripts/create_schema.fql --secret=secret
```

## How to run it

This is a [SBT](https://www.scala-sbt.org/index.html) multi-module project with 2 modules.

### server

Contains the code for the API.

Get into the module `sbt project server` and then
- `test` for unit tests
- `run` to start the server (port 8080)

Various configuration knobs are exposed in the `src/main/resources/application.conf`

```
allez-fauna {
  http-port = 8080
  fauna-api-endpoint = "http://localhost:8443"
  fauna-secret = "secret"
}
```

### integration-testing

Contains the code for the integration tests.

Get into the module `sbt project integration-testing` and then
- `it:test` for the integration tests (The server needs to be started beforehand manually!)

## FaunaDB resources

### FQL

- https://fauna.com/blog/getting-started-with-fql-faunadbs-native-query-language-part-1
- https://fauna.com/blog/getting-started-with-fql-faunadbs-native-query-language-part-2
- https://fauna.com/blog/getting-started-with-fql-faunadbs-native-query-language-part-3
- https://fauna.com/blog/getting-started-with-fql-faunadbs-native-query-language-part-4
- https://fauna.com/blog/getting-started-with-fql-faunadbs-native-query-language-part-5
- https://fauna.com/blog/core-fql-concepts-part-1-working-with-dates-and-times

### Reference & tutorials

- https://docs.fauna.com/fauna/current/start/
- https://docs.fauna.com/fauna/current/tutorials/

## TODOS

- introduce administrator role to protect some features
    - deletions of aggregates
  
- better handling of the grades at the DB level (enum of enums?)    
- the suggestions must have the same grade as the route's base grade
- a UI :)
