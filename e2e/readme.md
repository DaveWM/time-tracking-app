# E2E Tests

To run:

* Start up datomic, the api, and the frontend client. Note that the DB must be empty for all the tests to pass.
* Run `npm install`
* Run `npx cypress open` to run the tests in the cypress UI, or `npx cypress run` to run them in a headless browser.

Note - you must currently run the tests in order, or they will fail. If there is an error part way through the tests, you should run them again with a clean datomic db.