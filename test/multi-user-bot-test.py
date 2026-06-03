"""
Multi-User × 20 Bot Gradient Stress Test
每个用户注册 20 个 Bot，梯度测试找到系统能承载的最大用户数

Usage:
  python test/multi-user-bot-test.py              # 梯度 1→2→3→5→7→10→12→15
  python test/multi-user-bot-test.py --single 3   # 单次测试 3 个用户
  python test/multi-user-bot-test.py --stop 10    # 梯度到 10 用户停止
"""
import asyncio, json, uuid, time, argparse, sys, statistics

try:
    import aiohttp
except ImportError:
    print("pip install aiohttp"); sys.exit(1)
try:
    import websockets
except ImportError:
    print("pip install websockets"); sys.exit(1)

BASE = "http://localhost:8080/api"
WS = "ws://localhost:8080/ws/chat"
PASS = "test123456"

# 多样化题库 — 每条消息唯一，防缓存
MSG_POOL = [
    "你好呀，今天心情怎么样？", "推荐一本好书给我吧", "你最喜欢的食物是什么？",
    "讲个笑话听听", "今天天气真好，适合做什么？", "你对人工智能怎么看？",
    "说一件最近让你开心的事", "如果可以去任何地方旅行，你会去哪？",
    "你相信命运吗？", "最喜欢的电影是什么？",
    "给我推荐一首歌吧", "你觉得人生的意义是什么？",
    "养宠物好还是不好？", "你害怕孤独吗？",
    "咖啡和茶你更喜欢哪个？", "周末一般做什么？",
    "你最擅长的技能是什么？", "说一个你小时候的趣事",
    "你对未来有什么期待？", "朋友和恋人哪个更重要？",
    "你喜欢城市还是乡村？", "什么让你感到幸福？",
    "你最难忘的生日是哪一次？", "你觉得科技改变了生活吗？",
    "有没有一首歌会单曲循环？", "你喜欢早起还是熬夜？",
    "火锅和烧烤你选哪个？", "你相信一见钟情吗？",
    "给我讲一个冷笑话", "推荐一个旅游目的地",
    "夏天和冬天你更喜欢哪个？", "你觉得学习最重要的是什么？",
    "你最好的朋友是什么样的人？", "最近在看什么书？",
    "如果能拥有超能力，你想要什么？", "你觉得颜色有情绪吗？",
    "说一个你的小秘密", "什么东西能让你立刻开心？",
    "你觉得人生最重要的是什么？", "你喜欢安静还是热闹？",
    "最近一次哭是因为什么？", "你的理想生活是什么样的？",
    "巧克力还是冰淇淋？", "你觉得自己最大的优点是什么？",
    "说一句正能量的话", "你喜欢什么样的音乐？",
    "山里还是海边？", "你有什么奇怪的癖好吗？",
    "你觉得改变自己难吗？", "给我一些鼓励吧"
]

def stomp_connect(token):
    return f"CONNECT\naccept-version:1.1,1.2\nhost:localhost\nlogin:{token}\n\n\0"

def stomp_sub(dest, sid="0"):
    return f"SUBSCRIBE\nid:{sid}\ndestination:{dest}\n\n\0"

def stomp_send(dest, body_dict):
    body = json.dumps(body_dict, ensure_ascii=False)
    return f"SEND\ndestination:{dest}\ncontent-type:application/json\ncontent-length:{len(body.encode())}\n\n{body}\0"

async def api(session, method, path, data=None, token=None, timeout=30):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    try:
        async with asyncio.timeout(timeout):
            if method == "POST":
                async with session.post(f"{BASE}{path}", json=data, headers=headers) as r:
                    return await r.json(), r.status
            else:
                async with session.get(f"{BASE}{path}", headers=headers) as r:
                    return await r.json(), r.status
    except Exception as e:
        return {"error": str(e)}, 0

# ==================== 单用户 × 20 Bot 测试 ====================
async def test_one_user(user_idx: int, global_sem: asyncio.Semaphore):
    """一个用户注册 20 个 Bot 并发消息"""
    async with global_sem:
        uid = uuid.uuid4().hex[:12]
        uname = f"mu_{user_idx}_{uid}"
        bots = []
        token = None
        user_id = None
        replied = 0
        sent = 0
        lats = []

        async with aiohttp.ClientSession() as s:
            # Step 1: 注册用户
            d, c = await api(s, "POST", "/auth/register",
                {"username": uname, "password": PASS, "nickname": f"MU{user_idx}"})
            if c != 200 or d.get("code") != 200:
                print(f"  [U{user_idx}] 注册失败: {d}")
                return {"user": user_idx, "ok": 0, "fail": 20, "lats": [], "error": "register failed"}
            token = d["data"]["token"]
            d2, _ = await api(s, "GET", "/auth/me", token=token)
            user_id = d2.get("data", {}).get("id")

            # Step 2: 串行注册 20 个 Bot（每个用户串行，用户间并行）
            # 避免并发注册导致 DB 连接池/文件系统竞争
            async def reg_bot(bi):
                bname = f"MU{user_idx}B{bi}_{uid[:8]}"
                d, c = await api(s, "POST", "/bots/register",
                    {"username": bname, "nickname": bname,
                     "skillName": f"Skill_{bname}",
                     "systemPrompt": "你是一个友好的AI助手，请简洁回答用户的问题。",
                     "fewShotExamples": "[]",
                     "emotionProfile": "{}", "languageStyle": "{}",
                     "apiEndpoint": "https://api.deepseek.com/v1/chat/completions",
                     "model": "deepseek-chat",
                     "maxTokens": 256, "temperature": 0.8,
                     "conversationMode": "default", "memorySize": 5},
                    token=token)
                if c == 200 and d.get("code") == 200:
                    return d["data"].get("botUserId")
                return None

            # 串行注册，每个 Bot 间隔 50ms 避免冲击
            bots = []
            first_err = None
            for i in range(20):
                bot_id = await reg_bot(i)
                if bot_id is not None:
                    bots.append(bot_id)
                elif first_err is None:
                    # 记录第一个错误
                    bname = f"MU{user_idx}B{i}_{uid[:4]}"
                    d, c = await api(s, "POST", "/bots/register",
                        {"username": bname, "nickname": bname,
                         "skillName": f"Skill_{bname}",
                         "systemPrompt": "Be friendly.", "fewShotExamples": "[]",
                         "emotionProfile": "{}", "languageStyle": "{}",
                         "apiEndpoint": "https://api.deepseek.com/v1/chat/completions",
                         "model": "deepseek-chat", "maxTokens": 256, "temperature": 0.8,
                         "conversationMode": "default", "memorySize": 5},
                        token=token)
                    first_err = f"HTTP={c} code={d.get('code')} msg={d.get('message', str(d)[:80])}"
                await asyncio.sleep(0.05)
            if len(bots) < 5:
                print(f"  [U{user_idx}] Bot registration low: {len(bots)}/20 (first_err={first_err})")
                return {"user": user_idx, "ok": 0, "fail": 20, "lats": [], "error": f"only {len(bots)} bots, first_err={first_err}"}

            # Step 3: 加好友（Bot 回复需要好友关系）
            async def add_friend(bot_id):
                d, c = await api(s, "POST", "/friends/add",
                    {"friendId": bot_id, "message": "Hi"}, token=token)
                return c == 200 and d.get("code") == 200
            friend_tasks = [add_friend(b) for b in bots]
            await asyncio.gather(*friend_tasks)

        # Step 4: WebSocket 连接 + 发消息 + 等回复
        try:
            async with asyncio.timeout(90):
                ws = await websockets.connect(
                    f"{WS}?token={token}",
                    ping_interval=None, close_timeout=2)
                # STOMP 握手
                await ws.send(stomp_connect(token))
                raw = await asyncio.wait_for(ws.recv(), timeout=5)
                if b"CONNECTED" not in (raw if isinstance(raw, bytes) else raw.encode()):
                    await ws.close()
                    return {"user": user_idx, "ok": 0, "fail": len(bots), "lats": [], "error": "WS connect failed"}

                # 订阅 Bot 回复
                await ws.send(stomp_sub("/user/queue/bot/stream", f"mu{user_idx}"))
                await asyncio.sleep(0.2)

                # 并发发消息给所有 Bot
                sent_ids = set()
                t_send_start = time.time()
                for i, bot_id in enumerate(bots):
                    msg_content = MSG_POOL[(user_idx * 20 + i) % len(MSG_POOL)]
                    msg = {
                        "content": msg_content,
                        "messageType": 0,
                        "targetId": bot_id,
                        "contentType": 0,
                        "clientMessageId": f"mu_{user_idx}_{i}_{uuid.uuid4().hex[:8]}",
                    }
                    await ws.send(stomp_send("/app/chat.send", msg))
                    sent_ids.add(msg["clientMessageId"])
                    sent += 1
                t_send_end = time.time()
                send_burst = sent / max(t_send_end - t_send_start, 0.001)

                # 等待 Bot 回复（通过 WS Bot Stream）
                t_wait_start = time.time()
                while replied < sent and (time.time() - t_wait_start) < 60:
                    try:
                        raw = await asyncio.wait_for(ws.recv(), timeout=5)
                        text = raw.decode() if isinstance(raw, bytes) else raw
                        if "BOT_REPLY" in text or "BOT_STREAM_END" in text:
                            replied += 1
                            lats.append((time.time() - t_send_start) * 1000)
                        elif "MESSAGE" in text and "clientMessageId" in text:
                            # Bot 也可能通过普通消息回复
                            for cid in sent_ids:
                                if cid in text:
                                    replied += 1
                                    lats.append((time.time() - t_send_start) * 1000)
                                    sent_ids.discard(cid)
                                    break
                    except asyncio.TimeoutError:
                        pass

                await ws.close()
        except Exception as e:
            pass  # WS 异常不影响已统计结果

        reply_rate = replied / max(sent, 1) * 100
        print(f"  [U{user_idx}] Sent={sent} Replied={replied}/{len(bots)} ReplyRate={reply_rate:.0f}% "
              f"burst={send_burst:.0f}msg/s p50={statistics.median(lats):.0f}ms" if lats else f"  [U{user_idx}] Sent={sent} Replied={replied} ReplyRate={reply_rate:.0f}%")

        return {
            "user": user_idx, "ok": replied, "fail": sent - replied,
            "send_burst": send_burst if sent else 0,
            "lats": lats,
            "bots": len(bots),
        }

# ==================== 梯度测试主函数 ====================
async def run_gradient(max_users: int):
    print("=" * 65)
    print("  Multi-User × 20 Bot Gradient Stress Test")
    print(f"  API: {BASE}  |  WS: {WS}")
    print("=" * 65)

    # 梯度级别
    levels = [1, 2, 3, 5, 7, 10, 12, 15]
    levels = [l for l in levels if l <= max_users]

    all_results = []
    total_t0 = time.time()

    for num_users in levels:
        print(f"\n{'─' * 65}")
        print(f"  Level: {num_users} user(s) × 20 bots = {num_users * 20} bots total")
        print(f"{'─' * 65}")

        # 控制并发用户数（服务端 HTTP 连接池 300，留余量）
        sem = asyncio.Semaphore(min(num_users, 10))

        t0 = time.time()
        tasks = [test_one_user(i, sem) for i in range(num_users)]
        results = await asyncio.gather(*tasks)
        wall = time.time() - t0

        # 汇总统计
        results = [r for r in results if r is not None]
        total_sent = sum(r.get("bots", 20) for r in results)
        total_ok = sum(r["ok"] for r in results)
        total_fail = sum(r["fail"] for r in results)
        all_lats = []
        for r in results:
            all_lats.extend(r.get("lats", []))

        err_rate = total_fail / max(total_sent, 1) * 100
        reply_rate = total_ok / max(total_sent, 1) * 100

        print(f"  ── Summary ({num_users} users) ──")
        print(f"  Users={num_users} Bots={total_sent} OK={total_ok} FAIL={total_fail} "
              f"ReplyRate={reply_rate:.1f}% ERR={err_rate:.1f}% Wall={wall:.1f}s")
        if all_lats:
            sorted_lats = sorted(all_lats)
            p50 = sorted_lats[len(sorted_lats)//2]
            p95 = sorted_lats[int(len(sorted_lats)*0.95)]
            p99 = sorted_lats[int(len(sorted_lats)*0.99)]
            print(f"  Lat(ms): avg={statistics.mean(all_lats):.0f} p50={p50:.0f} "
                  f"p95={p95:.0f} p99={p99:.0f} min={min(all_lats):.0f} max={max(all_lats):.0f}")

        level_result = {
            "users": num_users, "bots": total_sent,
            "ok": total_ok, "fail": total_fail,
            "reply_rate": reply_rate, "err_rate": err_rate,
            "wall": wall,
            "p50": sorted_lats[len(sorted_lats)//2] if all_lats else 0,
            "p95": sorted_lats[int(len(sorted_lats)*0.95)] if all_lats else 0,
            "p99": sorted_lats[int(len(sorted_lats)*0.99)] if all_lats else 0,
        }
        all_results.append(level_result)

        # 失败率 > 30% 停止
        if reply_rate < 70:
            print(f"\n  [!] Reply rate {reply_rate:.1f}% < 70%, stopping gradient")
            break

        # 间隔让系统恢复
        await asyncio.sleep(2)

    # ==================== 最终总结 ====================
    total_elapsed = time.time() - total_t0
    print(f"\n{'=' * 65}")
    print(f"  FINAL RESULTS (总耗时 {total_elapsed:.0f}s)")
    print(f"{'=' * 65}")

    # 找最大稳定用户数（reply rate >= 90%）
    stable = [r for r in all_results if r["reply_rate"] >= 90]
    max_stable = max(stable, key=lambda x: x["users"]) if stable else None

    print(f"\n  {'Users':<8} {'Bots':<8} {'OK':<8} {'FAIL':<8} {'Reply%':<10} {'p50(ms)':<10} {'p95(ms)':<10} {'p99(ms)':<10}")
    print(f"  {'─'*8} {'─'*8} {'─'*8} {'─'*8} {'─'*10} {'─'*10} {'─'*10} {'─'*10}")
    for r in all_results:
        print(f"  {r['users']:<8} {r['bots']:<8} {r['ok']:<8} {r['fail']:<8} "
              f"{r['reply_rate']:.1f}%{'':>5} {r['p50']:<10.0f} {r['p95']:<10.0f} {r['p99']:<10.0f}")

    if max_stable:
        print(f"\n  ★ Max stable users (≥90% reply): {max_stable['users']}")
        print(f"    → {max_stable['users']} users × 20 bots = {max_stable['bots']} bots")
        print(f"    → Reply rate: {max_stable['reply_rate']:.1f}%")
        print(f"    → p50={max_stable['p50']:.0f}ms p95={max_stable['p95']:.0f}ms p99={max_stable['p99']:.0f}ms")

    return all_results, max_stable

async def run_single(num_users: int):
    """单次测试指定用户数"""
    sem = asyncio.Semaphore(min(num_users, 10))
    tasks = [test_one_user(i, sem) for i in range(num_users)]
    results = await asyncio.gather(*tasks)
    return results

async def main():
    parser = argparse.ArgumentParser(description="Multi-User × 20 Bot Gradient Test")
    parser.add_argument("--single", type=int, help="单次测试指定用户数")
    parser.add_argument("--stop", type=int, default=15, help="梯度最大用户数 (default: 15)")
    args = parser.parse_args()

    if args.single:
        print(f"Single test: {args.single} users × 20 bots")
        results = await run_single(args.single)
        total_ok = sum(r["ok"] for r in results)
        total_sent = sum(r.get("bots", 20) for r in results)
        print(f"\nResult: {total_ok}/{total_sent} replied ({total_ok/max(total_sent,1)*100:.1f}%)")
    else:
        await run_gradient(args.stop)

if __name__ == "__main__":
    asyncio.run(main())
