package net.datasa.project01.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.datasa.project01.domain.dto.MatchRequestDto;
import net.datasa.project01.domain.dto.MatchStartResponseDto;
import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.MatchRequestRepository;
import net.datasa.project01.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchRequestRepository matchRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    private ObjectMapper objectMapper;

    @InjectMocks
    private MatchService matchService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        matchService = new MatchService(
                matchRequestRepository,
                userRepository,
                chatService,
                messagingTemplate,
                objectMapper
        );
    }

    @Test
    @DisplayName("조건을 모두 만족하는 상대가 있으면 즉시 매칭 상태를 반환한다")
    void startOrFindMatch_shouldReturnMatchedResponse() throws Exception {
        User me = createUser(1L, "me", "M", LocalDate.now().minusYears(25));
        User opponent = createUser(2L, "you", "F", LocalDate.now().minusYears(24));

        MatchRequestDto requestDto = createRequestDto("F", 20, 30, "SEOUL", List.of("음악", "게임"));

        MatchRequest opponentRequest = MatchRequest.builder()
                .user(opponent)
                .choiceGender(MatchRequest.Gender.M)
                .minAge(20)
                .maxAge(32)
                .regionCode("SEOUL")
                .interestsJson(objectMapper.writeValueAsString(List.of("음악", "여행")))
                .status(MatchRequest.MatchStatus.WAITING)
                .build();

        Room room = Room.builder()
                .roomId(101L)
                .roomType(Room.RoomType.PRIVATE)
                .capacity(2)
                .build();

        given(userRepository.findByLoginId("me")).willReturn(Optional.of(me));
        given(matchRequestRepository.findByUserAndStatus(me, MatchRequest.MatchStatus.WAITING)).willReturn(Optional.empty());
        given(matchRequestRepository.existsByStatusAndRegionCode(MatchRequest.MatchStatus.WAITING, "SEOUL")).willReturn(true);
        given(matchRequestRepository.findPotentialMatches(
                me.getUserPid(),
                requestDto.getChoiceGender(),
                requestDto.getMinAge(),
                requestDto.getMaxAge(),
                requestDto.getRegionCode(),
                MatchRequest.MatchStatus.WAITING
        )).willReturn(List.of(opponentRequest));
        given(chatService.getOrCreatePrivateRoom("me", "you")).willReturn(room);

        MatchStartResponseDto response = matchService.startOrFindMatch("me", requestDto);

        assertThat(response.getStatus()).isEqualTo(MatchStartResponseDto.Status.MATCHED);
        assertThat(response.getMatch()).isNotNull();
        assertThat(response.getMatch().getRoomId()).isEqualTo(101L);
        assertThat(response.getMatch().getPartnerNickName()).isEqualTo(opponent.getNickName());

        verify(messagingTemplate).convertAndSendToUser(eq("me"), eq("/queue/match-results"), any());
        verify(messagingTemplate).convertAndSendToUser(eq("you"), eq("/queue/match-results"), any());
        verify(matchRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("조건에 맞는 사용자가 없고 대기열도 비어있으면 EMPTY 상태로 대기열 등록을 알린다")
    void startOrFindMatch_shouldReturnQueuedEmptyWhenNoWaitingUsers() throws Exception {
        User me = createUser(10L, "alpha", "F", LocalDate.now().minusYears(27));
        MatchRequestDto requestDto = createRequestDto("M", 23, 33, "BUSAN", List.of("독서"));

        given(userRepository.findByLoginId("alpha")).willReturn(Optional.of(me));
        given(matchRequestRepository.findByUserAndStatus(me, MatchRequest.MatchStatus.WAITING)).willReturn(Optional.empty());
        given(matchRequestRepository.existsByStatusAndRegionCode(MatchRequest.MatchStatus.WAITING, "BUSAN")).willReturn(false);
        given(matchRequestRepository.findPotentialMatches(
                me.getUserPid(),
                requestDto.getChoiceGender(),
                requestDto.getMinAge(),
                requestDto.getMaxAge(),
                requestDto.getRegionCode(),
                MatchRequest.MatchStatus.WAITING
        )).willReturn(List.of());

        MatchStartResponseDto response = matchService.startOrFindMatch("alpha", requestDto);

        assertThat(response.getStatus()).isEqualTo(MatchStartResponseDto.Status.QUEUED);
        assertThat(response.getQueueState()).isEqualTo(MatchStartResponseDto.QueueState.EMPTY);
        assertThat(response.getMessage()).contains("대기열에 등록");

        ArgumentCaptor<MatchRequest> captor = ArgumentCaptor.forClass(MatchRequest.class);
        verify(matchRequestRepository).save(captor.capture());
        MatchRequest saved = captor.getValue();
        assertThat(saved.getRegionCode()).isEqualTo("BUSAN");
        assertThat(saved.getChoiceGender()).isEqualTo(MatchRequest.Gender.M);
    }

    @Test
    @DisplayName("관심사가 겹치지 않는 경우 대기열 유지 상태를 반환한다")
    void startOrFindMatch_shouldReturnQueuedWaitingWhenInterestsDoNotOverlap() throws Exception {
        User me = createUser(21L, "beta", "M", LocalDate.now().minusYears(22));
        User opponent = createUser(30L, "gamma", "F", LocalDate.now().minusYears(23));

        MatchRequestDto requestDto = createRequestDto("F", 20, 30, "SEOUL", List.of("여행"));

        MatchRequest opponentRequest = MatchRequest.builder()
                .user(opponent)
                .choiceGender(MatchRequest.Gender.M)
                .minAge(20)
                .maxAge(30)
                .regionCode("SEOUL")
                .interestsJson(objectMapper.writeValueAsString(List.of("독서")))
                .status(MatchRequest.MatchStatus.WAITING)
                .build();

        given(userRepository.findByLoginId("beta")).willReturn(Optional.of(me));
        given(matchRequestRepository.findByUserAndStatus(me, MatchRequest.MatchStatus.WAITING)).willReturn(Optional.empty());
        given(matchRequestRepository.existsByStatusAndRegionCode(MatchRequest.MatchStatus.WAITING, "SEOUL")).willReturn(true);
        given(matchRequestRepository.findPotentialMatches(
                me.getUserPid(),
                requestDto.getChoiceGender(),
                requestDto.getMinAge(),
                requestDto.getMaxAge(),
                requestDto.getRegionCode(),
                MatchRequest.MatchStatus.WAITING
        )).willReturn(List.of(opponentRequest));

        MatchStartResponseDto response = matchService.startOrFindMatch("beta", requestDto);

        assertThat(response.getStatus()).isEqualTo(MatchStartResponseDto.Status.QUEUED);
        assertThat(response.getQueueState()).isEqualTo(MatchStartResponseDto.QueueState.WAITING);
        assertThat(response.getMessage()).contains("조건에 맞는 사용자를 찾지 못했습니다");

        verify(matchRequestRepository).save(any(MatchRequest.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq("beta"), eq("/queue/match-results"), any());
    }

    private MatchRequestDto createRequestDto(String gender, int minAge, int maxAge, String region, List<String> interests) {
        MatchRequestDto dto = new MatchRequestDto();
        dto.setChoiceGender(gender);
        dto.setMinAge(minAge);
        dto.setMaxAge(maxAge);
        dto.setRegionCode(region);
        dto.setInterests(interests);
        return dto;
    }

    private User createUser(Long id, String loginId, String gender, LocalDate birthDate) {
        return User.builder()
                .userPid(id)
                .loginId(loginId)
                .passwordHash("password")
                .nickName(loginId + "-nick")
                .email(loginId + "@example.com")
                .countryCode("KR")
                .gender(gender)
                .birthDate(birthDate)
                .emailVerified(true)
                .failedLoginCount(0)
                .enabled(true)
                .roleName("ROLE_USER")
                .build();
    }
}
