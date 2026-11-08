function page(title: string, body: string): Response {
  const html = `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title}</title>
<style>
body{max-width:44rem;margin:2.5rem auto;padding:0 1.25rem;font:16px/1.7 system-ui,-apple-system,"Segoe UI",sans-serif;color:#242424;background:#fff}
h1{font-size:1.5rem;margin-bottom:1.5rem}h2{font-size:1.05rem;margin-top:2rem}
ul{padding-left:1.25rem}li{margin:.3rem 0}a{color:#1b5e9a}
footer{margin-top:3rem;color:#666;font-size:.9rem}
</style>
</head>
<body>
${body}
<footer>가볍게 (gabyeop)</footer>
</body>
</html>`;
  return new Response(html, {
    status: 200,
    headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-cache" },
  });
}

export function privacyPage(contactEmail: string): Response {
  const contact = contactEmail.length > 0 ? contactEmail : "서버 관리자";
  return page(
    "개인정보처리방침 - 가볍게",
    `<h1>개인정보처리방침</h1>
<p>가볍게의 동기화 기능을 쓰면 아래 정보를 서버에 저장합니다. 동기화를 쓰지 않는 게스트 모드에서는 서버로 아무것도 보내지 않습니다.</p>
<h2>수집 항목</h2>
<ul>
<li>구글 계정 식별자(id)와 이메일 주소</li>
<li>체중 기록, 식단 기록, 하루 메모, 프로필 정보(성별, 출생 연도, 키, 시작·목표 체중, 활동량)</li>
</ul>
<h2>쓰는 목적</h2>
<p>기기 사이의 백업과 동기화에만 씁니다.</p>
<h2>보관 기간</h2>
<p>계정을 삭제하면 저장된 정보를 즉시 삭제합니다. 이메일로 삭제를 요청해도 확인 후 즉시 삭제합니다.</p>
<h2>제3자 제공</h2>
<p>개인정보를 제3자에게 제공하지 않습니다.</p>
<h2>사진</h2>
<p>식단 사진은 기기 안에만 저장하며 서버로 올리지 않습니다.</p>
<h2>문의</h2>
<p>${escapeHtml(contact)}</p>`,
  );
}

export function deletionPage(contactEmail: string): Response {
  const contact = contactEmail.length > 0 ? contactEmail : "서버 관리자";
  return page(
    "계정 삭제 - 가볍게",
    `<h1>계정 삭제</h1>
<p>앱에서 <strong>설정 &gt; 계정 &gt; 계정 삭제</strong>를 누르면 서버에 저장된 계정과 기록이 바로 삭제되고, 기기의 기록도 지워집니다.</p>
<p>앱을 쓸 수 없으면 아래 메일로 계정 식별자(구글 계정 이메일)를 적어 보내 주세요. 확인 후 삭제합니다.</p>
<p>${escapeHtml(contact)}</p>`,
  );
}

export function homePage(): Response {
  return page(
    "가볍게",
    `<h1>가볍게</h1>
<p>체중과 식단을 기록하고 목표까지 얼마나 왔는지 보는 안드로이드 앱입니다.</p>
<ul>
<li>현재 체중, 감량한 양, 목표까지 남은 무게와 진행률</li>
<li>주마다 체중 변화와 그 주의 식단</li>
<li>체중 곡선과 배지</li>
<li>식약처 음식 영양성분 데이터로 칼로리 계산</li>
</ul>
<p>로그인 없이 쓸 수 있고, 원하면 구글 계정으로 백업·동기화할 수 있습니다.</p>
<p><a href="/privacy">개인정보처리방침</a> · <a href="/account-deletion">계정 삭제</a></p>`,
  );
}

export function healthPage(): Response {
  return new Response("ok", { status: 200, headers: { "content-type": "text/plain; charset=utf-8" } });
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
