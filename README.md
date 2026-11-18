# Microservices MD05

Repository for developing a Spring Boot microservices system. Each service is maintained as an independent application, while the root **docker/** directory provides a central place to orchestrate services with Docker Compose.

## Repository structure

~~~text
microservices-md05/
├── docker/
│   ├── docker-compose.yml   # Central Docker Compose configuration
│   └── .env.example         # Example variables for Docker Compose
├── api-gateway/\n│   ├── src/\n│   ├── Dockerfile\n│   ├── .dockerignore\n│   └── build.gradle\n├── eureka-service/
│   ├── src/
│   ├── gradle/
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── build.gradle
│   └── gradlew
└── .github/
    └── workflows/
        ├── eureka-ci.yml\n        └── gateway-ci.yml
~~~

## Requirements

### Run applications locally (without Docker)
- JDK 21
- Git
- Internet access on the first Gradle build so dependencies can be downloaded

### Run with Docker
- Docker Engine or Docker Desktop
- Docker Compose v2 (the **docker compose** command)

---

# Eureka Service

## 1. What it does

The **eureka-service** is the Netflix Eureka Server used for service discovery. Other microservices can register themselves with Eureka and discover one another by application name instead of depending on fixed container IP addresses.

| Setting | Value |
|---|---|
| Application name | eureka-service |
| Default port | 8761 |
| Dashboard | http://localhost:8761 |
| Health endpoint | http://localhost:8761/actuator/health |
| Registry endpoint | http://localhost:8761/eureka/ |

The Eureka server is configured not to register itself as a client and not to fetch a registry from another Eureka server. This is the normal standalone-server configuration:
- eureka.client.register-with-eureka=false
- eureka.client.fetch-registry=false

### Security
- The dashboard and Eureka API require HTTP Basic authentication.
- /actuator/health is public so Docker and infrastructure health checks can inspect the service.
- Only the health and info Actuator endpoints are exposed over HTTP.
- Set a strong, unique EUREKA_PASSWORD before using this service outside local development.

## 2. Configure environment variables

The application reads its configuration from environment variables:

| Variable | Required | Default / example | Description |
|---|---:|---|---|
| SERVER_PORT | No | 8761 | HTTP port used by Eureka |
| EUREKA_HOSTNAME | No | localhost for local run; eureka-service in Compose | Hostname advertised by the Eureka instance |
| EUREKA_USERNAME | Yes | admin | HTTP Basic username |
| EUREKA_PASSWORD | Yes | Set your own strong password | HTTP Basic password |

> The .env.example files are templates only. Spring Boot does not automatically load a file named .env when started directly with Gradle. For a local non-Docker run, export the variables in your shell or configure them in your IDE run configuration. Docker Compose loads docker/.env for the container.

## 3. Run normally (without Docker)

Run these commands from the repository root.

### Linux / macOS

~~~bash
cd eureka-service

export SERVER_PORT=8761
export EUREKA_HOSTNAME=localhost
export EUREKA_USERNAME=admin
export EUREKA_PASSWORD='replace-with-a-long-random-password'

./gradlew bootRun
~~~

### Windows PowerShell

~~~powershell
cd eureka-service

$env:SERVER_PORT = "8761"
$env:EUREKA_HOSTNAME = "localhost"
$env:EUREKA_USERNAME = "admin"
$env:EUREKA_PASSWORD = "replace-with-a-long-random-password"

.\gradlew.bat bootRun
~~~

To run tests and build the executable JAR, from the eureka-service directory:

~~~bash
./gradlew test bootJar
~~~

On Windows, use .\gradlew.bat test bootJar.

After startup, open http://localhost:8761 and sign in with the configured username and password.

## 4. Run with Docker Compose (recommended for the microservices stack)

The Compose file is centralized at docker/docker-compose.yml; the Eureka Dockerfile remains in eureka-service/Dockerfile. This lets the repository add more services to the same Compose project and Docker network later.

### Step 1 — Create the local environment file

Run from the repository root:

~~~bash
cp docker/.env.example docker/.env
~~~

Edit docker/.env and set your own credentials:

~~~dotenv
EUREKA_USERNAME=admin
EUREKA_PASSWORD=replace-with-a-long-random-password
EUREKA_BIND_IP=127.0.0.1
EUREKA_HOST_PORT=8761
~~~

Keep EUREKA_BIND_IP=127.0.0.1 for local-only access. Change it to 0.0.0.0 only if access from other machines is required, and configure the host firewall/security group accordingly. Do not commit docker/.env.

### Step 2 — Build and start Eureka

From the repository root:

~~~bash
docker compose -f docker/docker-compose.yml up -d --build eureka-service
~~~

### Step 3 — Check status and logs

~~~bash
docker compose -f docker/docker-compose.yml ps
docker compose -f docker/docker-compose.yml logs -f eureka-service
~~~

The container has a health check that calls /actuator/health. The endpoint does not require credentials.

### Step 4 — Open the dashboard

Open http://localhost:8761 and authenticate with EUREKA_USERNAME and EUREKA_PASSWORD from docker/.env.

### Stop or remove the container

Stop the service without removing it:

~~~bash
docker compose -f docker/docker-compose.yml stop eureka-service
~~~

Stop and remove the container and the Compose network:

~~~bash
docker compose -f docker/docker-compose.yml down
~~~

To rebuild after changing the service:

~~~bash
docker compose -f docker/docker-compose.yml up -d --build eureka-service
~~~

## 5. Connecting other services to Eureka in Docker

All services defined in the root Compose file should join the shared microservices-network. From another container, use the Compose service name as the hostname:

~~~text
http://eureka-service:8761/eureka/
~~~

Do not use localhost:8761 from inside another container: localhost refers to that container itself.

A future Spring Boot client service can use an environment variable such as:

~~~yaml
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_SERVER_URL}
~~~

Then configure EUREKA_SERVER_URL for the container with the Eureka URL and credentials, for example:

~~~text
http://<username>:<url-encoded-password>@eureka-service:8761/eureka/
~~~

URL-encode username/password characters that have special meaning in URLs. Keep client credentials in the local docker/.env or a secrets manager; never hard-code real credentials in a committed Compose file.

When adding a service, add its build context (for example ../user-service), Dockerfile, and microservices-network membership to docker/docker-compose.yml. Add a health-based depends_on only if the dependent service truly needs Eureka to be healthy before starting; applications should still handle temporary Eureka unavailability and retry registration.

## 6. Troubleshooting

| Symptom | What to check |
|---|---|
| Container exits immediately | Run docker compose -f docker/docker-compose.yml logs eureka-service; verify docker/.env exists and has both credentials |
| Port 8761 is already in use | Change EUREKA_HOST_PORT in docker/.env, then recreate the container |
| Dashboard returns 401 | Check the username/password in docker/.env; restart the container after changing them |
| Another container cannot reach Eureka | Confirm both containers join microservices-network; use hostname eureka-service, not localhost |
| Local Gradle startup fails due to missing config | Set EUREKA_USERNAME and EUREKA_PASSWORD in the shell or IDE run configuration |

## 7. CI

The GitHub Actions workflow at .github/workflows/eureka-ci.yml runs the Eureka Gradle tests/build, validates the root Docker Compose configuration, and builds the Eureka Docker image. It is triggered by changes under eureka-service/, docker/, or the workflow file itself, as well as manual dispatch.

---

# API Gateway

## 1. What it does

The **api-gateway** is the single HTTP entry point for client requests. It routes requests to backend services using explicit Spring Cloud Gateway routes and Eureka service discovery. Business logic remains in the individual microservices.

| Setting | Value |
|---|---|
| Application name | api-gateway |
| Default port | 8080 |
| Health endpoint | http://localhost:8080/actuator/health |

### Routes

| Public path | Eureka service | Path forwarded downstream |
|---|---|---|
| /api/users/** | user-service | /users/** |
| /api/products/** | product-service | /products/** |
| /api/carts/** | cart-service | /carts/** |

The `StripPrefix=1` filter removes the first path segment (`api`). These routes are explicitly declared; the gateway does not automatically expose every service registered in Eureka.

## 2. Dependencies and versions

The service follows the same toolchain as eureka-service:
- Java 21
- Spring Boot 4.1.2
- Spring Cloud 2025.1.4
- Gradle 9.9.1

Spring Cloud Gateway uses the reactive WebFlux server. Do not add `spring-boot-starter-web` (Spring MVC) to this service.

## 3. Configure environment variables

| Variable | Required | Default | Description |
|---|---:|---|---|
| SERVER_PORT | No | 8080 | Gateway HTTP port |
| EUREKA_SERVER_URL | No | http://localhost:8761/eureka/ | Eureka registry URL; include URL-encoded Basic Auth credentials if Eureka security is enabled |
| EUREKA_REGISTER_WITH_EUREKA | No | true | Register this gateway instance in Eureka |
| EUREKA_FETCH_REGISTRY | No | true | Fetch the service registry |
| USER_SERVICE_URI | No | lb://user-service | User service route destination |
| PRODUCT_SERVICE_URI | No | lb://product-service | Product service route destination |
| CART_SERVICE_URI | No | lb://cart-service | Cart service route destination |

For local development, set `EUREKA_SERVER_URL` to the local Eureka URL with credentials, for example `http://admin:your-password@localhost:8761/eureka/`. URL-encode reserved characters in credentials. The gateway can start without Eureka; discovery-based requests cannot be routed until the registry and target service are available.

## 4. Run normally (without Docker)

Run from the repository root.

### Linux / macOS

~~~bash
cd api-gateway

export SERVER_PORT=8080
export EUREKA_SERVER_URL='http://admin:replace-with-a-long-random-password@localhost:8761/eureka/'

gradle bootRun
~~~

### Windows PowerShell

~~~powershell
cd api-gateway

$env:SERVER_PORT = "8080"
$env:EUREKA_SERVER_URL = "http://admin:replace-with-a-long-random-password@localhost:8761/eureka/"

gradle bootRun
~~~

Run tests and build the executable JAR:

~~~bash
gradle clean test bootJar
~~~

The service uses the Gradle 9.9.1 toolchain. Install Gradle 9.9.1 locally for these commands; CI and the Docker build provision this version automatically.

## 5. Run with root Docker Compose

The API Gateway is included in the central `docker/docker-compose.yml`.

### Step 1 — Prepare environment

From the repository root:

~~~bash
cp docker/.env.example docker/.env
~~~

Set a strong `EUREKA_PASSWORD` in `docker/.env`. Keep the gateway bound to loopback for local development:

~~~dotenv
EUREKA_USERNAME=admin
EUREKA_PASSWORD=replace-with-a-long-random-password
EUREKA_BIND_IP=127.0.0.1
EUREKA_HOST_PORT=8761
GATEWAY_BIND_IP=127.0.0.1
GATEWAY_HOST_PORT=8080
~~~

If the Eureka password contains URL-reserved characters, set `EUREKA_SERVER_URL` explicitly with URL-encoded credentials.

### Step 2 — Build and start

~~~bash
docker compose -f docker/docker-compose.yml up -d --build eureka-service api-gateway
~~~

The gateway does not have a hard startup dependency on Eureka. It can start while Eureka is unavailable and retry discovery/registration as the registry becomes available.

### Step 3 — Inspect

~~~bash
docker compose -f docker/docker-compose.yml ps
docker compose -f docker/docker-compose.yml logs -f api-gateway
~~~

Check the health endpoint at http://localhost:8080/actuator/health.

### Stop services

~~~bash
docker compose -f docker/docker-compose.yml stop api-gateway
~~~

Stop the entire Compose stack:

~~~bash
docker compose -f docker/docker-compose.yml down
~~~

## 6. Testing independently

The application context test disables Eureka discovery, so `gradle test` does not require a running Eureka server or backend microservices. Route-level integration tests with mock downstream services can be added as the service contracts become available.

## 7. CI

The workflow at `.github/workflows/gateway-ci.yml`:
- Runs on changes under `api-gateway/**`, `docker/**`, or the workflow itself.
- Sets up Java 21 and Gradle 9.9.1.
- Runs `clean test bootJar`.
- Validates the root Docker Compose configuration.
- Builds the API Gateway image through root Compose.
- Uploads test reports and the executable JAR.
