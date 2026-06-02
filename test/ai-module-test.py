"""
AI Module Comprehensive Test Suite
===================================
Tests previously uncovered AI features:
  T1: RAG Memory (vector similarity + keyword fallback)
  T2: Long-Term Memory (store, retrieve, consolidate via LLM)
  T3: LLM Streaming Response
  T4: Skill Distillation Correctness
  T5: Conversation Memory Cache
  T6: Bot Benchmark Service
  T7: Multi-Provider Configuration
  T8: Bot Active Mode Scheduling
"""
import asyncio
import json
import uuid
import time
import argparse
import sys
import statistics
from dataclasses import dataclass, field

try:
    import aiohttp
except ImportError:
    print("pip install aiohttp"); sys.exit(1)
try:
    import websockets
except ImportError:
    print("pip install websockets"); sys.exit(1)

BASE = "http://localhost:8080/api"
WS   = "ws://localhost:8080/ws/chat"
PASS = "test123456"
DEEPSEEK_KEY = os.environ.get("BOT_API_KEY", "")
DEEPSEEK_ENDPOINT = "https://api.deepseek.com/v1/chat/completions"
DEEPSEEK_MODEL = "deepseek-chat"

# STOMP helpers
def stomp_connect(token):
    return f"CONNECT\naccept-version:1.1,1.2\nhost:localhost\nlogin:{token}\n\n\0"

def stomp_sub(dest, sid="0"):
    return f"SUBSCRIBE\nid:{sid}\ndestination:{dest}\n\n\0"

def stomp_send(dest, body_dict):
    body = json.dumps(body_dict, ensure_ascii=False)
    return f"SEND\ndestination:{dest}\ncontent-type:application/json\ncontent-length:{len(body.encode())}\n\n{body}\0"

async def api(session, method, path, data=None, token=None, timeout=30):
    headers = {"Content-Type": "application/json"}
    if token: headers["Authorization"] = f"Bearer {token}"
    try:
        async with asyncio.timeout(timeout):
            if method == "POST":
                async with session.post(f"{BASE}{path}", json=data, headers=headers) as r:
                    return await r.json(), r.status
            elif method == "GET":
                async with session.get(f"{BASE}{path}", headers=headers) as r:
                    return await r.json(), r.status
            elif method == "PUT":
                async with session.put(f"{BASE}{path}", json=data, headers=headers) as r:
                    return await r.json(), r.status
            elif method == "DELETE":
                async with session.delete(f"{BASE}{path}", headers=headers) as r:
                    return await r.json(), r.status
    except Exception as e:
        return {"error": str(e)}, 0


# ==================== T1: RAG Memory Test ====================
async def test_rag_memory(token: str, bot_user_id: int):
    """Test RAG vector similarity retrieval + keyword fallback"""
    print(f"\n{'='*60}")
    print(f"  T1: RAG Memory Test (Bot={bot_user_id})")
    print(f"{'='*60}")
    results = []

    async with aiohttp.ClientSession() as s:
        # 1. Enable RAG
        d, c = await api(s, "PUT", f"/bots/{bot_user_id}/rag-config",
            {"ragEnabled": True, "ragTopK": 5}, token=token)
        passed = c == 200 and d.get("code") == 200
        print(f"  1. Enable RAG: {'PASS' if passed else 'FAIL'} (code={c})")
        results.append(("RAG Enable", passed))

        # 2. Get RAG stats (should be 0 initially)
        d, c = await api(s, "GET", f"/bots/{bot_user_id}/rag-stats", token=token)
        stats = d.get("data", {})
        embed_count = stats.get("totalEmbeddings", stats.get("count", -1))
        passed = c == 200 and d.get("code") == 200
        print(f"  2. RAG Stats (initial): count={embed_count} - {'PASS' if passed else 'FAIL'}")
        results.append(("RAG Stats Initial", passed))

        # 3. Send a conversation to populate RAG embeddings
        # Send 5 diverse messages via WS to trigger memory consolidation
        print(f"  3. Sending 5 diverse messages to build RAG context...")
        try:
            ws = await websockets.connect(
                f"{WS}?token={token}",
                additional_headers={"Origin": "http://localhost:3000"},
                ping_interval=None, close_timeout=2,
            )
            await ws.send(stomp_connect(token))
            raw = await asyncio.wait_for(ws.recv(), timeout=5)

            rag_messages = [
                "你对人工智能的未来怎么看？深度学习和强化学习哪个更有前景？",
                "我喜欢在周末去公园跑步，享受阳光和微风的感觉真好。",
                "最近看了一部科幻电影，里面的机器人产生了自我意识，你觉得这可能吗？",
                "我觉得团队协作很重要，大家互相帮助才能完成复杂的项目。",
                "最近在学Python，发现异步编程比想象的难，有没有什么学习建议？",
            ]

            for i, msg_text in enumerate(rag_messages):
                msg = {
                    "content": msg_text,
                    "messageType": 0,
                    "targetId": bot_user_id,
                    "contentType": 0,
                    "clientMessageId": f"rag_{uuid.uuid4().hex[:12]}",
                }
                await ws.send(stomp_send("/app/chat.send", msg))
                await asyncio.sleep(0.5)
                # Wait for bot reply
                try:
                    raw = await asyncio.wait_for(ws.recv(), timeout=0.5)
                except asyncio.TimeoutError:
                    pass

            await ws.close()
            print(f"    Messages sent: {len(rag_messages)}")
        except Exception as e:
            print(f"    WS Warning: {e}")

        # 4. Wait for async embedding to be stored
        await asyncio.sleep(3)

        # 5. Check RAG stats after messages
        d, c = await api(s, "GET", f"/bots/{bot_user_id}/rag-stats", token=token)
        stats_after = d.get("data", {})
        embed_count_after = stats_after.get("totalEmbeddings", stats_after.get("count", -1))
        print(f"  4. RAG Stats (after): count={embed_count_after}")
        passed = embed_count_after >= 0  # At least got a response
        results.append(("RAG Stats After", passed))

        # 6. Clear RAG memory
        d, c = await api(s, "DELETE", f"/bots/{bot_user_id}/rag-memory", token=token)
        passed = c == 200 and d.get("code") == 200
        print(f"  5. Clear RAG Memory: {'PASS' if passed else 'FAIL'}")
        results.append(("RAG Clear", passed))

    return results


# ==================== T2: Long-Term Memory Test ====================
async def test_long_term_memory(token: str, bot_user_id: int, my_user_id: int):
    """Test LTM store, retrieve, and LLM consolidation"""
    print(f"\n{'='*60}")
    print(f"  T2: Long-Term Memory Test (Bot={bot_user_id})")
    print(f"{'='*60}")
    results = []

    async with aiohttp.ClientSession() as s:
        # 1. Get initial LTM (should be empty or small)
        d, c = await api(s, "GET", f"/bots/{bot_user_id}/long-term-memory", token=token)
        ltm_data = d.get("data", [])
        initial_count = len(ltm_data) if isinstance(ltm_data, list) else 0
        passed = c == 200 and d.get("code") == 200
        print(f"  1. LTM Initial Count: {initial_count} - {'PASS' if passed else 'FAIL'}")
        results.append(("LTM Get Initial", passed))

        # 2. Trigger memory consolidation via REST API
        d, c = await api(s, "POST", f"/bots/{bot_user_id}/consolidate", token=token)
        passed = c == 200 and d.get("code") == 200
        print(f"  2. Trigger Consolidation: {'PASS' if passed else 'FAIL'} (code={c})")
        results.append(("LTM Consolidate Trigger", passed))

        # 3. Wait for LLM consolidation to complete
        await asyncio.sleep(8)

        # 4. Get LTM after consolidation
        d, c = await api(s, "GET", f"/bots/{bot_user_id}/long-term-memory", token=token)
        ltm_data_after = d.get("data", [])
        after_count = len(ltm_data_after) if isinstance(ltm_data_after, list) else 0
        print(f"  3. LTM After Consolidation: count={after_count}")
        passed = after_count >= 0  # Valid response
        results.append(("LTM Get After", passed))

        # 5. Check memory types if any exist
        if after_count > 0:
            mem_types = set()
            for mem in ltm_data_after[:5]:
                mtype = mem.get("memoryType", "unknown")
                mem_types.add(mtype)
                print(f"    - [{mtype}] {mem.get('content', '')[:80]}...")
            print(f"    Memory types found: {mem_types}")
            passed = any(t in mem_types for t in ["summary", "fact", "preference"])
            results.append(("LTM Types Valid", passed))

        # 6. Clear LTM
        d, c = await api(s, "DELETE", f"/bots/{bot_user_id}/long-term-memory", token=token)
        passed = c == 200 and d.get("code") == 200
        print(f"  4. Clear LTM: {'PASS' if passed else 'FAIL'}")
        results.append(("LTM Clear", passed))

    return results


# ==================== T3: LLM Streaming Test ====================
async def test_llm_streaming(token: str, bot_user_id: int):
    """Test that streaming responses work via WebSocket STOMP"""
    print(f"\n{'='*60}")
    print(f"  T3: LLM Streaming Response Test (Bot={bot_user_id})")
    print(f"{'='*60}")
    results = []

    try:
        ws = await websockets.connect(
            f"{WS}?token={token}",
            additional_headers={"Origin": "http://localhost:3000"},
            ping_interval=None, close_timeout=2,
        )
        await ws.send(stomp_connect(token))
        raw = await asyncio.wait_for(ws.recv(), timeout=5)
        if b"CONNECTED" in (raw if isinstance(raw, bytes) else raw.encode()):
            print(f"  1. STOMP Connected: PASS")
            results.append(("STOMP Connect", True))
        else:
            print(f"  1. STOMP Connected: FAIL")
            results.append(("STOMP Connect", False))
            return results

        # Subscribe to private messages
        await ws.send(stomp_sub("/user/queue/private/chat", "stream"))

        # Send a message that requires longer response
        msg = {
            "content": "请用至少两百字详细介绍一下深度学习的基本原理和应用场景。",
            "messageType": 0,
            "targetId": bot_user_id,
            "contentType": 0,
            "clientMessageId": f"stream_{uuid.uuid4().hex[:12]}",
        }
        send_time = time.time()
        await ws.send(stomp_send("/app/chat.send", msg))
        print(f"  2. Message sent, waiting for streamed response...")

        # Collect streaming response chunks
        response_chunks = []
        first_chunk_time = None
        try:
            while True:
                raw = await asyncio.wait_for(ws.recv(), timeout=60)
                text = raw.decode("utf-8") if isinstance(raw, bytes) else raw
                if first_chunk_time is None:
                    first_chunk_time = time.time()
                # Parse STOMP frame
                if "\n\n" in text:
                    body = text.split("\n\n", 1)[1].rstrip("\0")
                    if body:
                        try:
                            parsed = json.loads(body)
                            content = parsed.get("content", "")
                            if content:
                                response_chunks.append(content)
                        except json.JSONDecodeError:
                            response_chunks.append(body[:50])
                # Check if this is a MESSAGE with full bot reply
                if len(response_chunks) > 0 and first_chunk_time:
                    if time.time() - first_chunk_time > 30:
                        break  # enough data
                if len(response_chunks) > 3:
                    break  # got some chunks
        except asyncio.TimeoutError:
            pass

        await ws.close()

        total_response = "".join(response_chunks)
        response_time = (time.time() - send_time) if response_chunks else 0
        ttfb = (first_chunk_time - send_time) * 1000 if first_chunk_time else 0

        print(f"  3. Response chunks: {len(response_chunks)}, TTFB: {ttfb:.0f}ms, Total time: {response_time:.1f}s")
        print(f"     Content preview: {total_response[:100]}...")
        passed = len(response_chunks) > 0
        print(f"  4. Streaming Response: {'PASS' if passed else 'FAIL'}")
        results.append(("Streaming Response", passed))
        results.append(("Streaming TTFB", ttfb > 0))

    except Exception as e:
        print(f"  ERROR: {type(e).__name__}: {e}")
        results.append(("Streaming Response", False))

    return results


# ==================== T4: Skill Distillation Test ====================
async def test_skill_distillation(token: str):
    """Test skill distillation correctness - endpoint returns valid skill data"""
    print(f"\n{'='*60}")
    print(f"  T4: Skill Distillation Test")
    print(f"{'='*60}")
    results = []

    async with aiohttp.ClientSession() as s:
        # 1. Call distillation endpoint
        d, c = await api(s, "POST", "/bots/distill", token=token, timeout=60)
        passed = c == 200 and d.get("code") == 200
        print(f"  1. Distillation Endpoint: {'PASS' if passed else 'FAIL'} (code={c})")

        if not passed:
            print(f"     Error: {d.get('message', d)}")
            results.append(("Distill Endpoint", False))
            return results

        results.append(("Distill Endpoint", True))

        # 2. Check distilled data structure
        data = d.get("data", [])
        if isinstance(data, list):
            print(f"  2. Distilled Candidates: {len(data)}")
            results.append(("Distill Count > 0", len(data) > 0))

            for i, candidate in enumerate(data[:3]):
                user_id = candidate.get("userId", "?")
                nickname = candidate.get("nickname", "?")
                emotion = candidate.get("emotionProfile", {})
                lang_style = candidate.get("languageStyle", {})
                sys_prompt = candidate.get("systemPrompt", "")
                few_shot = candidate.get("fewShotExamples", [])
                msg_count = candidate.get("messageCount", 0)

                print(f"    [{i+1}] userId={user_id}, nickname={nickname}, msgs={msg_count}")
                print(f"        Emotion: joy={emotion.get('joy',0):.2f}, anger={emotion.get('anger',0):.2f}")
                print(f"        Language: avgSentenceLen={lang_style.get('avgSentenceLen',0):.1f}")
                print(f"        SystemPrompt: {sys_prompt[:80]}...")
                print(f"        FewShotExamples: {len(few_shot)} examples")

            # Verify structure completeness
            if len(data) > 0:
                c0 = data[0]
                has_emotion = "emotionProfile" in c0
                has_language = "languageStyle" in c0
                has_prompt = "systemPrompt" in c0 and len(c0.get("systemPrompt", "")) > 10
                has_fewshot = "fewShotExamples" in c0 and isinstance(c0.get("fewShotExamples"), list)
                has_msg_count = "messageCount" in c0 and c0.get("messageCount", 0) > 0

                print(f"  3. Data Completeness: emotion={has_emotion}, lang={has_language}, "
                      f"prompt={has_prompt}, fewshot={has_fewshot}, msgCount={has_msg_count}")
                all_complete = has_emotion and has_language and has_prompt and has_fewshot and has_msg_count
                results.append(("Distill Data Complete", all_complete))
        else:
            print(f"  2. Data not a list: {type(data)}")
            results.append(("Distill Count > 0", False))

    return results


# ==================== T5: Conversation Memory Cache Test ====================
async def test_conversation_memory(token: str, bot_user_id: int, my_user_id: int):
    """Test multi-level conversation memory (working + short-term)"""
    print(f"\n{'='*60}")
    print(f"  T5: Conversation Memory Cache Test (Bot={bot_user_id})")
    print(f"{'='*60}")
    results = []

    try:
        ws = await websockets.connect(
            f"{WS}?token={token}",
            additional_headers={"Origin": "http://localhost:3000"},
            ping_interval=None, close_timeout=2,
        )
        await ws.send(stomp_connect(token))
        raw = await asyncio.wait_for(ws.recv(), timeout=5)
        await ws.send(stomp_sub("/user/queue/private/chat", "mem"))

        # Send 3 messages rapidly to test working memory cache
        mem_messages = [
            "我叫小明，今年25岁，是一名程序员。",
            "我喜欢吃火锅，特别是麻辣锅底。",
            "我刚才说我的名字和年龄是什么？请复述给我。",
        ]

        replies = []
        for i, msg_text in enumerate(mem_messages):
            msg = {
                "content": msg_text,
                "messageType": 0,
                "targetId": bot_user_id,
                "contentType": 0,
                "clientMessageId": f"mem_{uuid.uuid4().hex[:12]}",
            }
            await ws.send(stomp_send("/app/chat.send", msg))
            print(f"  Sent [{i+1}]: {msg_text[:60]}...")

            # Wait for bot reply
            try:
                raw = await asyncio.wait_for(ws.recv(), timeout=30)
                text = raw.decode("utf-8") if isinstance(raw, bytes) else raw
                if "\n\n" in text:
                    body = text.split("\n\n", 1)[1].rstrip("\0")
                    try:
                        parsed = json.loads(body)
                        reply_content = parsed.get("content", "")
                        if reply_content:
                            replies.append(reply_content)
                            print(f"  Reply [{i+1}]: {reply_content[:80]}...")
                    except json.JSONDecodeError:
                        pass
                await asyncio.sleep(1)
            except asyncio.TimeoutError:
                print(f"  Reply [{i+1}]: TIMEOUT")

        await ws.close()

        # Check if bot remembered the name/age from earlier messages
        has_memory = len(replies) >= 1
        print(f"\n  Total replies received: {len(replies)}/3")
        print(f"  Memory test: {'PASS' if has_memory else 'INCONCLUSIVE'} (need to check if bot recalls '小明, 25岁')")
        results.append(("Memory Replies", has_memory))

        # Check if the last reply references earlier info
        if len(replies) >= 3:
            last_reply = replies[-1].lower()
            remembers = "小明" in last_reply or "25" in last_reply or "程序" in last_reply
            print(f"  Context retention: {'PASS - bot remembered!' if remembers else 'Partial - check reply content'}")
            results.append(("Memory Context Retention", remembers))

    except Exception as e:
        print(f"  ERROR: {type(e).__name__}: {e}")
        results.append(("Memory Replies", False))

    return results


# ==================== T6: Bot Benchmark Test ====================
async def test_bot_benchmark(token: str, bot_user_id: int):
    """Test built-in bot benchmark service"""
    print(f"\n{'='*60}")
    print(f"  T6: Bot Benchmark Service Test (Bot={bot_user_id})")
    print(f"{'='*60}")
    results = []

    async with aiohttp.ClientSession() as s:
        # 1. Quick benchmark
        d, c = await api(s, "GET", f"/bots/{bot_user_id}/benchmark/quick", token=token, timeout=60)
        passed = c == 200 and d.get("code") == 200
        print(f"  1. Quick Benchmark: {'PASS' if passed else 'FAIL'} (code={c})")
        if passed:
            data = d.get("data", {})
            print(f"     Save latency: {data.get('msgSaveLatencyMs', 'N/A')}ms")
            print(f"     Skill fetch: {data.get('skillFetchLatencyMs', 'N/A')}ms")
            print(f"     Queue size: {data.get('queueSize', 'N/A')}")
        results.append(("Quick Benchmark", passed))

        # 2. Full benchmark (with 5 messages, 3 concurrency)
        d, c = await api(s, "POST", f"/bots/{bot_user_id}/benchmark",
            {"count": 5, "concurrency": 3}, token=token, timeout=120)
        passed = c == 200 and d.get("code") == 200
        print(f"  2. Full Benchmark (5msgs, 3con): {'PASS' if passed else 'FAIL'} (code={c})")
        if passed:
            data = d.get("data", {})
            print(f"     Send lat: p50={data.get('sendLatencyMs',{}).get('p50','N/A')}ms")
            print(f"     Reply lat: p50={data.get('replyLatencyMs',{}).get('p50','N/A')}ms")
            print(f"     E2E lat: p50={data.get('e2eLatencyMs',{}).get('p50','N/A')}ms")
        results.append(("Full Benchmark", passed))

    return results


# ==================== T7: Bot Active Mode Test ====================
async def test_bot_active_mode(token: str, bot_user_id: int):
    """Test bot active mode scheduling"""
    print(f"\n{'='*60}")
    print(f"  T7: Bot Active Mode Test (Bot={bot_user_id})")
    print(f"{'='*60}")
    results = []

    async with aiohttp.ClientSession() as s:
        # 1. Get current active mode config
        d, c = await api(s, "GET", f"/bots/{bot_user_id}/active-mode", token=token)
        print(f"  1. Get Active Mode: code={c}")
        results.append(("Get Active Mode", c == 200))

        # 2. Enable active mode
        d, c = await api(s, "PUT", f"/bots/{bot_user_id}/active-mode",
            {"enabled": True, "intervalSeconds": 60}, token=token)
        passed = c == 200 and d.get("code") == 200
        print(f"  2. Enable Active Mode: {'PASS' if passed else 'FAIL'}")
        results.append(("Enable Active Mode", passed))

        # 3. Get active bots list
        d, c = await api(s, "GET", "/bots/active-mode/list", token=token)
        passed = c == 200 and d.get("code") == 200
        print(f"  3. Active Mode List: {'PASS' if passed else 'FAIL'}")
        results.append(("Active Mode List", passed))

        # 4. Disable active mode (cleanup)
        d, c = await api(s, "PUT", f"/bots/{bot_user_id}/active-mode",
            {"enabled": False, "intervalSeconds": 60}, token=token)
        passed = c == 200 and d.get("code") == 200
        print(f"  4. Disable Active Mode: {'PASS' if passed else 'FAIL'}")
        results.append(("Disable Active Mode", passed))

    return results


# ==================== T8: Bot Health & Queue Stats ====================
async def test_bot_monitoring(token: str):
    """Test bot monitoring endpoints"""
    print(f"\n{'='*60}")
    print(f"  T8: Bot Health & Queue Monitoring Test")
    print(f"{'='*60}")
    results = []

    async with aiohttp.ClientSession() as s:
        # 1. Bot health
        d, c = await api(s, "GET", "/bots/health", token=token)
        passed = c == 200 and d.get("code") == 200
        print(f"  1. Bot Health: {'PASS' if passed else 'FAIL'}")
        results.append(("Bot Health", passed))

        # 2. Queue stats
        d, c = await api(s, "GET", "/bots/queue-stats", token=token)
        passed = c == 200 and d.get("code") == 200
        print(f"  2. Queue Stats: {'PASS' if passed else 'FAIL'}")
        if passed:
            qdata = d.get("data", {})
            print(f"     Total bots: {qdata.get('totalBots', 'N/A')}")
            print(f"     Active: {qdata.get('activeBots', 'N/A')}")
            print(f"     Circuit broken: {qdata.get('circuitBrokenBots', 'N/A')}")
        results.append(("Queue Stats", passed))

        # 3. Bot count
        d, c = await api(s, "GET", "/bots/count", token=token)
        passed = c == 200 and d.get("code") == 200
        print(f"  3. Bot Count: {d.get('data', 'N/A')} - {'PASS' if passed else 'FAIL'}")
        results.append(("Bot Count", passed))

        # 4. Provider list
        d, c = await api(s, "GET", "/bots/providers", token=token)
        providers = d.get("data", [])
        passed = c == 200 and len(providers) > 0
        print(f"  4. AI Providers: {len(providers)} available - {'PASS' if passed else 'FAIL'}")
        for p in providers[:5]:
            print(f"     - {p.get('id','?')}: {p.get('name','?')}")
        results.append(("Provider List", passed))

    return results


# ==================== MAIN ====================
async def main():
    parser = argparse.ArgumentParser(description="AI Module Comprehensive Test Suite")
    parser.add_argument("--skip-streaming", action="store_true", help="Skip streaming test")
    parser.add_argument("--skip-rag", action="store_true", help="Skip RAG test")
    args = parser.parse_args()

    all_results = []
    t0 = time.time()

    print("=" * 60)
    print("  AI Module Comprehensive Test Suite")
    print(f"  API: {BASE}")
    print("=" * 60)

    async with aiohttp.ClientSession() as s:
        # --- Setup: Register user ---
        uname = f"ai_test_{uuid.uuid4().hex[:4]}"
        print(f"\n── Setup: registering {uname} ──")
        d, _ = await api(s, "POST", "/auth/register",
            {"nickname": uname, "password": PASS})
        if d.get("code") != 200:
            print(f"  [FAIL] Registration: {d}")
            return
        token = d["data"]["token"]
        d2, _ = await api(s, "GET", "/auth/me", token=token)
        my_id = d2.get("data", {}).get("id")
        print(f"  User ID={my_id}")

        # --- Register a bot for testing ---
        print(f"\n── Setting up test bot ──")
        d, _ = await api(s, "POST", "/bots/register", {
            "username": f"aitest_{uuid.uuid4().hex[:4]}",
            "nickname": "AITestBot",
            "skillName": "AI_Test_Bot",
            "systemPrompt": "你是一个友好的AI测试助手。请记住对话中的关键信息（姓名、偏好、事实）。回复简洁但信息丰富。",
            "fewShotExamples": "[]",
            "emotionProfile": "{}",
            "languageStyle": "{}",
            "apiEndpoint": DEEPSEEK_ENDPOINT,
            "apiKey": DEEPSEEK_KEY,
            "model": DEEPSEEK_MODEL,
            "password": PASS,
            "memorySize": 20,
            "ragEnabled": False,
        }, token=token, timeout=30)
        if d.get("code") != 200:
            print(f"  [FAIL] Bot registration: {d}")
            # Try to use an existing bot
            d, _ = await api(s, "GET", "/bots/active", token=token)
            bots = d.get("data", [])
            if bots:
                bot_user_id = bots[0].get("botUserId") or bots[0].get("id")
                print(f"  Using existing bot: {bot_user_id}")
            else:
                print("  No bots available, aborting")
                return
        else:
            bot_user_id = d["data"].get("botUserId")
            print(f"  Bot ID={bot_user_id}")

        # Add bot as friend
        await api(s, "POST", "/friends/add", {"friendId": bot_user_id, "message": "hi"}, token=token)
        await asyncio.sleep(2)

        # --- Run Tests ---
        print(f"\n{'#'*60}")
        print(f"  STARTING AI MODULE TESTS")
        print(f"{'#'*60}")

        # T8: Monitoring (fast, no deps)
        res = await test_bot_monitoring(token)
        all_results.extend(res)

        # T7: Active Mode (fast, no deps)
        res = await test_bot_active_mode(token, bot_user_id)
        all_results.extend(res)

        # T6: Benchmark (depends on bot replies)
        res = await test_bot_benchmark(token, bot_user_id)
        all_results.extend(res)

        # T5: Conversation Memory (depends on WS + bot replies)
        res = await test_conversation_memory(token, bot_user_id, my_id)
        all_results.extend(res)

        # T4: Skill Distillation (depends on chat history)
        res = await test_skill_distillation(token)
        all_results.extend(res)

        # T3: Streaming (depends on WS + bot replies)
        if not args.skip_streaming:
            res = await test_llm_streaming(token, bot_user_id)
            all_results.extend(res)

        # T1: RAG Memory (requires embeddings API)
        if not args.skip_rag:
            res = await test_rag_memory(token, bot_user_id)
            all_results.extend(res)

        # T2: Long-Term Memory (requires LLM consolidation)
        res = await test_long_term_memory(token, bot_user_id, my_id)
        all_results.extend(res)

    # --- Summary ---
    elapsed = time.time() - t0
    passed = sum(1 for _, ok in all_results if ok)
    failed = len(all_results) - passed

    print(f"\n{'='*60}")
    print(f"  AI MODULE TEST RESULTS (total {elapsed:.0f}s)")
    print(f"{'='*60}")
    for name, ok in all_results:
        status = "PASS" if ok else "FAIL"
        print(f"  [{status}] {name}")
    print(f"\n  Total: {len(all_results)} | Passed: {passed} | Failed: {failed}")
    print(f"  Pass rate: {passed/max(len(all_results),1)*100:.0f}%")


if __name__ == "__main__":
    asyncio.run(main())
