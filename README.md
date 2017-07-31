# alf.io-PI
Raspberry-Pi 3 version for offline check-in

## Development

### Requirements

* Java 8 SDK (min. 1.8.0_112)
* Node LTS

Install latest npm and angular-cli

```bash
npm install npm -g
npm install @angular/cli -g
```

### Configuration

Create an *application.properties* file

```bash
cp src/main/resources/application.properties.sample src/main/resources/application.properties
```

and edit the following lines properly adding host, user and credentials for testing

```
master.url=https://url-of-master
master.username=pi-agent
master.password=password
```

### Build and start server

To build launch:

* on OSX or Linux: `./gradlew build`
* on Windows: `gradlew.bat build`


To start backend server launch:

* on OSX or Linux: `./gradlew -Dspring.profiles.active=dev :backend:bootRun`
* on Windows: `gradlew.bat -Dspring.profiles.active=dev :backend:bootRun`

Then point your browser to http://localhost:8080/ and login with credentials:

* user: admin
* password: abcd

Start frontend application:

```bash
cd frontend
npm install # only once
npm start
```

Then point your browser to http://localhost:4200/

