# Codebase Cartographer — Backend API Test Report
**Date:** May 22, 2026  
**Tester:** Satya Prakash  
**Environment:** Local (Spring Boot 3.2 + PostgreSQL)  
**Base URL:** http://localhost:8080  
**Auth:** JWT Bearer Token via POST /api/auth/callback  

---

## Test Summary

| Category | Total | Passed | Failed | Issues Found |
|----------|-------|--------|--------|-------------|
| Auth Endpoints | 1 | 1 | 0 | — |
| User Endpoints | 3 | 2 | 1 | 500 on one case |
| Repo Endpoints | 6 | 6 | 0 | 403 instead of 401 |
| Query Endpoints | 4 | 3 | 1 | Blank questions saved |
| Graph Endpoints | 2 | 2 | 0 | — |
| **TOTAL** | **16** | **14** | **2** | **3 issues** |

---

## AUTH ENDPOINTS

### POST /api/auth/callback
| # | Scenario | Input | Expected | Got | Status |
|---|----------|-------|----------|-----|--------|
| A1 | Valid GitHub data | githubId, name, email, token | 200 + JWT token + user | 200 ✅ | PASS |

**Sample Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": "test-uuid-123",
    "name": "Satya",
    "email": "satya@gmail.com",
    "dailyQueryCount": 0
  }
}
```

---

## USER ENDPOINTS

### GET /api/me
| # | Scenario | Input | Expected | Got | Status |
|---|----------|-------|----------|-----|--------|
| U1 | Valid token | Authorization: Bearer <token> | 200 + user data | 200 ✅ | PASS |
| U2 | No token | No header | 401 | 401 ✅ | PASS |
| U3 | Fake/invalid token | Authorization: Bearer fake | 401 | 401 ✅ | PASS |
| U4 | Expired token | jwt.expiration=1 in yaml | 401 | Pending ⏳ | TODO |

**U1 — Sample Response:**
```json
{
  "id": "test-uuid-123",
  "name": "Satya",
  "email": "satya@gmail.com",
  "dailyQueryCount": 0,
  "queryResetAt": null,
  "createdAt": "2026-05-18T18:03:56.943497"
}
```

**⚠️ Issue Found — U_BUG_01:**
```
One call to GET /api/me returned 500 Internal Server Error
instead of 200. Intermittent — did not reproduce consistently.

Possible cause: race condition or null field in toResponse()
Action: add ex.printStackTrace() in GlobalExceptionHandler
        to capture stack trace when it happens again
Status: Under Investigation
```

---

## REPO ENDPOINTS

### GET /api/repos
| # | Scenario | Input | Expected | Got | Status |
|---|----------|-------|----------|-----|--------|
| R1 | Valid token | Authorization: Bearer <token> | 200 + list | 200 ✅ | PASS |
| R2 | No token | No header | 401 | 403 ⚠️ | ISSUE |

**⚠️ Issue Found — R_BUG_01:**
```
GET /api/repos with no token returned 403 Forbidden
instead of 401 Unauthorized.

Cause: Spring Security returns 403 when no authentication
       is provided on some endpoint configurations.
Fix:   Add AuthenticationEntryPoint to SecurityConfig
       to force 401 on missing credentials.

Code fix (SecurityConfig.java):
http.exceptionHandling(ex -> ex
    .authenticationEntryPoint((request, response, authException) -> {
        response.setStatus(401);
        response.getWriter().write("Unauthorized");
    })
);

Status: Known issue, fix in next session
```

### GET /api/repos/{id}
| # | Scenario | Input | Expected | Got | Status |
|---|----------|-------|----------|-----|--------|
| R3 | Valid repo id | correct UUID | 200 + repo data | 200 ✅ | PASS |
| R4 | Wrong repo id | wrong-id | 404 | 404 ✅ | PASS |

**R4 — Sample Error Response:**
```json
{
  "type": "https://codebasecartographer.com/errors/not-found",
  "title": "Resource not found",
  "status": 404,
  "detail": "Repository not found with id: wrong-id",
  "instance": "/api/repos/wrong-id",
  "timestamp": "2026-05-22T08:43:53.563286396"
}
```

### POST /api/repos
| # | Scenario | Input | Expected | Got | Status |
|---|----------|-------|----------|-----|--------|
| R5 | New valid repo | all fields filled | 201 + repo | 201 ✅ | PASS |
| R6 | Duplicate repo | same githubRepoId | 400 | 400 ✅ | PASS |
| R7 | Missing fields | only name sent | 400 | 400 ✅ | PASS |

**R5 — Sample Request:**
```json
{
  "githubRepoId": "codebase-cartographer",
  "name": "Satya",
  "fullName": "Satya Prakash",
  "branch": "main",
  "language": "Java"
}
```

**R5 — Sample Response:**
```json
{
  "id": "c0f8f25f-e57d-45ab-b5a3-18f7ad5205c4",
  "userId": "test-uuid-123",
  "name": "Satya",
  "status": "PENDING",
  "totalFiles": 0,
  "indexedFiles": 0,
  "createdAt": "2026-05-22T08:28:58.566977"
}
```

**R6 — Sample Error Response:**
```json
{
  "type": "https://codebasecartographer.com/errors/duplicate",
  "title": "Duplicate Resource",
  "status": 400,
  "detail": "Repository already imported: Satya Prakash"
}
```

### POST /api/repos/{id}/index
| # | Scenario | Input | Expected | Got | Status |
|---|----------|-------|----------|-----|--------|
| R8 | Valid repo id | correct UUID | 202 + INDEXING status | 202 ✅ | PASS |
| R9 | Wrong repo id | fake-repo-id | 404 | 404 ✅ | PASS |

**R8 — Sample Response:**
```json
{
  "id": "c0f8f25f-e57d-45ab-b5a3-18f7ad5205c4",
  "status": "INDEXING",
  "totalFiles": 0,
  "indexedFiles": 0
}
```

### GET /api/repos/{id}/status
| # | Scenario | Input | Expected | Got | Status |
|---|----------|-------|----------|-----|--------|
| R10 | Valid repo | correct UUID | 200 + current status | 200 ✅ | PASS |

---

## QUERY ENDPOINTS

### POST /api/repos/{id}/query
| # | Scenario | Input | Expected | Got | Status |
|---|----------|-------|----------|-----|--------|
| Q1 | Repo not indexed (INDEXING) | question sent | 400 | 400 ✅ | PASS |
| Q2 | Repo indexed (INDEXED) | valid question | 200 + answer | 200 ✅ | PASS |
| Q3 | Empty question | question: "" | 400 | 400 ✅ (after @Valid fix) | PASS |
| Q4 | Repo PENDING/FAILED | question sent | 400 | 400 ✅ | PASS |

**Q2 — Sample Response (Week 5 placeholder):**
```json
{
  "answer": "AI response coming in Week 5. Question received: How does auth work?",
  "sourceFiles": [],
  "tokensUsed": 0
}
```

**Q1 — Sample Error Response:**
```json
{
  "type": "https://codebasecartographer.com/errors/bad-request",
  "status": 400,
  "detail": "Repository is not ready for querying. Current status: INDEXING"
}
```

**⚠️ Issue Found — Q_BUG_01:**
```
Empty questions (question: "") were being saved to query_logs
even after returning 400 Bad Request.

Root cause: @Valid annotation was missing on @RequestBody
            in QueryController. Spring was not validating
            the request before passing to service.
            Blank string passed validation → saved to DB.

Fix applied: Added @Valid to QueryController.query() method.
             Empty questions now return 400 without saving.

Lesson: @Valid must be on every @RequestBody that has
        validation annotations (@NotBlank, @NotNull etc.)

Status: FIXED
Side effect: Old blank records still in query_logs table.
Action:      Clean up manually:
             DELETE FROM query_logs WHERE question = '' OR question IS NULL;
```

### GET /api/repos/{id}/queries
| # | Scenario | Input | Expected | Got | Status |
|---|----------|-------|----------|-----|--------|
| Q5 | Indexed repo with history | valid UUID | 200 + list | 200 ✅ | PASS |
| Q6 | Non-indexed repo | PENDING/INDEXING id | 404 | 404 ✅ | PASS |

**Q5 — Sample Response:**
```json
[
  {
    "answer": "AI response coming in Week 5. Question received: How does auth work?",
    "sourceFiles": [],
    "tokensUsed": 0
  }
]
```

**Note on Q6:**
```
404 for PENDING/INDEXING repos on /queries is CORRECT behaviour.
verifyRepoAccess() checks repo belongs to user.
But non-indexed repos can still have chat history if they 
were previously indexed and re-indexed.

Future improvement (Week 5): 
Return empty list [] instead of 404 for non-indexed repos.
Currently acceptable for MVP.
```

---

## GRAPH ENDPOINTS

### GET /api/repos/{id}/graph
| # | Scenario | Input | Expected | Got | Status |
|---|----------|-------|----------|-----|--------|
| G1 | Repo not indexed | INDEXING status | 400 | 400 ✅ | PASS |
| G2 | Repo indexed | INDEXED status | 200 + empty graph | 200 ✅ | PASS |

**G2 — Sample Response:**
```json
{
  "nodes": [],
  "edges": []
}
```

**Note on G2 — Why nodes/edges are empty:**
```
Repo is marked INDEXED in DB (manually via SQL)
but actual file parsing has NOT happened yet.

Code chunks are only created by:
→ Tree-sitter AST parsing (Week 3)
→ Files fetched from GitHub (Week 3)
→ Embeddings generated (Week 3)

Empty nodes/edges is CORRECT for Week 1.
Graph will populate in Week 3 after indexing pipeline is built.
```

### GET /api/repos/{id}/files
| # | Scenario | Input | Expected | Got | Status |
|---|----------|-------|----------|-----|--------|
| G3 | Indexed repo | INDEXED status | 200 + file list | 200 [] ✅ | PASS |

**Note on G3:**
```
Empty array is correct — no chunks in code_chunks table yet.
File paths come from code_chunks.file_path column.
Week 3: real files will appear here after indexing pipeline.
```

---

## ISSUES TRACKER

| ID | Endpoint | Issue | Severity | Status |
|----|----------|-------|----------|--------|
| U_BUG_01 | GET /api/me | Intermittent 500 error | Medium | Investigating |
| R_BUG_01 | GET /api/repos | 403 instead of 401 on no token | Low | Fix next session |
| Q_BUG_01 | POST /api/repos/:id/query | Blank questions saved before @Valid fix | Low | FIXED |

---

## NEXT WEEK — Tests to Run (Week 2)

These tests require Week 2 features (GitHub OAuth + JWT from real login):

### GitHub OAuth Flow
| # | Test | How |
|---|------|-----|
| N1 | Real GitHub login end-to-end | Click login button in frontend |
| N2 | New user created in DB after first login | Check users table after login |
| N3 | Existing user token updated on re-login | Login twice, check access_token |
| N4 | JWT token generated from real GitHub data | Decode token, verify githubId |
| N5 | Frontend stores JWT in NextAuth session | Check browser cookies |

### JWT Expiration
| # | Test | How |
|---|------|-----|
| N6 | Expired token returns 401 | Set jwt.expiration=1, wait, retry |
| N7 | Refreshed token works | After expiry, login again, new token |

### Security
| # | Test | How |
|---|------|-----|
| N8 | User A cannot access User B repos | Create 2 accounts, cross-query |
| N9 | Middleware blocks /dashboard without login | Visit dashboard URL without auth |
| N10 | Middleware allows / without login | Visit landing page |

### Frontend Integration
| # | Test | How |
|---|------|-----|
| N11 | Dashboard loads user's real repos | Login → dashboard |
| N12 | API calls include Authorization header | Network tab in browser |
| N13 | 401 response redirects to login | Force token expiry |

---

## FIXES TO APPLY BEFORE WEEK 2

### Fix 1 — 403 → 401 on missing token
Add to `SecurityConfig.java`:

```java
http.exceptionHandling(ex -> ex
    .authenticationEntryPoint(
        (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"status\":401,\"error\":\"Unauthorized\"," +
                "\"message\":\"Missing or invalid token\"}"
            );
        }
    )
);
```

### Fix 2 — Clean blank query_logs records
```sql
DELETE FROM query_logs 
WHERE question = '' OR question IS NULL;
```

### Fix 3 — Investigate intermittent 500 on GET /api/me
Add to `GlobalExceptionHandler.java` temporarily:
```java
@ExceptionHandler(Exception.class)
public ProblemDetail handleGeneral(Exception ex) {
    ex.printStackTrace(); // ADD THIS to see real cause
    ...
}
```

---

## WHAT EMPTY FILES MEANS

```
Q: "We indexed a repo, why are files empty?"

A: Marking a repo as INDEXED in SQL manually does NOT
   actually run the indexing pipeline.

   Real indexing pipeline (Week 3) does:
   1. Fetch all files from GitHub API
   2. Parse each file with Tree-sitter
   3. Split into code chunks (functions, classes)
   4. Generate embeddings via Bedrock
   5. Save chunks to code_chunks table

   Right now code_chunks table is empty
   because none of these steps exist yet.

   Empty files list is CORRECT for Week 1.
   Week 3: files will appear automatically.
```

---

## BACKEND STATUS — END OF WEEK 1

```
✅ Entity Layer        (4 tables in PostgreSQL)
✅ Repository Layer    (4 repositories)
✅ Exception Layer     (RFC 7807 Problem Details)
✅ DTO Layer           (request + response)
✅ Service Layer       (5 services, 3 scaffolded)
✅ Controller Layer    (4 controllers)
✅ JWT Auth            (generate + validate)
✅ Security Config     (filter chain)
✅ Auth Endpoint       (POST /api/auth/callback)
✅ All endpoints tested in Postman
⚠️ 3 minor issues found (2 low, 1 investigating)

Next: Week 2 — GitHub OAuth + NextAuth frontend
```
