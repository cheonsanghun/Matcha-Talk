# 랜덤/다대다 채팅 기능 검증 보고서

## 1. 개요
본 문서는 Matcha Talk 서비스의 프론트엔드(Vue 3)와 백엔드(Spring Boot) 구현을 대상으로 요구사항 SFR-304, SFR-307, SFR-308, SFR-309, SFR-406의 충족 여부를 집중적으로 검토한 결과를 정리한다. 이번 개정에서는 실시간 STOMP 연동, 파일 업로드 파이프라인, 친구 팔로우 기반의 재대화 흐름, 번역기 토글, 그룹 영상 통화 다이얼로그가 새롭게 추가·수정되었으며, Section 5의 추가 관찰 사항을 전면 갱신하였다.

## 2. 시스템 개요
- **실시간 메시징**: `/ws-stomp` 엔드포인트를 통해 STOMP/WebSocket을 사용하며, `/topic/rooms/{roomId}` 주제를 구독한다. 텍스트 메시지는 `/app/chat.sendMessage/{roomId}`로 전송되고 `WebSocketChatController`에서 브로드캐스트된다.
- **파일 공유**: `/api/rooms/{roomId}/files` REST API는 multipart 업로드를 받아 서버 로컬 스토리지(`uploads/chat`)에 저장하고, 저장 경로는 `/files/chat/{filename}`으로 제공된다.
- **친구/팔로우**: `Friendship` 엔티티와 `/api/friends` API를 통해 팔로우/언팔로우 및 목록 조회가 가능하며, 팔로우 시 자동으로 1:1 채팅방이 생성된다.
- **번역기**: `/api/translate` REST 엔드포인트를 통해 Papago API 연동 `TranslationService`를 호출하고, 프론트 `ChatPanel`에서 토글된 경우 번역 결과를 출력 및 단어장에 저장할 수 있다.
- **그룹 영상통화**: `GroupCallDialog` 컴포넌트는 다자간 WebRTC Mesh 연결을 구성하며, `SignalMessage`에 `roomId`를 포함해 동일 방 참여자 간 시그널을 라우팅한다.

## 3. 요구사항별 검증 결과
### SFR-304 – 영상 및 텍스트 채팅
- `ChatPanel.vue`가 STOMP 구독을 통해 실시간으로 `ChatMessageResponseDto`를 수신하고, 텍스트/번역/파일 메시지를 분기 렌더링한다.
- `MatchingResult.vue`와 `Chat.vue` 모두 STOMP 클라이언트를 생성해 `ChatPanel`에 전달한다.
- WebRTC 연결은 `MatchingResult.vue`(1:1)와 `GroupCallDialog.vue`(그룹)에 의해 관리되어 영상/오디오 트랙을 정상적으로 교환한다.

### SFR-307 – 이미지/파일 공유
- 백엔드 `ChatService.processFileMessage`가 업로드된 파일을 저장하고 메시지 메타데이터(`fileName`, `mimeType`, `fileUrl`)를 DB에 기록한다.
- `ChatController.uploadFile`는 저장 직후 `/topic/rooms/{roomId}`로 DTO를 브로드캐스트해 상대 참여자에게 즉시 반영되도록 한다.
- 프론트 `ChatPanel`은 파일 선택 시 `uploadRoomFile`을 호출해 서버에 업로드하고, STOMP를 통해 수신된 후 미리보기/다운로드 링크를 표시한다.

### SFR-308 – 친구 추가 및 재대화
- `Friendship` 엔티티 및 `FriendshipController`를 통해 `/api/friends` API를 제공하고, 팔로우 시 `ChatService.getOrCreatePrivateRoom`으로 재대화용 1:1 방을 확보한다.
- `useFriendsStore`가 서버 API를 통해 친구 목록을 동기화하며, `Chat.vue`의 1:1 탭에서 친구 선택 시 `openPrivateRoom`으로 즉시 대화가 재개된다.
- 랜덤 매칭 화면의 `ChatPanel`은 “친구 추가” 버튼으로 해당 API를 호출해 팔로우 상태를 유지한다.

### SFR-309 – 번역기 버튼
- `translator.js`가 `/api/translate`를 호출해 Papago 번역 결과를 받아오며, 번역 토글이 활성화된 경우 메시지 하단에 번역문과 단어장 저장 아이콘을 노출한다.
- 저장된 번역은 `useVocabularyStore`를 통해 기존 단어장 화면에서도 확인 가능하다.

### SFR-406 – 단체 영상통화 버튼
- `Chat.vue` 그룹 채팅 헤더의 “영상 통화” 버튼이 `GroupCallDialog`를 열어 참여자 정보를 전달한다.
- `GroupCallDialog`는 참여자별 `RTCPeerConnection`을 생성하고, `SignalMessage.roomId`로 동일 방 시그널만 수락해 최대 4명의 다자간 통화를 지원한다.

## 4. 테스트 및 검증 절차
1. 두 개 이상의 브라우저 세션을 로그인 시킨 뒤 1:1 및 그룹 채팅 방에 입장한다.
2. 텍스트 메시지를 송신해 상대 화면에 즉시 표시되는지 확인한다.
3. 이미지와 일반 파일을 업로드해 미리보기 및 다운로드 링크가 동기화되는지 확인한다.
4. 번역기 토글을 활성화한 후 메시지 번역과 단어장 저장 흐름을 점검한다.
5. 친구 추가 버튼으로 팔로우 후 `Chat.vue`의 1:1 탭에서 즉시 재대화가 가능한지 확인한다.
6. 그룹 방에서 영상 통화 버튼을 눌러 다자간 WebRTC 세션이 연결되는지 테스트한다.

## 5. 추가 관찰 사항 (개정)
1. **동시 접속 최적화**: 현재는 Mesh 방식으로 다자간 WebRTC를 구성하므로 참여자가 4명에 가까워질수록 브라우저 부하가 증가한다. SFU(Media Server) 도입을 검토하면 네트워크 효율과 품질을 개선할 수 있다.
2. **번역 캐싱**: 동일 메시지에 대한 번역이 클라이언트별로 반복 호출될 가능성이 있다. 메시지 ID와 대상 언어를 키로 한 캐싱 전략을 적용하면 Papago 호출 비용을 절감할 수 있다.
3. **파일 보안**: 현재 업로드된 파일은 공개 URL로 노출된다. 토큰 기반 일시적 다운로드 링크, MIME 타입 화이트리스트, 바이러스 스캔 연동 등을 추가해 보안을 강화하는 것이 바람직하다.
4. **읽음/미확인 카운트**: 채팅방 목록에 아직 읽지 않은 메시지 수를 표시하면 사용자 경험을 개선할 수 있다. `RoomMessage`에 읽음 상태를 추가하거나 Redis 캐시를 활용한 미확인 카운트 기능을 고려한다.
5. **오프라인 알림**: STOMP 연결이 끊어진 상태에서 도착한 메시지는 현재 실시간으로만 확인 가능하다. 푸시 알림 또는 이메일 알림 기능을 연계해 오프라인 사용자에게도 도달하도록 확장할 수 있다.

## 6. 요약
- 텍스트/영상/파일 채팅 흐름이 STOMP + WebRTC + REST 조합으로 통합되었고, 필수 요구사항(SFR-304/307/308/309/406)을 충족한다.
- 팔로우 기반 재대화, 번역기 토글, 그룹 영상 통화 등 사용자가 기대하는 핵심 기능이 단일 UI 흐름에서 작동한다.
- 추가 개선 항목으로는 미디어 서버 도입, 번역 캐싱, 파일 보안 강화, 읽음 처리, 오프라인 알림 등이 있다.
