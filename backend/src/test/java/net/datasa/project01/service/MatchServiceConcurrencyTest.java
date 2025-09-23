package net.datasa.project01.service;

import net.datasa.project01.domain.dto.MatchDecisionResponseDto;
import net.datasa.project01.domain.dto.MatchEventMessage;
import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.MatchRequestRepository;
import net.datasa.project01.repository.RoomRepository;
import net.datasa.project01.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class MatchServiceConcurrencyTest {

    @Autowired
    private MatchService matchService;

    @Autowired
    private MatchRequestRepository matchRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @MockBean
    private SimpMessageSendingOperations messagingTemplate;

    @MockBean
    private ChatService chatService;

    @Test
    void whenBothUsersAcceptConcurrently_bothReceiveConfirmationEvents() throws Exception {
        User userA = createUser("userA", "userA@example.com", 'M', LocalDate.of(1995, 1, 1));
        User userB = createUser("userB", "userB@example.com", 'F', LocalDate.of(1994, 6, 15));

        Room room = roomRepository.save(Room.builder()
                .roomType(Room.RoomType.PRIVATE)
                .capacity(2)
                .build());

        MatchRequest requestA = matchRequestRepository.saveAndFlush(MatchRequest.builder()
                .user(userA)
                .choiceGender(MatchRequest.Gender.A)
                .minAge(20)
                .maxAge(40)
                .regionCode("11")
                .interestsJson("[]")
                .status(MatchRequest.MatchStatus.MATCHED)
                .room(room)
                .build());

        MatchRequest requestB = matchRequestRepository.saveAndFlush(MatchRequest.builder()
                .user(userB)
                .choiceGender(MatchRequest.Gender.A)
                .minAge(20)
                .maxAge(40)
                .regionCode("11")
                .interestsJson("[]")
                .status(MatchRequest.MatchStatus.MATCHED)
                .room(room)
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<MatchDecisionResponseDto> acceptTaskForA = () -> {
            ready.countDown();
            startLatch.await();
            return matchService.respondToMatch(userA.getLoginId(), requestA.getRequestId(), true);
        };

        Callable<MatchDecisionResponseDto> acceptTaskForB = () -> {
            ready.countDown();
            startLatch.await();
            return matchService.respondToMatch(userB.getLoginId(), requestB.getRequestId(), true);
        };

        Future<MatchDecisionResponseDto> futureA = executor.submit(acceptTaskForA);
        Future<MatchDecisionResponseDto> futureB = executor.submit(acceptTaskForB);

        ready.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        MatchDecisionResponseDto responseA = futureA.get(5, TimeUnit.SECONDS);
        MatchDecisionResponseDto responseB = futureB.get(5, TimeUnit.SECONDS);

        executor.shutdownNow();

        List<MatchDecisionResponseDto> responses = List.of(responseA, responseB);
        assertThat(responses)
                .allMatch(response -> response.getDecision() == MatchDecisionResponseDto.Decision.ACCEPTED);
        assertThat(responses.stream().anyMatch(MatchDecisionResponseDto::isBothAccepted)).isTrue();

        MatchRequest updatedA = matchRequestRepository.findById(requestA.getRequestId()).orElseThrow();
        MatchRequest updatedB = matchRequestRepository.findById(requestB.getRequestId()).orElseThrow();
        assertThat(updatedA.getStatus()).isEqualTo(MatchRequest.MatchStatus.CONFIRMED);
        assertThat(updatedB.getStatus()).isEqualTo(MatchRequest.MatchStatus.CONFIRMED);

        ArgumentCaptor<MatchEventMessage> eventCaptor = ArgumentCaptor.forClass(MatchEventMessage.class);
        verify(messagingTemplate, atLeast(3)).convertAndSendToUser(anyString(), eq("/queue/match-results"), eventCaptor.capture());

        long bothConfirmedEvents = eventCaptor.getAllValues().stream()
                .filter(event -> event.getEventType() == MatchEventMessage.EventType.BOTH_CONFIRMED)
                .count();
        assertThat(bothConfirmedEvents).isEqualTo(2L);
    }

    private User createUser(String loginId, String email, char gender, LocalDate birthDate) {
        return userRepository.save(User.builder()
                .loginId(loginId)
                .passwordHash("password")
                .nickName(loginId)
                .email(email)
                .countryCode("KR")
                .languageCode("ko")
                .gender(gender)
                .birthDate(birthDate)
                .build());
    }
}
