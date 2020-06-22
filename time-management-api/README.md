# time-management-api

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed to build the application. You will also need Java installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

The application uses Datomic for its DB, the easiest way to run it is to run `./run-datomic.sh`. This will start up Datomic in a docker container.

To run the application, the easiest way is to use Java to run the uberjar (I should have supplied this, please let me know if you don't have it). 
Just run `java -jar time-management-api-0.1.0-SNAPSHOT-standalone.jar`.

To build the application, you must sign up for an account on the [Datomic website](https://my.datomic.com/account/create).
They will then email you a license key. 
You must then update your `.lein/credentials.clj` to:
```clojure
{#"my\.datomic\.com" {:username "Account email"
                      :password "Download key (displayed at https://my.datomic.com/account)"}}
```
Once you have done this, to start the api just run `lein run`. 
This will start the api on port 8081 by default. To change the port, set the `API_PORT` environment variable. 
You can also compile to a jar file by running `lein uberjar`.

To run the unit tests, run `lein test`.
