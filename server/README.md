# 가볍게 동기화 서버

Cloudflare Workers + D1. 구글 로그인한 사용자의 체중·식단·프로필을 백업하고 기기끼리 맞춘다. 사진은 올리지 않는다.

## 처음 배포

```sh
npm install
npx wrangler login
npx wrangler d1 create gabyeop          # 나온 database_id를 wrangler.toml에 넣기
npx wrangler d1 migrations apply gabyeop --remote
npx wrangler secret put JWT_SECRET      # 32자 이상 임의 문자열
# wrangler.toml [vars]: GOOGLE_WEB_CLIENT_ID(구글 웹 클라이언트 ID), CONTACT_EMAIL
npm run deploy
```

배포 주소를 앱 `local.properties`의 `diet.apiBaseUrl`에, 같은 웹 클라이언트 ID를 `diet.googleWebClientId`에 넣고 다시 빌드한다.

## 확인

```sh
npm run typecheck
npm test
```

## 무료 한도

동기화 1회가 요청 1개, D1 batch 1~2번이라 사용자 수십 명이면 Workers 하루 10만 요청·D1 무료 한도에 한참 못 미친다.

경로: `/v1/auth/google`, `/v1/auth/refresh`, `/v1/auth/logout`, `DELETE /v1/account`, `/v1/sync`, `/privacy`, `/account-deletion`, `/health`
