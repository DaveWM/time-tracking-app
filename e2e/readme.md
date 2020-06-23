# E2E Tests

To run:

* Start up datomic, the api, and the frontend client (see instructions in respective readmes).
* Set up the datomic repo in your `~/.m2/settings.xml` file as outlined [here](https://my.datomic.com/account) (you will need to create an account if you haven't already).
* Run `./setup.sh` (you will need the [clojure CLI](https://clojure.org/guides/getting_started) installed)
* Run `npm install`
* Run `npx cypress open` to run the tests in the cypress UI, or `npx cypress run` to run them in a headless browser.

Note - If there is an error part way through the tests, you should run them again with a clean datomic db (i.e. stop the api, then datomic, then start them both back up again)
