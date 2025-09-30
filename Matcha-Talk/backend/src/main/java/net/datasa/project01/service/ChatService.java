package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.GroupRoomCreateRequestDto;
import net.datasa.project01.domain.dto.RoomDetailResponseDto;
import net.datasa.project01.domain.dto.RoomListResponseDto;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.RoomMember;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.RoomMemberRepository;
import net.datasa.project01.repository.RoomMessageRepository;
import net.datasa.project01.repository.RoomRepository;
import net.datasa.project01.repository.UserRepository;
import net.datasa.project01.domain.entity.Follow;
import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.domain.dto.ChatMessageRequestDto;
import net.datasa.project01.domain.dto.ChatMessageResponseDto;
import net.datasa.project01.domain.dto.TranslationResponseDto;
import net.datasa.project01.domain.entity.RoomMessage;
import net.datasa.project01.websocket.RealTimeMessagingService;
import net.datasa.project01.repository.MatchRequestRepository;
import net.datasa.project01.service.support.MatchRequestSanitizer;
import net.datasa.project01.repository.FollowRepository;
import net.datasa.project01.repository.FollowListRepository;
import net.datasa.project01.repository.FollowRequestRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.datasa.project01.domain.entity.Room.RoomType;

import net.datasa.project01.service.TranslationService;
import net.datasa.project01.domain.entity.FollowList;
import net.datasa.project01.domain.entity.FollowRequest;
import net.datasa.project01.service.support.CallSessionCoordinator;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

        private final RoomRepository roomRepository;
        private final RoomMemberRepository roomMemberRepository;
        private final UserRepository userRepository;
        private final RoomMessageRepository roomMessageRepository;
        private final MatchRequestRepository matchRequestRepository;
        private final FollowRepository followRepository;
        private final FollowRequestRepository followRequestRepository;
        private final FollowListRepository followListRepository;
        private final TranslationService translationService;
        private final RealTimeMessagingService messagingService;
        private final CallSessionCoordinator callSessionCoordinator;

        private static final String PROMOTION_REASON_MUTUAL_FOLLOW = "MUTUAL_FOLLOW";

        private static final Map<String, String> COUNTRY_TO_LANGUAGE_MAP = Map.ofEntries(
                Map.entry("KR", "ko"),
                Map.entry("KP", "ko"),
                Map.entry("US", "en"),
                Map.entry("GB", "en"),
                Map.entry("CA", "en"),
                Map.entry("AU", "en"),
                Map.entry("NZ", "en"),
                Map.entry("PH", "en"),
                Map.entry("JP", "ja"),
                Map.entry("CN", "zh-CN"),
                Map.entry("TW", "zh-TW"),
                Map.entry("HK", "zh-TW"),
                Map.entry("MO", "zh-TW"),
                Map.entry("FR", "fr"),
                Map.entry("DE", "de"),
                Map.entry("ES", "es"),
                Map.entry("IT", "it"),
                Map.entry("TH", "th"),
                Map.entry("VN", "vi"),
                Map.entry("ID", "id")
        );

        private final Path attachmentBasePath = Paths.get("uploads", "attachments");

        // TODO: 알림 서비스 추가 (Push Notification)
        // private final NotificationService notificationService;
        // TODO: 파일 업로드 서비스 추가
        // private final FileUploadService fileUploadService;
        // TODO: 커비너 세션 및 캐시 관리
        // private final RedisTemplate<String, Object> redisTemplate;

        @Transactional
        public Room createGroupRoom(String loginId) {
                CreatedGroupRoom context = createGroupRoomInternal(loginId, new GroupRoomCreateRequestDto(null, List.of()));
                return context.room();
        }

        @Transactional
        public RoomDetailResponseDto createGroupRoom(String loginId, GroupRoomCreateRequestDto request) {
                CreatedGroupRoom context = createGroupRoomInternal(loginId, request);
                return RoomDetailResponseDto.fromEntity(context.room(), context.members());
        }

        private CreatedGroupRoom createGroupRoomInternal(String loginId, GroupRoomCreateRequestDto request) {
                User creator = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

                List<Long> requestedMemberIds = request != null && request.memberUserPids() != null
                        ? request.memberUserPids()
                        : List.of();

                LinkedHashSet<Long> uniqueMemberIds = requestedMemberIds.stream()
                        .filter(Objects::nonNull)
                        .map(Long::longValue)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                uniqueMemberIds.remove(creator.getUserPid());

                if (uniqueMemberIds.size() > 3) {
                        throw new IllegalArgumentException("그룹 채팅은 최대 4명까지 참여할 수 있습니다.");
                }

                List<User> invitedUsers = uniqueMemberIds.isEmpty()
                        ? List.of()
                        : StreamSupport.stream(userRepository.findAllById(uniqueMemberIds).spliterator(), false)
                                .collect(Collectors.toList());

                if (invitedUsers.size() != uniqueMemberIds.size()) {
                        throw new IllegalArgumentException("초대 대상 중 존재하지 않는 사용자가 있습니다.");
                }

                Map<Long, User> invitedMap = invitedUsers.stream()
                        .collect(Collectors.toMap(User::getUserPid, Function.identity()));

                List<User> orderedInvitedUsers = uniqueMemberIds.stream()
                        .map(invitedMap::get)
                        .collect(Collectors.toList());

                if (orderedInvitedUsers.stream().anyMatch(Objects::isNull)) {
                        throw new IllegalArgumentException("초대 대상 중 존재하지 않는 사용자가 있습니다.");
                }

                Room newRoom = Room.builder()
                        .roomType(Room.RoomType.GROUP)
                        .capacity(Math.max(2, Math.min(4, 1 + orderedInvitedUsers.size())))
                        .build();
                roomRepository.save(newRoom);

                List<RoomMember> members = new ArrayList<>();
                RoomMember hostMember = RoomMember.builder()
                        .room(newRoom)
                        .user(creator)
                        .role("HOST")
                        .build();
                members.add(hostMember);

                for (User invited : orderedInvitedUsers) {
                        RoomMember member = RoomMember.builder()
                                .room(newRoom)
                                .user(invited)
                                .role("MEMBER")
                                .invitedByUser(creator)
                                .build();
                        members.add(member);
                }

                roomMemberRepository.saveAll(members);

                return new CreatedGroupRoom(newRoom, members);
        }

        private record CreatedGroupRoom(Room room, List<RoomMember> members) {}

        @Transactional
        public ChatMessageResponseDto processMessage(ChatMessageRequestDto requestDto, String loginId) {
                User sender = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
                Room room = roomRepository.findById(requestDto.getRoomId())
                        .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        RoomMessage message = RoomMessage.builder()
                .room(room)
                .sender(sender)
                .contentType(RoomMessage.ContentType.TEXT)
                .textContent(requestDto.getContent())
                .build();
        roomMessageRepository.save(message);

        return toResponseDto(message);
        }
        @Transactional
        public Room createPrivateRoom(User user1, User user2) {
                // 1. 새로운 PRIVATE 타입의 방 생성
                Room newRoom = Room.builder()
                        .roomType(Room.RoomType.PRIVATE)
                        .capacity(2)
                        .build();
                roomRepository.save(newRoom);

                // 2. 두 명의 사용자를 멤버로 추가
                RoomMember member1 = RoomMember.builder()
                        .room(newRoom)
                        .user(user1)
                        .role("MEMBER")
                        .build();
                
                RoomMember member2 = RoomMember.builder()
                        .room(newRoom)
                        .user(user2)
                        .role("MEMBER")
                        .build();
                
                roomMemberRepository.saveAll(java.util.List.of(member1, member2)); // 두 멤버를 한 번에 저장

                return newRoom;
        }

        @Transactional
        public Room createRandomRoom(User user1, User user2) {
                return createRandomMatchRoom(user1, user2);
        }

        @Transactional
        public Room createRandomMatchRoom(User user1, User user2) {
                Room newRoom = Room.builder()
                        .roomType(Room.RoomType.RANDOM)
                        .capacity(2)
                        .createdFromRoom(null)
                        .promotedAt(null)
                        .promotedReason(null)
                        .build();
                roomRepository.save(newRoom);

                RoomMember member1 = RoomMember.builder()
                        .room(newRoom)
                        .user(user1)
                        .role("MEMBER")
                        .build();

                RoomMember member2 = RoomMember.builder()
                        .room(newRoom)
                        .user(user2)
                        .role("MEMBER")
                        .build();

                roomMemberRepository.saveAll(java.util.List.of(member1, member2));

                return newRoom;
        }

        @Transactional
        public Room promoteRandomRoom(Room room) {
                if (room == null || room.getRoomType() != Room.RoomType.RANDOM) {
                        return room;
                }

                Room origin = room.getCreatedFromRoom() != null ? room.getCreatedFromRoom() : room;
                room.markPromoted(Room.RoomType.PRIVATE, origin, PROMOTION_REASON_MUTUAL_FOLLOW, LocalDateTime.now());

                return roomRepository.save(room);
        }

        @Transactional(readOnly = true)
        public List<RoomListResponseDto> findRoomsByUser(String loginId) {
                User user = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

                // 1. N+1 문제를 해결하기 위해 fetch join으로 사용자가 속한 모든 방과 멤버 정보를 한 번에 조회
                List<RoomMember> allMembersInMyRooms = roomMemberRepository.findAllRoomsAndMembersByUser(user);

                // 2. 조회된 멤버 목록을 '방(Room)' 기준으로 그룹핑
                Map<Room, List<RoomMember>> roomsGroupedByRoom = allMembersInMyRooms.stream()
                        .collect(Collectors.groupingBy(RoomMember::getRoom));

                // 3. 그룹핑된 데이터를 DTO로 변환
                return roomsGroupedByRoom.entrySet().stream()
                        .filter(entry -> !entry.getKey().isTemporary())
                        .map(entry -> RoomListResponseDto.fromEntity(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList());
        }

        @Transactional
        public RoomDetailResponseDto ensureDirectRoom(String loginId, Long targetUserPid) {
                if (targetUserPid == null) {
                        throw new IllegalArgumentException("대상 사용자를 선택해주세요.");
                }

                User requester = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

                if (requester.getUserPid().equals(targetUserPid)) {
                        throw new IllegalArgumentException("자기 자신과는 채팅방을 만들 수 없습니다.");
                }

                User target = userRepository.findById(targetUserPid)
                        .orElseThrow(() -> new IllegalArgumentException("대상 사용자를 찾을 수 없습니다."));

                boolean hasAcceptedFollow = followRepository.findByFollowerAndFolloweeAndStatus(requester, target, Follow.FollowStatus.ACCEPTED)
                        .isPresent()
                        || followRepository.findByFollowerAndFolloweeAndStatus(target, requester, Follow.FollowStatus.ACCEPTED)
                        .isPresent();

                if (!hasAcceptedFollow) {
                        throw new IllegalStateException("상대방과 팔로우가 수락된 상태에서만 1:1 채팅을 시작할 수 있습니다.");
                }

                Room existing = findExistingDirectRoom(requester, target);
                Room resolved = existing != null ? existing : createPrivateRoom(requester, target);

                List<RoomMember> members = roomMemberRepository.findByRoom(resolved);
                return RoomDetailResponseDto.fromEntity(resolved, members);
        }

        @Transactional(readOnly = true)
        public RoomDetailResponseDto findRoomDetailsById(Long roomId, String loginId) {
                User user = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

                Room room = roomRepository.findById(roomId)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

                // 사용자가 해당 방의 멤버인지 확인 (권한 검사)
                roomMemberRepository.findByRoomAndUser(room, user)
                        .orElseThrow(() -> new IllegalArgumentException("해당 채팅방에 접근할 권한이 없습니다."));

                // 방의 모든 멤버를 조회하여 상세 DTO 생성
                List<RoomMember> members = roomMemberRepository.findByRoom(room);

                return RoomDetailResponseDto.fromEntity(room, members);
        }

        @Transactional(readOnly = true)
        public java.util.List<String> findParticipantLoginIds(Long roomId) {
                Room room = roomRepository.findById(roomId)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

                return roomMemberRepository.findByRoom(room).stream()
                        .map(RoomMember::getUser)
                        .map(User::getLoginId)
                        .toList();
        }

        @Transactional(readOnly = true)
        public List<ChatMessageResponseDto> getMessageHistory(Long roomId, String loginId, int limit) {
                User requester = requireUser(loginId);
                Room room = requireRoom(roomId);
                ensureRoomMembership(room, requester);

                int sanitizedLimit = Math.max(1, Math.min(limit, 200));
                Pageable pageable = PageRequest.of(0, sanitizedLimit, Sort.by(Sort.Direction.DESC, "createdAt"));
                List<RoomMessage> messages = roomMessageRepository.findByRoomOrderByCreatedAtDesc(room, pageable).getContent();
                Collections.reverse(messages);
                return messages.stream()
                        .map(this::toResponseDto)
                        .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public CallReadyResponse markCallReady(Long roomId, String loginId) {
                User requester = requireUser(loginId);
                Room room = requireRoom(roomId);
                ensureRoomMembership(room, requester);

                List<String> participants = findParticipantLoginIds(roomId);
                CallSessionCoordinator.CallHandshakeStatus status = callSessionCoordinator.markReady(roomId, loginId, participants);

                messagingService.broadcastToUsers(
                        participants,
                        RealTimeMessagingService.EVENT_ROOM_CALL_READY,
                        new CallReadyPayload(roomId, loginId, status.readyMembers(), status.allReady())
                );

                return new CallReadyResponse(roomId, status.readyMembers(), status.allReady());
        }

        @Transactional
        public CallTerminationResponse endCall(Long roomId, String loginId) {
                User requester = requireUser(loginId);
                Room room = requireRoom(roomId);
                ensureRoomMembership(room, requester);

                List<RoomMember> members = roomMemberRepository.findByRoom(room);
                List<String> participantLogins = members.stream()
                        .map(RoomMember::getUser)
                        .filter(Objects::nonNull)
                        .map(User::getLoginId)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .collect(Collectors.toList());

                CallSessionCoordinator.CallHandshakeStatus handshakeStatus = callSessionCoordinator.reset(roomId);

                boolean randomRoom = room.getRoomType() == Room.RoomType.RANDOM;
                boolean followDataCleared = false;
                boolean roomRemoved = false;
                boolean singleFollow = false;

                if (members.size() >= 2) {
                        User first = members.get(0).getUser();
                        User second = members.get(1).getUser();
                        if (first != null && second != null) {
                                boolean forwardAccepted = isFollowAccepted(first, second);
                                boolean reverseAccepted = isFollowAccepted(second, first);
                                singleFollow = forwardAccepted ^ reverseAccepted;
                                if (randomRoom && singleFollow) {
                                        followDataCleared = removeFollowRelations(first, second);
                                        int removedRequests = removeFollowRequestsForRoom(room);
                                        followDataCleared = followDataCleared || removedRequests > 0;
                                        removeRoomWithDependencies(room, members);
                                        roomRemoved = true;
                                }
                        }
                }

                if (participantLogins.isEmpty() && handshakeStatus.participants() != null) {
                        participantLogins = handshakeStatus.participants().stream()
                                .filter(StringUtils::hasText)
                                .distinct()
                                .collect(Collectors.toList());
                }

                boolean redirectToMatch = randomRoom;

                messagingService.broadcastToUsers(
                        participantLogins,
                        RealTimeMessagingService.EVENT_ROOM_CALL_ENDED,
                        new CallEndedPayload(
                                roomId,
                                loginId,
                                redirectToMatch,
                                roomRemoved,
                                followDataCleared,
                                singleFollow ? "SINGLE_FOLLOW" : "ENDED"
                        )
                );

                return new CallTerminationResponse(roomId, redirectToMatch, roomRemoved, followDataCleared);
        }

        @Transactional
        public RoomCleanupResult cleanupTemporaryRoom(Long roomId, String loginId) {
                User user = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

                Room room = roomRepository.findById(roomId)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

                roomMemberRepository.findByRoomAndUser(room, user)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방 멤버만 정리할 수 있습니다."));

                boolean temporary = room.getRoomType() == Room.RoomType.RANDOM;
                if (!temporary) {
                        return new RoomCleanupResult(false, false, false, "ROOM_NOT_TEMPORARY");
                }

                List<RoomMember> members = roomMemberRepository.findByRoom(room);
                boolean mutualFollow = hasMutualFollow(members);
                if (mutualFollow) {
                        return new RoomCleanupResult(false, true, true, "MUTUAL_FOLLOW");
                }

                removeRoomWithDependencies(room, members);
                return new RoomCleanupResult(true, true, false, "DELETED");
        }

        @Transactional
        public ChatMessageResponseDto saveAttachment(Long roomId, MultipartFile file, String loginId) throws IOException {
                if (file == null || file.isEmpty()) {
                        throw new IllegalArgumentException("업로드할 파일이 존재하지 않습니다.");
                }

                User sender = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
                Room room = roomRepository.findById(roomId)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

                roomMemberRepository.findByRoomAndUser(room, sender)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방에 속한 사용자만 파일을 전송할 수 있습니다."));

                Path uploadDir = ensureUploadDirectory(roomId);

                String originalName = StringUtils.hasText(file.getOriginalFilename())
                        ? file.getOriginalFilename()
                        : "attachment";
                String extension = "";
                int dotIndex = originalName.lastIndexOf('.');
                if (dotIndex > -1 && dotIndex < originalName.length() - 1) {
                        extension = originalName.substring(dotIndex);
                }

                String storedName = UUID.randomUUID() + extension;
                Path storedPath = uploadDir.resolve(storedName);
                Files.copy(file.getInputStream(), storedPath, StandardCopyOption.REPLACE_EXISTING);

                RoomMessage.ContentType contentType = determineContentType(file.getContentType());
                String placeholderText = contentType == RoomMessage.ContentType.IMAGE ? "이미지 첨부" : originalName;

                RoomMessage message = RoomMessage.builder()
                        .room(room)
                        .sender(sender)
                        .contentType(contentType)
                        .textContent(placeholderText)
                        .fileName(originalName)
                        .filePath(storedPath.toString())
                        .mimeType(file.getContentType())
                        .sizeBytes(file.getSize())
                        .build();
                roomMessageRepository.save(message);

                ChatMessageResponseDto response = toResponseDto(message);

                messagingService.broadcastToUsers(
                        findParticipantLoginIds(roomId),
                        "chat",
                        response
                );

                return response;
        }

        @Transactional(readOnly = true)
        public AttachmentResource loadAttachment(Long roomId, Long messageId, String loginId) throws IOException {
                User requester = userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

                RoomMessage message = roomMessageRepository.findById(messageId)
                        .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

                if (!message.getRoom().getRoomId().equals(roomId)) {
                        throw new IllegalArgumentException("요청한 파일이 해당 채팅방에 존재하지 않습니다.");
                }

                roomMemberRepository.findByRoomAndUser(message.getRoom(), requester)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방에 속한 사용자만 파일을 다운로드할 수 있습니다."));

                Path filePath = Paths.get(message.getFilePath());
                if (!Files.exists(filePath)) {
                        throw new IllegalArgumentException("파일이 삭제되었거나 존재하지 않습니다.");
                }

                Resource resource = new UrlResource(filePath.toUri());
                if (!resource.exists()) {
                        throw new IllegalArgumentException("파일을 찾을 수 없습니다.");
                }
                return new AttachmentResource(resource, message.getFileName(), message.getMimeType());
        }

        @Transactional(readOnly = true)
        public TranslationResponseDto translateMessage(Long roomId, Long messageId, String loginId) {
                User requester = requireUser(loginId);
                Room room = requireRoom(roomId);

                try {
                        ensureRoomMembership(room, requester);
                } catch (IllegalArgumentException exception) {
                        throw new AccessDeniedException(exception.getMessage());
                }

                RoomMessage message = roomMessageRepository.findById(messageId)
                        .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

                if (message.getRoom() == null || !roomId.equals(message.getRoom().getRoomId())) {
                        throw new IllegalArgumentException("요청한 메시지가 해당 채팅방에 존재하지 않습니다.");
                }

                RoomMessage.ContentType contentType = message.getContentType() != null
                        ? message.getContentType()
                        : RoomMessage.ContentType.TEXT;

                String originalText = message.getTextContent() != null ? message.getTextContent() : "";
                String sourceLanguage = sanitizeSourceLanguage(resolvePapagoLanguageFromUser(message.getSender(), "auto"));
                String targetLanguage = sanitizeTargetLanguage(resolvePapagoLanguageFromUser(requester, "en"));

                if (contentType != RoomMessage.ContentType.TEXT || !StringUtils.hasText(originalText)) {
                        return TranslationResponseDto.builder()
                                .roomId(roomId)
                                .messageId(messageId)
                                .originalText(originalText)
                                .translatedText(originalText)
                                .sourceLanguage(sourceLanguage)
                                .targetLanguage(targetLanguage)
                                .translated(false)
                                .build();
                }

                boolean languagesEqual = StringUtils.hasText(sourceLanguage)
                        && StringUtils.hasText(targetLanguage)
                        && sourceLanguage.equalsIgnoreCase(targetLanguage);

                boolean shouldTranslate = StringUtils.hasText(targetLanguage) && !languagesEqual;

                String translatedText = originalText;
                boolean translated = false;

                if (shouldTranslate) {
                        translatedText = translationService.translate(originalText, sourceLanguage, targetLanguage);
                        translated = !Objects.equals(originalText, translatedText);
                }

                return TranslationResponseDto.builder()
                        .roomId(roomId)
                        .messageId(messageId)
                        .originalText(originalText)
                        .translatedText(translatedText)
                        .sourceLanguage(sourceLanguage)
                        .targetLanguage(targetLanguage)
                        .translated(translated)
                        .build();
        }

        private ChatMessageResponseDto toResponseDto(RoomMessage message) {
                if (message == null) {
                        throw new IllegalArgumentException("메시지 정보를 확인할 수 없습니다.");
                }

                RoomMessage.ContentType contentType = message.getContentType() != null
                        ? message.getContentType()
                        : RoomMessage.ContentType.TEXT;

                Long roomId = null;
                try {
                        roomId = message.getRoom() != null ? message.getRoom().getRoomId() : null;
                } catch (Exception exception) {
                        log.warn("Failed to resolve room information for message {}", message.getMessageId(), exception);
                }

                String downloadUrl = buildAttachmentDownloadUrl(message, contentType, roomId);

                return ChatMessageResponseDto.builder()
                        .messageId(message.getMessageId())
                        .roomId(roomId)
                        .senderLoginId(message.getSender() != null ? message.getSender().getLoginId() : null)
                        .senderNickName(message.getSender() != null ? message.getSender().getNickName() : "시스템")
                        .senderLanguageCode(message.getSender() != null ? message.getSender().getLanguageCode() : null)
                        .content(message.getTextContent())
                        .contentType(contentType.name())
                        .fileName(message.getFileName())
                        .fileUrl(downloadUrl)
                        .mimeType(message.getMimeType())
                        .sizeBytes(message.getSizeBytes())
                        .sentAt(message.getCreatedAt())
                        .build();
        }

        private String buildAttachmentDownloadUrl(RoomMessage message, RoomMessage.ContentType contentType, Long roomId) {
                if (message == null || contentType == null || contentType == RoomMessage.ContentType.TEXT) {
                        return null;
                }
                if (!StringUtils.hasText(message.getFilePath())) {
                        return null;
                }

                Long messageId = message.getMessageId();
                if (roomId == null || messageId == null) {
                        return null;
                }

                try {
                        return ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/api/rooms/")
                                .path(String.valueOf(roomId))
                                .path("/attachments/")
                                .path(String.valueOf(messageId))
                                .toUriString();
                } catch (IllegalStateException exception) {
                        log.debug("No request context available while building attachment URL for message {}", messageId, exception);
                        return String.format("/api/rooms/%s/attachments/%s", roomId, messageId);
                }
        }

        private String sanitizeSourceLanguage(String language) {
                if (StringUtils.hasText(language)) {
                        return language;
                }
                return "auto";
        }

        private String sanitizeTargetLanguage(String language) {
                if (StringUtils.hasText(language) && !"auto".equalsIgnoreCase(language)) {
                        return language;
                }
                return "en";
        }

        private String resolvePapagoLanguageFromUser(User user, String fallbackLanguage) {
                String normalizedFallback = normalizeLanguageCode(fallbackLanguage);
                if (user == null) {
                        return normalizedFallback;
                }

                String languageCode = normalizeLanguageCode(user.getLanguageCode());
                if (StringUtils.hasText(languageCode)) {
                        return languageCode;
                }

                String fromCountry = resolvePapagoLanguageFromCountry(user.getCountryCode());
                if (StringUtils.hasText(fromCountry)) {
                        return fromCountry;
                }

                return normalizedFallback;
        }

        private String resolvePapagoLanguageFromCountry(String countryCode) {
                if (!StringUtils.hasText(countryCode)) {
                        return null;
                }
                return COUNTRY_TO_LANGUAGE_MAP.get(countryCode.trim().toUpperCase());
        }

        private String normalizeLanguageCode(String languageCode) {
                if (!StringUtils.hasText(languageCode)) {
                        return null;
                }
                return languageCode.trim();
        }

        private boolean hasMutualFollow(List<RoomMember> members) {
                if (members == null) {
                        return false;
                }

                Map<Long, User> distinct = new LinkedHashMap<>();
                for (RoomMember member : members) {
                        User participant = member.getUser();
                        if (participant != null) {
                                distinct.putIfAbsent(participant.getUserPid(), participant);
                        }
                }

                Iterator<User> iterator = distinct.values().iterator();
                if (!iterator.hasNext()) {
                        return false;
                }
                User first = iterator.next();
                if (!iterator.hasNext()) {
                        return false;
                }
                User second = iterator.next();

                boolean forward = followRepository.findByFollowerAndFolloweeAndStatus(first, second, Follow.FollowStatus.ACCEPTED).isPresent();
                boolean reverse = followRepository.findByFollowerAndFolloweeAndStatus(second, first, Follow.FollowStatus.ACCEPTED).isPresent();
                return forward && reverse;
        }

        private Room findExistingDirectRoom(User user1, User user2) {
                List<RoomMember> memberships = roomMemberRepository.findByUser(user1);
                Set<Long> requiredMembers = Set.of(user1.getUserPid(), user2.getUserPid());

                for (RoomMember membership : memberships) {
                        Room room = membership.getRoom();
                        if (room == null || room.getRoomType() != Room.RoomType.PRIVATE) {
                                continue;
                        }

                        List<RoomMember> participants = roomMemberRepository.findByRoom(room);
                        Set<Long> participantIds = participants.stream()
                                .map(member -> member.getUser().getUserPid())
                                .collect(Collectors.toSet());

                        if (participantIds.containsAll(requiredMembers) && participantIds.size() == requiredMembers.size()) {
                                return room;
                        }
                }

                return null;
        }

        private User requireUser(String loginId) {
                if (!StringUtils.hasText(loginId)) {
                        throw new IllegalArgumentException("로그인 정보를 확인할 수 없습니다.");
                }
                return userRepository.findByLoginId(loginId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        }

        private Room requireRoom(Long roomId) {
                if (roomId == null) {
                        throw new IllegalArgumentException("채팅방 정보를 확인할 수 없습니다.");
                }
                return roomRepository.findById(roomId)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        }

        private void ensureRoomMembership(Room room, User user) {
                roomMemberRepository.findByRoomAndUser(room, user)
                        .orElseThrow(() -> new IllegalArgumentException("채팅방 멤버만 이용할 수 있습니다."));
        }

        private boolean isFollowAccepted(User follower, User followee) {
                return followRepository.findByFollowerAndFolloweeAndStatus(follower, followee, Follow.FollowStatus.ACCEPTED)
                        .isPresent();
        }

        private boolean removeFollowRelations(User first, User second) {
                boolean removed = false;
                removed = removeFollowEntry(first, second) || removed;
                removed = removeFollowEntry(second, first) || removed;
                return removed;
        }

        private boolean removeFollowEntry(User follower, User followee) {
                return followRepository.findByFollowerAndFollowee(follower, followee)
                        .map(follow -> {
                                List<FollowList> lists = followListRepository.findAllByFollow(follow);
                                if (lists != null && !lists.isEmpty()) {
                                        followListRepository.deleteAll(lists);
                                }
                                followRepository.delete(follow);
                                return true;
                        })
                        .orElse(false);
        }

        private int removeFollowRequestsForRoom(Room room) {
                List<FollowRequest> requests = followRequestRepository.findByRoom(room);
                if (requests == null || requests.isEmpty()) {
                        return 0;
                }
                followRequestRepository.deleteAll(requests);
                return requests.size();
        }

        private void removeRoomWithDependencies(Room room, List<RoomMember> existingMembers) {
                List<RoomMessage> messages = roomMessageRepository.findByRoom(room);
                for (RoomMessage message : messages) {
                        deleteAttachmentFile(message.getFilePath());
                }
                if (!messages.isEmpty()) {
                        roomMessageRepository.deleteAll(messages);
                }

                List<RoomMember> membersToRemove = existingMembers;
                if (membersToRemove == null || membersToRemove.isEmpty()) {
                        membersToRemove = roomMemberRepository.findByRoom(room);
                }
                if (membersToRemove != null && !membersToRemove.isEmpty()) {
                        roomMemberRepository.deleteAll(membersToRemove);
                }

                archiveMatchRequestsForRoom(room);

                roomRepository.delete(room);
                deleteAttachmentDirectory(room.getRoomId());
        }

        private void archiveMatchRequestsForRoom(Room room) {
                List<MatchRequest> relatedRequests = matchRequestRepository.findByRoom(room);
                if (relatedRequests.isEmpty()) {
                        return;
                }

                for (MatchRequest request : relatedRequests) {
                        request.setStatus(MatchRequest.MatchStatus.CANCELLED);
                        request.setRoom(null);
                        request.setHandshakeKey(null);
                        request.setHandshakeExpiresAt(null);
                        MatchRequestSanitizer.normalizeAgeRange(request);
                }

                matchRequestRepository.saveAll(relatedRequests);
        }

        private void deleteAttachmentFile(String filePath) {
                if (!StringUtils.hasText(filePath)) {
                        return;
                }
                try {
                        Files.deleteIfExists(Paths.get(filePath));
                } catch (IOException e) {
                        log.warn("Failed to delete attachment file {}", filePath, e);
                }
        }

        private void deleteAttachmentDirectory(Long roomId) {
                if (roomId == null) {
                        return;
                }
                Path directory = attachmentBasePath.resolve(String.valueOf(roomId));
                if (!Files.exists(directory)) {
                        return;
                }
                try (Stream<Path> walk = Files.walk(directory)) {
                        walk.sorted(Comparator.reverseOrder())
                                .forEach(path -> {
                                        try {
                                                Files.deleteIfExists(path);
                                        } catch (IOException e) {
                                                log.warn("Failed to delete path {}", path, e);
                                        }
                                });
                } catch (IOException e) {
                        log.warn("Failed to remove attachment directory for room {}", roomId, e);
                }
        }

        private RoomMessage.ContentType determineContentType(String mimeType) {
                if (mimeType != null && mimeType.startsWith("image")) {
                        return RoomMessage.ContentType.IMAGE;
                }
                return RoomMessage.ContentType.FILE;
        }

        private Path ensureUploadDirectory(Long roomId) throws IOException {
                Path roomDir = attachmentBasePath.resolve(String.valueOf(roomId));
                if (!Files.exists(roomDir)) {
                        Files.createDirectories(roomDir);
                }
                return roomDir;
        }

        public record CallReadyResponse(Long roomId, List<String> readyMembers, boolean allReady) {}

        public record CallTerminationResponse(Long roomId, boolean redirectToMatch, boolean roomRemoved, boolean followDataCleared) {}

        public record CallReadyPayload(Long roomId, String triggeredBy, List<String> readyMembers, boolean allReady) {}

        public record CallEndedPayload(Long roomId, String triggeredBy, boolean redirectToMatch, boolean roomRemoved, boolean followDataCleared, String reason) {}

        public record AttachmentResource(Resource resource, String fileName, String mimeType) {}

        public record RoomCleanupResult(boolean deleted, boolean temporary, boolean mutualFollow, String reason) {}

    // TODO: 추가 필요한 메서드들
    // public void joinRoom(Long roomId, String loginId) { }
    // public void leaveRoom(Long roomId, String loginId) { }
    // public List<RoomResponseDto> getUserRooms(String loginId) { }
    // public List<ChatMessageResponseDto> getMessageHistory(Long roomId, int page, int size) { }
    // public void deleteMessage(Long messageId, String loginId) { }
    // public void updateMessage(Long messageId, String newContent, String loginId) { }
    // public void uploadFile(Long roomId, MultipartFile file, String loginId) { }
    // public void markMessageAsRead(Long messageId, String loginId) { }
    // public int getUnreadMessageCount(String loginId) { }
    // public void kickMember(Long roomId, String targetLoginId, String adminLoginId) { }
    // public void updateRoomSettings(Long roomId, RoomSettingsDto settings, String loginId) { }
}