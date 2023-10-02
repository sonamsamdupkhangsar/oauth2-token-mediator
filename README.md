# token-mediator
This is for mediating Oauth2 authorization and token creation process.

This token-mediator will follow the `Token-Mediating Backend`  where the 
client application makes a request to this project and which initiates the Oauth
flow as described in [Token-Mediating Backend](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps#name-token-mediating-backend).

There are 2 endpoints:
1. Initiate the OAuth flow
2. Get token for a client.

For getting token for a client, this application retrieves the clientSecret
value from the database.  The clientSecret value is saved from the [authorization-server](https://github.com/sonamsamdupkhangsar/my-spring-authorization-server) using a rest call to this application.

## Run
For running locally using local profile:
`./gradlew bootRun --args="--spring.profiles.active=local"`

## Build Docker image
Gradle build:
```
./gradlew bootBuildImage --imageName=name/oauth2-token-mediator
```
Docker build passing in username and personal access token varaibles into docker to be used as environment variables in the gradle `build.gradle` file for pulling private maven artifact:
```
docker build --secret id=USERNAME,src=USERNAME --secret id=PERSONAL_ACCESS_TOKEN,src=PERSONAL_ACCESS_TOKEN . -t my/oauth2-token-mediator
```

Pass local profile as argument:
```
 docker run -e --spring.profiles.active=local -p 9001:9001 -t myorg/myapp
```
