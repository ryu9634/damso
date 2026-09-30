#!/usr/bin/env python3
"""담소 매칭 시스템 더미데이터 시더.

백엔드(8080)가 떠 있는 상태에서 실행한다:
    python3 scripts/seed_dummy.py

- 현장 소개팅: 진행 중(REGISTRATION) 이벤트에 손님 + 호감도 지목 + 메시지
- 손님 선택 소개팅: 커스텀 질문 답변을 포함한 소개팅 프로필
"""
import json
import random
import urllib.error
import urllib.request

BASE = "http://localhost:8080/api"
random.seed(7)

MBTIS = ["INTJ", "INTP", "ENTJ", "ENTP", "INFJ", "INFP", "ENFJ", "ENFP",
         "ISTJ", "ISFJ", "ESTJ", "ESFJ", "ISTP", "ISFP", "ESTP", "ESFP"]


def _req(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        BASE + path, data=data, method=method,
        headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read().decode()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        msg = e.read().decode()
        raise RuntimeError(f"{method} {path} -> {e.code}: {msg}") from None


def get(path):
    return _req("GET", path)


def post(path, body):
    return _req("POST", path, body)


# ─────────────────────────────────────────────────────────
# 현장 소개팅
# ─────────────────────────────────────────────────────────
def seed_onsite():
    events = get("/admin/events")
    active = next((e for e in events if e["status"] == "REGISTRATION"), None)
    if not active:
        print("[현장] REGISTRATION 상태 이벤트가 없어 건너뜁니다.")
        return
    eid = active["id"]
    print(f"[현장] 이벤트 #{eid} ({active['name']})")

    existing = get(f"/admin/customers?eventId={eid}")
    used_seats = {c["seatNumber"] for c in existing}
    nq = len(get("/admin/questions"))  # valuesAnswers 자릿수

    people = [
        ("민준", "김민준", "MALE"), ("도윤", "이도윤", "MALE"),
        ("시우", "박시우", "MALE"), ("준서", "최준서", "MALE"),
        ("서연", "김서연", "FEMALE"), ("지우", "이지우", "FEMALE"),
        ("하은", "박하은", "FEMALE"), ("유나", "정유나", "FEMALE"),
    ]

    created = []
    seat = 1
    for i, (nick, name, gender) in enumerate(people):
        while seat in used_seats:
            seat += 1
        if seat > 9:
            print("[현장] 자리(최대 9)가 가득 차 나머지는 건너뜁니다.")
            break
        va = "".join(random.choice("01") for _ in range(nq))
        body = {
            "eventId": eid, "nickname": nick, "realName": name,
            "phone": f"010-3000-{1000 + i:04d}", "gender": gender,
            "mbti": random.choice(MBTIS), "valuesAnswers": va, "seatNumber": seat,
        }
        try:
            c = post("/admin/customers", body)
            created.append(c)
            used_seats.add(seat)
            seat += 1
        except RuntimeError as e:
            print("  손님 생성 실패:", e)

    print(f"[현장] 손님 {len(created)}명 생성")

    # 호감도 지목: 각자 이성 중 1~3순위
    pref_count = 0
    for c in created:
        opp = [o for o in created if o["gender"] != c["gender"]]
        if not opp:
            continue
        picks = random.sample(opp, min(3, len(opp)))
        body = {
            "customerId": c["id"],
            "firstChoiceId": picks[0]["id"],
            "secondChoiceId": picks[1]["id"] if len(picks) > 1 else None,
            "thirdChoiceId": picks[2]["id"] if len(picks) > 2 else None,
        }
        try:
            post("/guest/preference", body)
            pref_count += 1
        except RuntimeError as e:
            print("  호감도 실패:", e)
    print(f"[현장] 호감도 지목 {pref_count}건")

    # 메시지: 일부가 이성에게 (최대 2명)
    notes = ["오늘 대화 즐거웠어요 :)", "다음에 또 뵙고 싶어요!",
             "가치관이 잘 맞는 것 같아요", "연락 주고받아요~"]
    msg_count = 0
    for c in random.sample(created, min(6, len(created))):
        opp = [o for o in created if o["gender"] != c["gender"]]
        for r in random.sample(opp, min(2, len(opp))):
            body = {"customerId": c["id"], "receiverId": r["id"],
                    "content": random.choice(notes), "senderContact": c["phone"]}
            try:
                post("/guest/message", body)
                msg_count += 1
            except RuntimeError:
                pass  # 최대 인원/중복 제한 등은 무시
    print(f"[현장] 메시지 {msg_count}건")


# ─────────────────────────────────────────────────────────
# 손님 선택 소개팅
# ─────────────────────────────────────────────────────────
HOBBIES = ["등산과 캠핑을 좋아해요", "카페 투어와 사진 찍기", "헬스와 러닝",
           "영화 감상, 요리", "보드게임과 방탈출", "여행과 맛집 탐방"]
PERSONALITY = ["차분하고 다정한 편이에요", "활발하고 리액션이 좋아요",
               "낯은 가리지만 친해지면 수다스러워요", "계획적이고 꼼꼼해요",
               "긍정적이고 잘 웃어요", "호기심 많고 새로운 걸 좋아해요"]
STRENGTHS = ["잘 들어주는 사람", "약속을 잘 지켜요", "유머감각", "배려심",
             "추진력", "손이 야무져요"]
WEAKNESSES = ["가끔 게을러요", "걱정이 많아요", "고집이 조금 있어요",
              "직진 스타일", "느긋한 편", "매운 걸 못 먹어요"]


def answer_for(q):
    label, typ = q["label"], q["type"]
    if typ == "SELECT":
        return random.choice(q["options"]) if q["options"] else ""
    if typ == "NUMBER":
        if "키" in label:
            return str(random.randint(158, 185))
        if "몸무게" in label:
            return str(random.randint(46, 82))
        return str(random.randint(1, 10))
    if "성격" in label:
        return random.choice(PERSONALITY)
    if "취미" in label:
        return random.choice(HOBBIES)
    if "장점" in label:
        return random.choice(STRENGTHS)
    if "단점" in label:
        return random.choice(WEAKNESSES)
    return random.choice(PERSONALITY)


def seed_dating():
    questions = get("/dating/questions")
    if not questions:
        print("[소개팅] 질문이 없어 건너뜁니다.")
        return

    people = [
        ("준혁", "MALE"), ("태윤", "MALE"), ("현우", "MALE"),
        ("소민", "FEMALE"), ("예린", "FEMALE"), ("다현", "FEMALE"),
    ]
    made = []
    for i, (name, gender) in enumerate(people):
        answers = [{"questionId": q["id"], "value": answer_for(q)} for q in questions]
        body = {
            "name": name, "contact": f"@{name}_insta",
            "birthYear": random.randint(1990, 2001), "gender": gender,
            "photoUrl": None, "answers": answers, "agreedPrivacy": True,
        }
        try:
            self = post("/dating/profiles", body)
            made.append((self, gender))
        except RuntimeError as e:
            print("  프로필 실패:", e)
    print(f"[소개팅] 프로필 {len(made)}명 생성")

    # 찜: 이성 프로필 몇 명 찜 (쌍방 매칭도 생기도록)
    like_count = 0
    for self, gender in made:
        opp = [o for o, g in made if g != gender]
        for target in random.sample(opp, min(2, len(opp))):
            try:
                post(f"/dating/profiles/{self['id']}/likes/{target['id']}", None)
                like_count += 1
            except RuntimeError:
                pass
    print(f"[소개팅] 찜 {like_count}건")


if __name__ == "__main__":
    seed_onsite()
    seed_dating()
    print("완료.")