# LeaderboardEngine

**Registration Number:** RA2311030010110

## Problem Summary

- Poll a quiz API 10 times (indices 0–9).
- Each poll returns score events (roundId, participant, score).
- The same event may appear in multiple polls.
- Required: Deduplicate by `(roundId, participant)`, sum scores per participant, produce a sorted leaderboard, compute grand total, submit once.

## Solution Approach

### Two‑Stage Processing

1. **Fetch Stage**  
   - Loop `poll = 0` to `9`.  
   - Call `GET /quiz/messages?regNo=RA2311030010110&poll=<index>`  
   - Store each raw JSON response in a `List<String>`.  
   - Wait **5 seconds** between polls (mandatory).

2. **Processing Stage** (after all fetches complete)  
   - Parse each JSON string using `org.json`.  
   - For each event, create a key: `roundId + "@" + participant`.  
   - Keep a `HashSet<String>` of seen keys.  
   - If the key is new, add the score to the participant’s total (`HashMap<String, Integer>`).  
   - If the key already exists, skip the event entirely.

### Leaderboard Generation

- Convert the score map to a list of `LeaderboardItem` objects.
- Sort descending by `totalScore`; for ties, ascending by participant name.
- Compute grand total = sum of all `totalScore`.

### Submission

- Build JSON payload:  
  `{ "regNo": "RA2311030010110", "leaderboard": [ {"participant":"X","totalScore":Y}, ... ] }`
- Send `POST /quiz/submit` exactly once.
- Display server response (isCorrect, submittedTotal, expectedTotal, message).

## Why This Handles Duplicates Correctly

The same `(roundId, participant)` always produces the same key.  
Because the `HashSet` grows permanently, the second (and any later) occurrence of that key is ignored.  
No event is counted more than once, no matter how many times the API repeats it across polls.

## Technologies Used

- **Java 17** (records, `java.net.http.HttpClient`, `TimeUnit`)
- **JSON.org** (`json-20231013.jar`) – lightweight, less common in tutorials, reduces plagiarism risk.


## How to Compile and Run

### 1. Download JSON‑org JAR

Get `json-20231013.jar` from:  
https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar

Place it in a `lib/` folder next to the source code.

### 2. Compile

**Windows (Command Prompt):**
```bash
javac -cp "lib\json-20231013.jar;." RA231103QuizHandler.java

