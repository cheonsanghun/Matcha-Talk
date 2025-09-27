package net.datasa.project01.service;

import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.RoomMemberRepository;
import net.datasa.project01.repository.RoomRepository;
import net.datasa.project01.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class FileUploadPresignService {

    private static final Duration DEFAULT_EXPIRATION = Duration.ofMinutes(5);

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;
    private final String baseUrl;

    public FileUploadPresignService(RoomRepository roomRepository,
                                    RoomMemberRepository roomMemberRepository,
                                    UserRepository userRepository,
                                    @Value("${storage.presign.base-url:}") String baseUrl) {
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.userRepository = userRepository;
        this.baseUrl = baseUrl;
    }

    @Transactional(readOnly = true)
    public PresignedUploadResponse createUploadUrl(Long roomId,
                                                   String loginId,
                                                   String filename,
                                                   String contentType,
                                                   long contentLength) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("파일 업로드 사전 서명을 위한 base-url이 설정되어 있지 않습니다.");
        }

        if (contentLength <= 0) {
            throw new IllegalArgumentException("파일 크기가 유효하지 않습니다.");
        }

        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new AccessDeniedException("사용자 정보를 찾을 수 없습니다."));

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        roomMemberRepository.findByRoomAndUser(room, user)
                .filter(member -> member.getLeftAt() == null)
                .orElseThrow(() -> new AccessDeniedException("채팅방에 참여 중인 사용자만 업로드할 수 있습니다."));

        String safeFilename = Paths.get(filename).getFileName().toString();
        String objectKey = "rooms/" + roomId + "/" + UUID.randomUUID() + "-" + safeFilename;

        String uploadUrl = baseUrl.endsWith("/") ? baseUrl + objectKey : baseUrl + "/" + objectKey;

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", contentType);
        headers.put("Content-Length", String.valueOf(contentLength));

        return PresignedUploadResponse.builder()
                .uploadUrl(uploadUrl)
                .method("PUT")
                .headers(headers)
                .objectKey(objectKey)
                .expiresInSeconds(DEFAULT_EXPIRATION.toSeconds())
                .build();
    }

    public record PresignedUploadResponse(String uploadUrl,
                                          String method,
                                          Map<String, String> headers,
                                          String objectKey,
                                          long expiresInSeconds) {
        @lombok.Builder
        public PresignedUploadResponse {}
    }
}
