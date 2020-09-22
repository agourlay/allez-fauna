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

Routes are graded according to different scales depending on the geography and type of climbing.
Those grades act a reference point to enable climbers to pick appropriately which route to tackle next.

Climbers often look for things that are both challenging and fun therefore a route should not be too easy nor too hard.

Unfortunately grading systems are not following a strict scientific method as the difficulty of a route is also a subjective feeling.

This is where `Allez-Fauna` comes in :)

`Allez-Fauna` is offering climbers to share their suggested grades for all their favourite routes.
This way, grades become clearer through collaborative grading.

## Data model

The data model is rather straight forward.

- `Gyms` have `Routes`.
- Authenticated `Users` submit `SuggestedGrades` on `Routes`.

### Gyms

```json
  {
    "id" : "<gym-id>",
    "name" : "MagicMountain",
    "address" : "Böttgerstrasse 20, Berlin, DE 13357",
    "website" : "https://www.magicmountain.de/",
    "createdAt" : "2020-09-08T13:51:40.126573Z"
  }
```

They are indexed by `id` and `name`.

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

They are indexed by `id`, `name` and `gymId`.

### User

```json
  {
    "id" : "<user-id>",
    "firstName" : "John",
    "surname" : "Doe",
    "country" : "USA",
    "email" : "john.doe@mail.com",
    "createdAt" : "2020-09-08T13:51:40.126573Z"
  }   
```

They are indexed by `id` and `email` (unique).

### SuggestedGrade

```json
  {
    "id" : "<suggested-grade-id>",
    "routeId" : "<route-id>",
    "userId" : "<user-id>",
    "grade" : {
      "label" : "6b",
      "scale" : "Fontainebleau"
    },
    "comment": "nasty crux midway",
    "createdAt" : "2020-09-08T13:51:40.126573Z"
  }
```

They are indexed by `id`, `routeId`, `gymId`, `userId` and (`userId` + `routeId`)  (unique).

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
DELETE /gyms/<id>
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

It is possible to use FaunaDB cloud for development as well, but we are going to set up everything locally for convenience.

### FaunaDB Docker container

Run FaunaDB locally using the [official Docker container](https://docs.fauna.com/fauna/current/integrations/dev.html):

```docker run --rm --name faunadb -p 8443:8443 fauna/faunadb```

You can inspect the status of the database using the admin console:

```
docker exec -it faunadb /bin/bash
root@2aab35f2fda0:/faunadb# cd bin/
root@2aab35f2fda0:/faunadb/bin# faunadb-admin --key secret status
Loaded configuration from /etc/faunadb.yml...
Replica: NoDC (data+log)
========================
Status  State  WorkerID  Log Segment  Address     Owns    Goal    HostID                                Persisted Timestamp
up      live   512       0 (leader)   172.17.0.2  100.0%  100.0%  761fdd9f-b24c-43a6-a210-b19ccf51f162  2020-09-17T08:53:08.089999999Z
```

### FaunaShell

We are going to use [FaunaShell](https://fauna.com/blog/introducing-fauna-shell)

Open a terminal and install the Fauna Shell. If you are on a PC, you can use npm:
```
npm install -g fauna-shell
```

Alternatively, if you are on a Mac, you can use Homebrew:

```
brew install fauna-shell
```

Configure the local endpoint to talk to the Docker container:

```
fauna add-endpoint http://localhost:8443
fauna default-endpoint localhost
```

Create the database:

```
fauna create-database allez-fauna-db
```

Next, issue an API Key for connecting to the new created DB from the service:

```
fauna create-key allez-fauna-db server --secret=secret
``` 

Setup the schema and initial data using:

```
fauna eval allez-fauna --file=./scripts/create_schema.fql --secret=secret
```

The database is now ready!

## How to run the application

This is a [SBT](https://www.scala-sbt.org/index.html) multi-module project with 2 modules.

### server

Contains the code for the API.

Start `sbt` and then get into the module by running `project server`
- execute `run` to start the server (port 8080)

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

Start `sbt` and execute `it:test` for the integration tests (The server needs to be started beforehand manually!)

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
- query language for filtering routes (less/greater than grade)
- a UI :)
