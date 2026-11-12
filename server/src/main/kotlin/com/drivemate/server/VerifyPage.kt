package com.drivemate.server

/** The phone pages of the mock identity check (the car shows the form's address as a QR code). */
object VerifyPage {
    private fun page(body: String) = """<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>SOMEON 본인 인증</title>
<style>
  body { margin: 0; font-family: -apple-system, 'Apple SD Gothic Neo', 'Noto Sans KR', sans-serif; background: #F2F3F7; color: #151928; }
  main { max-width: 420px; margin: 0 auto; padding: 28px 20px; }
  h1 { font-size: 24px; margin: 8px 0 4px; }
  .brand { font-weight: 800; font-size: 22px; letter-spacing: .06em; color: #665E92; }
  .brand b { color: #F74C93; text-shadow: 0 0 10px rgba(247,76,147,.45); }
  .card { background: #fff; border-radius: 20px; padding: 22px; margin-top: 18px; box-shadow: 0 4px 16px rgba(26,32,64,.08); }
  label { display: block; font-size: 14px; color: #6E7280; margin: 14px 0 6px; }
  input, select { width: 100%; box-sizing: border-box; font-size: 17px; padding: 12px; border: 1px solid #E6E8EF; border-radius: 12px; background: #F7F8FB; }
  button { width: 100%; margin-top: 22px; padding: 16px; font-size: 18px; font-weight: 700; color: #fff; background: #F04D80; border: 0; border-radius: 16px; }
  .note { font-size: 13px; color: #6E7280; line-height: 1.6; margin-top: 16px; }
  .ok { font-size: 48px; text-align: center; } .msg { font-size: 18px; text-align: center; line-height: 1.6; }
</style></head><body><main>$body</main></body></html>"""

    private const val BRAND = """<div class="brand">SOME<b>ON</b></div>"""

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun form(id: String, name: String, alreadyVerified: Boolean) = page(
        if (alreadyVerified) result(true, "이미 본인 인증을 마쳤어요.", wrap = false)
        else """
$BRAND
<h1>휴대폰 본인 인증</h1>
<div class="note">공모전 시연용 모의 인증이에요. 실제 서비스에서는 통신사 본인확인(PASS 등)으로 바뀌어요.</div>
<form class="card" method="post" action="/verify/${escape(id)}">
  <label>이름</label><input name="name" value="${escape(name)}">
  <label>생년월일</label><input name="birth" type="date" value="1995-03-14" required>
  <label>통신사</label><select name="carrier"><option>SKT</option><option>KT</option><option>LG U+</option><option>알뜰폰</option></select>
  <label>휴대폰 번호</label><input name="phone" value="010-0000-0000" inputmode="tel">
  <button type="submit">인증하기</button>
  <div class="note">만 19세 이상만 이용할 수 있어요. 인증 정보는 인증 여부 확인에만 쓰고 저장하지 않아요.</div>
</form>""",
    )

    fun result(ok: Boolean, message: String, wrap: Boolean = true): String {
        val body = """$BRAND<div class="card"><div class="ok">${if (ok) "✅" else "⚠️"}</div><p class="msg">${escape(message)}</p></div>"""
        return if (wrap) page(body) else body
    }

    fun unknown() = result(false, "차 화면의 QR 코드를 다시 찍어 주세요.")
}
