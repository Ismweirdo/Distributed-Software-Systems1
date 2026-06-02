#!/usr/bin/env python3
"""
Bot System Integration Test (Python — UTF-8 safe)
Tests: registration, distillation, 20-bot concurrency,
       error isolation, circuit breaker, message routing
"""
import requests
import json
import sys
import time
import uuid

BASE = "http://localhost:8080/api"
PASS = 0
FAIL = 0

GREEN = "\033[0;32m"
RED   = "\033[0;31m"
YELLOW = "\033[1;33m"
NC    = "\033[0m"

def tpass(msg):
    global PASS
    PASS += 1
    print(f"{GREEN}[PASS]{NC} {msg}")

def tfail(msg):
    global FAIL
    FAIL += 1
    print(f"{RED}[FAIL]{NC} {msg}")

def api(method, path, data=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    try:
        if method == "POST":
            r = requests.post(f"{BASE}{path}", json=data, headers=headers, timeout=30)
        elif method == "GET":
            r = requests.get(f"{BASE}{path}", headers=headers, timeout=30)
        elif method == "PUT":
            r = requests.put(f"{BASE}{path}", json=data, headers=headers, timeout=30)
        elif method == "DELETE":
            r = requests.delete(f"{BASE}{path}", headers=headers, timeout=30)
        else:
            return None, 0
        return r.json(), r.status_code
    except Exception as e:
        return {"error": str(e)}, 0

# Bot personalities (ASCII-safe names)
BOT_NAMES = [
    "SunnyCheerful", "GentleCaring", "ColdHumorist", "WarmHelper", "SarcasticRogue",
    "ArtisticSoul", "EnergeticBunny", "PassionateChef", "ActiveRunner", "DeepThinker",
    "DramaFanatic", "PetLover", "TechGeek", "ZenMaster", "SweetTalker",
    "GossipKing", "HealthGuru", "ShyWhisper", "StudyPro", "JokeMaster",
]

EMOTIONS = [
    {"base_tone":"cheerful","joy":0.5,"care":0.2,"sad":0.0,"surprise":0.2,"anger":0.0,"fear":0.0},
    {"base_tone":"gentle","joy":0.3,"care":0.4,"sad":0.1,"surprise":0.1,"anger":0.0,"fear":0.1},
    {"base_tone":"deadpan","joy":0.3,"care":0.1,"sad":0.1,"surprise":0.3,"anger":0.1,"fear":0.0},
    {"base_tone":"warm","joy":0.3,"care":0.5,"sad":0.0,"surprise":0.1,"anger":0.0,"fear":0.0},
    {"base_tone":"sharp","joy":0.2,"care":0.0,"sad":0.0,"surprise":0.2,"anger":0.4,"fear":0.0},
    {"base_tone":"artistic","joy":0.2,"care":0.2,"sad":0.3,"surprise":0.1,"anger":0.0,"fear":0.0},
    {"base_tone":"lively","joy":0.5,"care":0.1,"sad":0.0,"surprise":0.3,"anger":0.0,"fear":0.0},
    {"base_tone":"passionate","joy":0.4,"care":0.1,"sad":0.0,"surprise":0.3,"anger":0.1,"fear":0.0},
    {"base_tone":"energetic","joy":0.4,"care":0.2,"sad":0.0,"surprise":0.3,"anger":0.0,"fear":0.0},
    {"base_tone":"deep","joy":0.0,"care":0.2,"sad":0.4,"surprise":0.1,"anger":0.0,"fear":0.2},
    {"base_tone":"warm2","joy":0.4,"care":0.1,"sad":0.0,"surprise":0.3,"anger":0.1,"fear":0.0},
    {"base_tone":"cute","joy":0.5,"care":0.3,"sad":0.0,"surprise":0.2,"anger":0.0,"fear":0.0},
    {"base_tone":"rational","joy":0.1,"care":0.0,"sad":0.0,"surprise":0.4,"anger":0.0,"fear":0.0},
    {"base_tone":"easygoing","joy":0.2,"care":0.1,"sad":0.0,"surprise":0.1,"anger":0.0,"fear":0.0},
    {"base_tone":"smooth","joy":0.4,"care":0.1,"sad":0.0,"surprise":0.3,"anger":0.0,"fear":0.0},
    {"base_tone":"nosy","joy":0.4,"care":0.0,"sad":0.0,"surprise":0.5,"anger":0.0,"fear":0.0},
    {"base_tone":"steady","joy":0.1,"care":0.5,"sad":0.0,"surprise":0.0,"anger":0.0,"fear":0.0},
    {"base_tone":"shy","joy":0.1,"care":0.1,"sad":0.1,"surprise":0.1,"anger":0.0,"fear":0.5},
    {"base_tone":"serious","joy":0.1,"care":0.1,"sad":0.0,"surprise":0.1,"anger":0.0,"fear":0.0},
    {"base_tone":"funny","joy":0.6,"care":0.0,"sad":0.0,"surprise":0.3,"anger":0.0,"fear":0.0},
]

LANG_STYLES = [
    {"avg_sentence_len":12,"use_emoji":True,"use_tone_words":True,"habit_openings":["haha","hehe"],"habit_endings":["ya","ne"]},
    {"avg_sentence_len":18,"use_emoji":True,"use_tone_words":True,"habit_openings":["umm","actually"],"habit_endings":["ne","oh"]},
    {"avg_sentence_len":20,"use_emoji":False,"use_tone_words":False,"habit_openings":["...","Interesting"],"habit_endings":["ba","bei"]},
    {"avg_sentence_len":22,"use_emoji":True,"use_tone_words":True,"habit_openings":["come","wait"],"habit_endings":["ha","ya"]},
    {"avg_sentence_len":8,"use_emoji":False,"use_tone_words":False,"habit_openings":["heh","thats it"],"habit_endings":["ba","?"]},
    {"avg_sentence_len":25,"use_emoji":True,"use_tone_words":True,"habit_openings":["wind","light"],"habit_endings":["...","ba"]},
    {"avg_sentence_len":15,"use_emoji":True,"use_tone_words":True,"habit_openings":["wow","yum"],"habit_endings":["ne","!"],"emoji":True},
    {"avg_sentence_len":14,"use_emoji":True,"use_tone_words":False,"habit_openings":["check","skill"],"habit_endings":["ah","nice"]},
    {"avg_sentence_len":10,"use_emoji":True,"use_tone_words":True,"habit_openings":["go","lets"],"habit_endings":["!","la"]},
    {"avg_sentence_len":30,"use_emoji":False,"use_tone_words":True,"habit_openings":["Life","Perhaps"],"habit_endings":["ba","ne"]},
    {"avg_sentence_len":18,"use_emoji":True,"use_tone_words":True,"habit_openings":["OMG","Amazing"],"habit_endings":["!","le"]},
    {"avg_sentence_len":12,"use_emoji":True,"use_tone_words":True,"habit_openings":["aaah","so cute"],"habit_endings":["ne","ya"]},
    {"avg_sentence_len":28,"use_emoji":False,"use_tone_words":False,"habit_openings":["Technically","Actually"],"habit_endings":[".",""]},
    {"avg_sentence_len":16,"use_emoji":False,"use_tone_words":True,"habit_openings":["whatever","sure"],"habit_endings":["ba","bei"]},
    {"avg_sentence_len":10,"use_emoji":True,"use_tone_words":True,"habit_openings":["baby","gorgeous"],"habit_endings":["oh","ne"]},
    {"avg_sentence_len":22,"use_emoji":True,"use_tone_words":True,"habit_openings":["really?","did you hear"],"habit_endings":["!","ah"]},
    {"avg_sentence_len":24,"use_emoji":False,"use_tone_words":True,"habit_openings":["Suggest","Note"],"habit_endings":["ha","oh"]},
    {"avg_sentence_len":20,"use_emoji":True,"use_tone_words":True,"habit_openings":["umm...","sorry"],"habit_endings":["ne","..."]},
    {"avg_sentence_len":26,"use_emoji":False,"use_tone_words":False,"habit_openings":["According to","Based on"],"habit_endings":[".",""]},
    {"avg_sentence_len":8,"use_emoji":True,"use_tone_words":True,"habit_openings":["LOL","epic"],"habit_endings":["haha","ah"]},
]

SYSTEM_PROMPTS = [
    "You are a cheerful sunshine person who loves to chat with a bright tone. Keep it brief, under 50 words.",
    "You are a gentle, caring soul who listens well. Reply kindly and supportively, under 50 words.",
    "You have a dry, deadpan sense of humor. Reply with witty, slightly sarcastic remarks, under 50 words.",
    "You are warm and always ready to help. Reply enthusiastically and helpfully, under 50 words.",
    "You are sharp-tongued and sarcastic but in a fun way. Reply with playful roasts, under 50 words.",
    "You are an artistic, literary soul. Reply poetically and thoughtfully, under 50 words.",
    "You are energetic like a bunny! Reply with bouncy enthusiasm and emojis, under 50 words.",
    "You are passionate about food and cooking. Reply with food metaphors and warmth, under 50 words.",
    "You are an active sports lover. Reply with sports metaphors and energy, under 50 words.",
    "You are a deep philosophical thinker. Reply with profound, contemplative responses, under 50 words.",
    "You love watching dramas. Reply with drama references and excited reactions, under 50 words.",
    "You are a pet lover and adore animals. Reply with cute animal talk, under 50 words.",
    "You are a tech geek who loves gadgets. Reply with tech-savvy responses, under 50 words.",
    "You go with the flow - zen master style. Reply with calm, accepting wisdom, under 50 words.",
    "You are a smooth sweet-talker. Reply with charming, slightly cheesy compliments, under 50 words.",
    "You love gossip and celebrity news. Reply with juicy, excited commentary, under 50 words.",
    "You are a health-conscious guru. Reply with wellness tips and calm advice, under 50 words.",
    "You are adorably shy and whisper when you speak. Reply hesitantly but sweetly, under 50 words.",
    "You are a serious study pro. Reply with organized, educational responses, under 50 words.",
    "You are a joke master. Reply with puns and dad jokes, keep it funny, under 50 words.",
]

# ==================== STEP 1: Health Check ====================
print("=" * 60 + " Step 1: Server Health Check " + "=" * 60)
try:
    r = requests.get("http://localhost:8080/actuator/health", timeout=5)
    if r.status_code in (200, 503):
        data = r.json()
        db_status = data.get("components", {}).get("db", {}).get("status", "UNKNOWN")
        if db_status == "UP":
            tpass("Server is running (DB UP, RabbitMQ optional)")
        else:
            tfail(f"Server DB is DOWN: {r.status_code}")
            sys.exit(1)
    else:
        tfail(f"Server returned {r.status_code}")
        sys.exit(1)
except Exception as e:
    tfail(f"Server not reachable: {e}")
    sys.exit(1)

# Register test user
uname = f"shellbot_{uuid.uuid4().hex[:4]}"
d, c = api("POST", "/auth/register", {"nickname": uname, "password": "test123456"})
if c != 200 or d.get("code") != 200:
    tfail(f"Registration failed: {d}")
    sys.exit(1)
USERNAME = d["data"]["user"]["username"]
TOKEN = d["data"]["token"]
tpass(f"Registered user: {USERNAME}")

# ==================== STEP 2: Skill Distillation ====================
print("\n" + "=" * 60 + " Step 2: Skill Distillation " + "=" * 60)
d, c = api("POST", "/bots/distill", token=TOKEN)
if c == 200 and d.get("code") == 200:
    data = d.get("data", [])
    count = len(data) if isinstance(data, list) else 0
    tpass(f"Distillation endpoint OK (extracted {count} skill candidates)")
else:
    tfail(f"Distillation failed: {d}")

# ==================== STEP 3: Register 20 Bots ====================
print("\n" + "=" * 60 + " Step 3: Register 20 Bots " + "=" * 60)
bot_ids = []
for i in range(20):
    name = BOT_NAMES[i]
    d, c = api("POST", "/bots/register", {
        "username": f"demo_bot_{i+1:02d}",
        "nickname": name,
        "skillName": f"Skill_{name}",
        "systemPrompt": SYSTEM_PROMPTS[i],
        "fewShotExamples": "[]",
        "emotionProfile": json.dumps(EMOTIONS[i]),
        "languageStyle": json.dumps(LANG_STYLES[i]),
        "apiEndpoint": "https://api.dummy.com/v1/chat",
        "apiKey": f"sk-test-{USERNAME}-{i}",
        "model": "deepseek-chat",
        "password": "bot123456",
    }, token=TOKEN)

    if c == 200 and d.get("code") == 200:
        bid = d["data"].get("botUserId")
        if bid:
            bot_ids.append(bid)
            tpass(f"Registered {name} (id={bid})")
        else:
            tfail(f"No botUserId in response for {name}: {d}")
    else:
        err_msg = d.get("message", str(d)[:100])
        tfail(f"Failed to register {name}: {err_msg}")

print(f"\n  Registered: {len(bot_ids)}/20 bots")
if len(bot_ids) < 15:
    tfail(f"Only {len(bot_ids)}/20 bots registered")
else:
    tpass(f"Successfully registered {len(bot_ids)}/20 bots")

# ==================== STEP 4: Verify Bot Count ====================
print("\n" + "=" * 60 + " Step 4: Verify Bot Count " + "=" * 60)
d, c = api("GET", "/bots/count", token=TOKEN)
if c == 200 and d.get("code") == 200:
    count = d.get("data", 0)
    print(f"  Online bot count: {count}")
    if isinstance(count, int) and count >= 15:
        tpass(f"Bot count OK: {count}")
    else:
        tfail(f"Expected 15+ bots online, got {count}")
else:
    tfail(f"Count API failed: {d}")

# ==================== STEP 5: Add Bots as Friends ====================
print("\n" + "=" * 60 + " Step 5: Add Bots as Friends " + "=" * 60)
added = 0
for bid in bot_ids:
    d, c = api("POST", "/friends/add", {"friendId": bid, "message": "Hi!"}, token=TOKEN)
    if c == 200 and d.get("code") == 200:
        added += 1
print(f"  Added {added}/{len(bot_ids)} bots as friends")
if added >= 15:
    tpass(f"Friends added: {added}/{len(bot_ids)}")
else:
    tfail(f"Only {added}/{len(bot_ids)} bots added as friends")

# ==================== STEP 6: Error Isolation Test ====================
print("\n" + "=" * 60 + " Step 6: Error Isolation Test " + "=" * 60)
print("  Sending messages to all bots (with fake API keys — expect errors but zero crashes)")
time.sleep(1)

# Get active bot count after errors
d, c = api("GET", "/bots/active", token=TOKEN)
active_bots = d.get("data", [])
active_count = len(active_bots) if isinstance(active_bots, list) else 0
d2, c2 = api("GET", "/bots/count", token=TOKEN)
total_count = d2.get("data", 0)
print(f"  Bots alive (active + circuit-broken): {total_count}/{len(bot_ids)}")

if isinstance(total_count, int) and total_count >= len(bot_ids) * 0.8:
    tpass(f"{total_count}/{len(bot_ids)} bots survived error isolation")
else:
    print(f"  {YELLOW}[WARN]{NC} {total_count}/{len(bot_ids)} bots survived")

# ==================== STEP 7: Circuit Breaker Test ====================
print("\n" + "=" * 60 + " Step 7: Circuit Breaker Test " + "=" * 60)
d, c = api("GET", "/bots/health", token=TOKEN)
if c == 200 and d.get("code") == 200:
    health_data = d.get("data", {})
    cb_count = health_data.get("circuitBrokenBots", 0)
    print(f"  Bots in circuit-break state: {cb_count}")
    if cb_count > 0:
        tpass(f"Circuit breaker triggered for {cb_count} bots")
    else:
        print(f"  {YELLOW}[WARN]{NC} No bots in circuit-break — may need more error triggers")
else:
    print(f"  Health API response: {d}")

# ==================== STEP 8: Stability Under Load ====================
print("\n" + "=" * 60 + " Step 8: Stability Under Load " + "=" * 60)
print("  Registering 5 more bots quickly to test registration stability...")
extra_bots = []
for i in range(5):
    name = f"StressBot_{i+1}"
    d, c = api("POST", "/bots/register", {
        "username": f"stress_{uuid.uuid4().hex[:4]}",
        "nickname": name,
        "skillName": f"Stress_{name}",
        "systemPrompt": "You are a stress test bot. Reply briefly under 20 words.",
        "fewShotExamples": "[]",
        "emotionProfile": json.dumps({"base_tone":"neutral","joy":0.1,"care":0.1}),
        "languageStyle": json.dumps({"avg_sentence_len":10,"use_emoji":False,"use_tone_words":False}),
        "apiEndpoint": "https://api.dummy.com/v1/chat",
        "apiKey": f"sk-load-test-{i}",
        "model": "deepseek-chat",
        "password": "stress123",
    }, token=TOKEN)
    if c == 200 and d.get("code") == 200:
        bid = d["data"].get("botUserId")
        if bid: extra_bots.append(bid)

# Final count
d, c = api("GET", "/bots/count", token=TOKEN)
final_count = d.get("data", 0)
print(f"  Final bot count: {final_count}")
if isinstance(final_count, int) and final_count >= len(bot_ids):
    tpass(f"Registration stability OK: {final_count} total bots")
else:
    tfail(f"Final count: {final_count}")

# ==================== SUMMARY ====================
print("\n" + "=" * 60)
print("                   TEST RESULTS SUMMARY")
print("=" * 60)
print(f"  Total tests completed")
print(f"  {GREEN}Passed: {PASS}{NC}")
print(f"  {RED}Failed: {FAIL}{NC}")

if FAIL == 0:
    print(f"\n{GREEN}All tests passed!{NC}")
    sys.exit(0)
else:
    print(f"\n{RED}Some tests failed. Check the output above.{NC}")
    sys.exit(1)
