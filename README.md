# java-service-template

## template-api
- defines interfaces
- contains minimum of code

## template-logic
library  
- implements api 
- contains business logic
- can be "embedded" into some project as an api implementation

## template-rest-service
web service implements api and can be started as separate web (spring boot) app
- implements api
- uses `template-logic` to run logic

## template-rest-client
library can be used instead of "embedded" `template-logic` implementation.
- implements api
- makes query to `template-rest-service`


## TODO

- ??? api models must be records
- add and use bom project with
  - versions of apis/implementations
  - test util version
- add ACL
- add Audit
- add common errors (like, "Version conflict", "Access denied", "Validation error" etc)