# Split Bill API

A production-quality RESTful API for managing shared expenses within a group, built as a take-home test for **Allo Bank Backend Developer**.

**GitHub Username:** `ahyrnsrlh`  
**Service Charge:** `7%` (computed at runtime â€” see [Personalization](#personalization))

---

## Features

- Create a bill group with a name and list of participants
- Add expenses to a group â€” who paid, total amount, and who it benefits
- Three split strategies: **EQUAL**, **EXACT**, and **PERCENTAGE**
- Deterministic rounding so share totals always equal the original expense exactly
- Settlement calculation (who owes whom) using a greedy debtor-creditor algorithm
- `service_charge_pct` and `service_charge_amount` included in every settlement response
- Full input validation with consistent JSON error responses
- OpenAPI / Swagger UI documentation
- Spring Boot Actuator health endpoint
- Multi-stage Docker build

---

## Architecture

```
com.ahyrnsrlh.splitbill
â”œâ”€â”€ config/         # AppProperties (GitHub username binding), OpenApiConfig
â”œâ”€â”€ controller/     # GroupController, ExpenseController
â”œâ”€â”€ dto/
â”‚   â”œâ”€â”€ request/    # CreateGroupRequest, CreateExpenseRequest, ShareRequest, ParticipantRequest
â”‚   â””â”€â”€ response/   # GroupResponse, ExpenseResponse, SettlementResponse, TransferResponse, â€¦
â”œâ”€â”€ entity/         # BillGroup, Participant, Expense, ExpenseShare, SplitType
â”œâ”€â”€ exception/      # GlobalExceptionHandler, ResourceNotFoundException, BusinessException
â”œâ”€â”€ repository/     # BillGroupRepository, ParticipantRepository, ExpenseRepository, ExpenseShareRepository
â””â”€â”€ service/        # ServiceChargeService, GroupService, ExpenseService, SettlementService
```

Key design principles:
- JPA entities are never exposed through controllers â€” all I/O uses DTOs
- `BigDecimal` with scale=2 and `RoundingMode.HALF_UP` for every monetary value
- Constructor injection throughout; no `@Autowired` field injection
- Settlement logic is isolated in `SettlementService` with no controller dependency

---

## Technology Stack

| Technology | Version |
|---|---|
| Java | 17 (compatible with Java 24) |
| Spring Boot | 3.3.4 |
| Spring Web | â€” |
| Spring Validation | â€” |
| Spring Data JPA | â€” |
| H2 Database | In-memory (no external DB required) |
| SpringDoc OpenAPI | 2.6.0 |
| Spring Boot Actuator | â€” |
| JUnit 5 + Mockito | (via `spring-boot-starter-test`) |
| Maven | 3.9.9 (via Maven Wrapper) |

---

## Prerequisites

- **Java 17+** installed and `JAVA_HOME` configured
- **Maven Wrapper** is included (`./mvnw` / `mvnw.cmd`) â€” no separate Maven installation needed
- **Docker** (optional, for containerised deployment)

---

## How to Run (Maven)

```bash
# Clone the repository
git clone https://github.com/ahyrnsrlh/<repo-name>.git
cd <repo-name>

# Run the application (H2 in-memory, starts on port 8080)
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

To override the GitHub username via environment variable:

```bash
GITHUB_USERNAME=yourname ./mvnw spring-boot:run
```

The application is accessible at: http://localhost:4110

---

## How to Run Tests

```bash
./mvnw test

# Windows
mvnw.cmd test
```

Test coverage includes:
- `ServiceChargeServiceTest` â€” proves the service charge is computed, not hardcoded
- `SettlementServiceTest` â€” 8 unit tests covering all required settlement scenarios (pure Mockito, no Spring context)
- `GroupControllerIntegrationTest` â€” full-stack integration tests via MockMvc

---

## How to Build

```bash
./mvnw clean package

# Windows
mvnw.cmd clean package
```

The fat JAR will be at `target/splitbill-1.0.0.jar`.

---

## Docker

### Build the image

```bash
docker build -t split-bill-api .
```

### Run the container

```bash
docker run -p 8080:8080 split-bill-api

# With a custom GitHub username
docker run -p 8080:8080 -e GITHUB_USERNAME=ahyrnsrlh split-bill-api
```

The Dockerfile uses a two-stage build:
1. **Builder stage** â€” compiles with Maven Wrapper inside an eclipse-temurin JDK 17 Alpine image
2. **Runtime stage** â€” copies only the JAR into a JRE 17 Alpine image, runs as a non-root user

---

## API Endpoints

Base URL: `http://localhost:4110/api/v1`

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/groups` | Create a bill group with participants |
| `GET` | `/groups/{groupId}` | Get group details and expenses |
| `POST` | `/groups/{groupId}/expenses` | Add an expense to a group |
| `GET` | `/groups/{groupId}/expenses` | List all expenses in a group |
| `GET` | `/groups/{groupId}/settlements` | Get settlement summary |

Additional:
- `GET /swagger-ui.html` â€” Interactive API docs
- `GET /actuator/health` â€” Health check

---

## curl Examples

### 1. Create a group

```bash
curl -s -X POST http://localhost:4110/api/v1/groups \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Bali Trip",
    "participants": [
      { "name": "Akhyar" },
      { "name": "Budi" },
      { "name": "Citra" }
    ]
  }' | jq
```

**Response (201 Created):**
```json
{
  "id": 1,
  "name": "Bali Trip",
  "createdAt": "2026-08-20T15:00:00Z",
  "participants": [
    { "id": 1, "name": "Akhyar", "createdAt": "2026-08-20T15:00:00Z" },
    { "id": 2, "name": "Budi",   "createdAt": "2026-08-20T15:00:00Z" },
    { "id": 3, "name": "Citra",  "createdAt": "2026-08-20T15:00:00Z" }
  ],
  "expenses": []
}
```

---

### 2. Get group details

```bash
curl -s http://localhost:4110/api/v1/groups/1 | jq
```

---

### 3. Add an expense â€” EQUAL split

```bash
curl -s -X POST http://localhost:4110/api/v1/groups/1/expenses \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Dinner",
    "amount": 300000.00,
    "paidByParticipantId": 1,
    "splitType": "EQUAL",
    "shares": [
      { "participantId": 1 },
      { "participantId": 2 },
      { "participantId": 3 }
    ]
  }' | jq
```

---

### 4. Add an expense â€” EXACT split

```bash
curl -s -X POST http://localhost:4110/api/v1/groups/1/expenses \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Hotel",
    "amount": 600000.00,
    "paidByParticipantId": 2,
    "splitType": "EXACT",
    "shares": [
      { "participantId": 1, "amount": 200000.00 },
      { "participantId": 2, "amount": 250000.00 },
      { "participantId": 3, "amount": 150000.00 }
    ]
  }' | jq
```

---

### 5. Add an expense â€” PERCENTAGE split

```bash
curl -s -X POST http://localhost:4110/api/v1/groups/1/expenses \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Car rental",
    "amount": 500000.00,
    "paidByParticipantId": 1,
    "splitType": "PERCENTAGE",
    "shares": [
      { "participantId": 1, "percentage": 50 },
      { "participantId": 2, "percentage": 30 },
      { "participantId": 3, "percentage": 20 }
    ]
  }' | jq
```

---

### 6. List all expenses in a group

```bash
curl -s http://localhost:4110/api/v1/groups/1/expenses | jq
```

---

### 7. Get settlement summary

```bash
curl -s http://localhost:4110/api/v1/groups/1/settlements | jq
```

**Example response:**
```json
{
  "groupId": 1,
  "groupName": "Bali Trip",
  "totalExpenses": 1400000.00,
  "service_charge_pct": 7,
  "service_charge_amount": 98000.00,
  "settlements": [
    {
      "fromParticipantId": 3,
      "fromParticipantName": "Citra",
      "toParticipantId": 1,
      "toParticipantName": "Akhyar",
      "amount": 150000.00
    }
  ]
}
```

---

## Personalization

**GitHub username:** `ahyrnsrlh`  
**Service charge percentage:** `7%`

The percentage is **not hardcoded**. At application startup, `ServiceChargeService` computes:

```
serviceChargePct = sum_of_unicode_codepoints(lowercase(username)) % 10
```

For `ahyrnsrlh`:
```
a=97, h=104, y=121, r=114, n=110, s=115, r=114, l=108, h=104
Sum = 987
987 % 10 = 7
```

The `GITHUB_USERNAME` environment variable (defaulting to `ahyrnsrlh`) is read from `application.yml`:

```yaml
app:
  github-username: ${GITHUB_USERNAME:ahyrnsrlh}
```

The `service_charge_amount` in the settlement response is informational only:

```
serviceChargeAmount = totalGroupExpenses Ã— serviceChargePct Ã· 100
```

It does **not** alter individual participant balances or debts.

---

## Monetary Scale and Rounding

All monetary values use `BigDecimal` with scale=2 and `RoundingMode.HALF_UP`.

**EQUAL split** â€” remainder handling:  
Divides using `FLOOR` rounding, then distributes remaining cents to the first N participants deterministically. This guarantees `sum(shares) == expense.amount` exactly, with at most a 1-cent difference between any two shares.

**PERCENTAGE split** â€” remainder handling:  
Each percentage is converted with `HALF_UP` rounding. Any aggregate rounding error is absorbed by adjusting the first participant's share, guaranteeing the total is exact.

**EXACT split** â€” strict validation:  
The sum of provided amounts must equal the expense amount exactly (after normalizing to scale=2), otherwise a `400 Bad Request` is returned.

---

## Settlement Calculation

Uses a greedy debtor-creditor algorithm:

1. Compute each participant's net balance: `paid âˆ’ allocated_share`
2. Sort participants into debtors (negative) and creditors (positive)
3. Repeatedly match the largest debtor with the largest creditor, recording a transfer for the lesser amount
4. Continue until all balances are zero

This produces at most `n-1` transfers for typical balanced cases but does not guarantee the global minimum in all pathological edge cases.

The sum of all participant balances is always zero (conservation of money).

---

## Assumption: Service Charge is Informational

The `service_charge_pct` and `service_charge_amount` fields in the settlement response are **display-only**. They reflect a suggested charge but are not redistributed to participant balances. This avoids ambiguity about how the charge would be fairly allocated across varying expense participation patterns. If the charge should affect individual debts in a future version, a documented allocation rule (e.g., proportional to expense share) would be needed.

---

## Submission Question

> **"What was the hardest design decision you made while building this, and what trade-off did you accept?"**

The hardest design decision was defining how rounding should work for equal and percentage-based splits while guaranteeing that every allocated share still adds up exactly to the original expense. I chose to calculate shares with `BigDecimal` and assign any remaining smallest currency units deterministically, which makes the result reproducible and prevents money from being lost. The trade-off is that one or more participants may receive a share that differs by a minimal rounding unit from the mathematically exact value. I accepted this because maintaining an exact and auditable total is more important than pretending fractional currency units can be paid.

---

## Error Response Format

All errors return a consistent JSON structure:

```json
{
  "timestamp": "2026-08-20T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Exact shares must equal the expense amount. Expected: 600.00, got: 550.00",
  "path": "/api/v1/groups/1/expenses"
}
```

| HTTP Status | When |
|---|---|
| `201 Created` | Successful creation |
| `200 OK` | Successful retrieval |
| `400 Bad Request` | Validation or business rule failure |
| `404 Not Found` | Group or participant not found |
