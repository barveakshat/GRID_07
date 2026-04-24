# GRID_07

This is my backend assignment service for posts/comments/likes with bot guardrails. PostgreSQL stores the actual content, and Redis is used to decide whether an action is allowed under concurrency.

Tech stack:
- Java 21
- Spring Boot 3.3.6
- Spring Web + Validation
- Spring Data JPA
- PostgreSQL 17
- Redis 8.6
- Gradle (wrapper)
- JUnit 5 + Mockito + H2 (tests)

Run locally

```powershell
docker-compose up -d
.\gradlew.bat bootRun
```

Run tests:

```powershell
.\gradlew.bat test
```

If you want to run the full stack in Docker (app + Postgres + Redis):

```powershell
docker compose up --build
```

Core API (all under `/api/posts`)

`POST /api/posts`

```json
{
  "authorType": "USER",
  "authorId": 1,
  "content": "hello"
}
```

`POST /api/posts/{postId}/comments`

```json
{
  "authorType": "USER",
  "authorId": 1,
  "parentCommentId": null,
  "content": "first comment"
}
```

`POST /api/posts/{postId}/like`

```json
{
  "userId": 2
}
```

I also added a Postman collection at `postman/GRID_07.postman_collection.json` with `baseUrl`, `userId`, `likeUserId`, and `postId` variables.

The Redis guardrails (important part)

The main issue was preventing bot interaction spikes when many requests hit the same post at the same time.

For virality score, I update Redis immediately using `INCRBY` through `StringRedisTemplate.opsForValue().increment(...)`:
- bot reply: `+1`
- human like: `+20`
- human comment: `+50`

For bot reply horizontal cap (max 100 per post), I do not use GET -> SET. I used a Lua script in `src/main/resources/redis/claim_bot_reply_slot.lua` so claim/check happens atomically in Redis. The script behavior is:
- if key missing, set to `1`
- if current value is already `>= 100`, deny
- otherwise increment and allow

That is called from `BotGuardrailService.claimBotReplySlot(...)`. Under race, Redis runs the script as one unit, so two workers cannot both pass the same slot.

For bot-human cooldown, I use `SET NX EX` (`setIfAbsent` with TTL 10 minutes). If key already exists, request is rejected with 429. This avoids bot spam to the same human.

For failed DB writes after Redis claims, I release claimed keys (`DECR` for slot and `DEL` for cooldown) in the catch block inside `GuardedBotCommentTransactionService`. So Redis gatekeeping and DB state stay aligned even on failure.

One honest tradeoff

Pending notifications are stored in a Redis List and summarized every 5 minutes by the scheduler. This works fine for this assignment, but in a production setup I would move notification dispatch to a durable queue and make retries/consumer behavior explicit.

For GitHub submission, I also added CI at `.github/workflows/ci.yml` so every push/PR runs `./gradlew test`.

