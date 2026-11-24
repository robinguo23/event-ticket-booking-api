# Ticket Booking API

A Spring Boot REST API for creating events and booking tickets. It demonstrates how a transactional booking workflow and optimistic locking can protect a limited ticket inventory when requests arrive concurrently.

## Technology

- Java 21 and Maven Wrapper
- Spring Boot, Spring Web and Spring Data JPA
- H2 in-memory database for local development; PostgreSQL JDBC driver for deployment
- JUnit 5 and Spring Boot Test

## Design

The API separates HTTP handling, booking logic and persistence into controllers, a service and JPA repositories. `BookingService.bookTicket` runs in a single `@Transactional` boundary: it loads the event, checks availability, decrements the remaining tickets and saves a confirmed booking. Both writes commit together; a failure rolls them back together.

`Event` has a JPA `@Version` field. If two transactions read the same version and compete for the last ticket, only one versioned update can commit. The other fails with an optimistic locking exception and its transaction rolls back, including any booking insert. The booking endpoint returns HTTP 409 for an optimistic locking conflict; clients may retry after checking availability. A request that sees no remaining tickets returns HTTP 400. This approach protects against overselling, but does not queue requests or automatically retry them.

## Run locally

Prerequisites: JDK 21 and access to Maven Central on the first run so the wrapper and dependencies can be downloaded. From the project root:

```bash
java -version
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd` in place of `./mvnw`. The API listens on `http://localhost:8080` by default. H2 stores data in memory, so data is discarded on shutdown. On each start the application inserts two sample events: Coldplay London (10 tickets) and Taylor Swift London (5 tickets).

Run the tests with:

```bash
./mvnw verify
```

The test suite includes a concurrent booking test for the last available ticket. It checks that one booking succeeds, inventory reaches zero and a subsequent booking reports a sell-out.

## API

All request and response bodies are JSON. IDs below are examples; use IDs returned by the API.

### List events

```bash
curl -s http://localhost:8080/api/events
```

Returns an array of events with `id`, `title`, `availableTickets` and `version`.

### Create an event

```bash
curl -s -X POST http://localhost:8080/api/events \
  -H 'Content-Type: application/json' \
  -d '{"title":"Example Concert","availableTickets":20}'
```

Returns the saved event, including its generated ID and version.

### Book a ticket

```bash
curl -i -X POST http://localhost:8080/api/bookings \
  -H 'Content-Type: application/json' \
  -d '{"eventId":1,"buyerName":"Alex Smith"}'
```

A successful request returns HTTP 200 and a booking with an ID, event ID, buyer name, `CONFIRMED` status and booking time. An optimistic locking conflict returns HTTP 409 with `{"error":"Too many requests, please try again"}`. A sold-out or missing event, or a missing event ID or buyer name, returns HTTP 400 with an error message, for example `{"error":"Sold out"}`.

### List bookings

```bash
curl -s http://localhost:8080/api/bookings
```

Returns an array of saved bookings.

## PostgreSQL

The application includes the PostgreSQL driver and a commented datasource template in `src/main/resources/application.properties`. To switch, provision a database, comment out the H2 datasource and `ddl-auto` settings, uncomment the PostgreSQL settings, and set `DB_USERNAME` and `DB_PASSWORD` in the environment. Configure the JDBC URL for your instance. The template uses `ddl-auto=validate`, so apply a managed schema migration before starting; this project does not currently include migrations. No application code changes are required for the datasource switch.

The sample-data runner inserts two events on every startup, including when using PostgreSQL. Disable or replace it before deploying against persistent data. Production deployment also requires appropriate authentication, input validation, secrets management and operational configuration.
