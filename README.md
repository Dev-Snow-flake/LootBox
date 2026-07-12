# LootBox

Paper 26.1.2 / Java 25용 가챠 박스 플러그인입니다.

서명된 박스 아이템을 지급하고, 플레이어가 우클릭으로 즉시 오픈하면 설정된 보상 풀에서 가중치 기반으로 보상을 추첨합니다. 박스 발급, 오픈 기록, 보상 내역은 PostgreSQL에 저장됩니다.

## 명령어

| 명령어 | 설명 | 권한 |
|---|---|---|
| `/lootbox` | 가챠 박스 목록 GUI를 엽니다. | `lootbox.use` |
| `/lootbox chance <box_id>` | 특정 박스의 보상 확률 GUI를 엽니다. | `lootbox.use` |
| `/lootbox list` | 설정된 박스 종류를 채팅에 표시합니다. | `lootbox.use` |
| `/lootbox give <player> <box_id> [amount]` | 플레이어에게 서명된 가챠 박스를 지급합니다. | `lootbox.admin` |
| `/lootbox createpool <pool_id>` | 보상 풀 편집 GUI를 열고, 닫을 때 저장합니다. | `lootbox.admin` |
| `/lootbox viewpool <pool_id>` | 활성화된 보상과 계산된 확률을 채팅에 표시합니다. | `lootbox.admin` |
| `/lootbox history <player> [page]` | 플레이어의 가챠 박스 오픈 기록을 조회합니다. | `lootbox.admin` |
| `/lootbox reload` | 설정과 보상 풀 정보를 다시 불러옵니다. | `lootbox.admin` |

별칭: `/gacha`, `/lootboxes`, `/가챠`

## 동작 방식

1. 관리자가 `/lootbox give <player> <box_id> [amount]`로 박스를 지급합니다.
2. 박스 아이템에는 고유 `box_serial`, `lootbox_id`, HMAC 서명이 저장됩니다.
3. 플레이어가 박스를 우클릭하면 DB 상태를 확인하고 중복 오픈을 차단합니다.
4. 보상 풀의 `weight` 값으로 실제 확률을 계산해 보상을 추첨합니다.
5. 인벤토리에 공간이 있으면 즉시 지급하고, 공간이 없으면 Mail 연동으로 발송합니다.
6. 모든 오픈 결과는 `lootbox_history`에 기록됩니다.

## Placeholder

| Placeholder | 설명 |
|---|---|
| `%lootbox_opens_total%` | 전체 가챠 박스 오픈 수 |
| `%lootbox_opens_today%` | 오늘 가챠 박스 오픈 수 |
| `%lootbox_mythic_count%` | MYTHIC 등급 보상 획득 수 |

## 권한

| 권한 | 기본값 | 설명 |
|---|---:|---|
| `lootbox.use` | true | 일반 명령어 사용 및 지급된 박스 오픈 |
| `lootbox.admin` | op | 박스 지급, 보상 풀 편집, 기록 조회, 리로드 |

## 참고

- 운영 전 `config.yml`의 `security.hmac_secret` 값을 서버 전용 비밀키로 변경하는 것을 권장합니다.
- `PlaceholderAPI`, `Mail`, `ItemsAdder`는 선택 연동입니다.
- 기본 DB 설정은 예시 값이므로 실제 서버 환경에 맞게 변경해야 합니다.

## Build

```powershell
.\gradlew.bat releaseJar --no-daemon --max-workers=1
```

완성된 JAR는 `../build_file/LootBox-1.0.0.jar`로 복사됩니다.
