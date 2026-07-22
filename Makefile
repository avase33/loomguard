.PHONY: up down infra test build-web run load

up:
	docker compose up --build

infra:
	docker compose --profile infra up --build

down:
	docker compose down

test:
	cd api-java && mvn -B test

build-web:
	cd dashboard-ts && npm install && npm run build

# Run the API with no external services (in-memory bus + feature store).
run:
	cd api-java && mvn -B spring-boot:run

# Load test with one virtual thread per client: make load CONC=5000 SECS=20
CONC ?= 2000
SECS ?= 15
load:
	cd api-java && mvn -q -B compile exec:java \
	  -Dexec.mainClass=com.loomguard.loadtest.LoadHarness \
	  -Dexec.args="http://localhost:8080 $(CONC) $(SECS)"
