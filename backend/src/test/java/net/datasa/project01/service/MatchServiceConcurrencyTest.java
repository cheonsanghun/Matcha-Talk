package net.datasa.project01.service;

import net.datasa.project01.domain.dto.MatchEventMessage;
import net.datasa.project01.domain.dto.MatchRequestDto;
import net.datasa.project01.domain.dto.MatchStartResponseDto;
import net.datasa.project01.domain.entity.MatchRequest;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class MatchServiceConcurrencyTest {

    @Autowired
    private MatchService matchService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchRequestRepository matchRequestRepository;

    @Autowired
    private RoomRepository roomRepository;

    @MockBean
    private SimpMessageSendingOperations messagingTemplate;

    @Test
    void twoConcurrentRequestsMatchExactlyOnce() throws Exception {
        User user1 = createUser("alpha", "alpha@example.com", 'M', LocalDate.of(1995, 1, 1));
        User user2 = createUser("bravo", "bravo@example.com", 'F', LocalDate.of(1996, 2, 2));

        MatchRequestDto requestForUser1 = buildRequestDto("F");
        MatchRequestDto requestForUser2 = buildRequestDto("M");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<MatchStartResponseDto> task1 = () -> {
            readyLatch.countDown();
            if (!startLatch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start");
            }
            return matchService.startOrFindMatch(user1.getLoginId(), requestForUser1);
        };

        Callable<MatchStartResponseDto> task2 = () -> {
            readyLatch.countDown();
            if (!startLatch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start");
            }
            return matchService.startOrFindMatch(user2.getLoginId(), requestForUser2);
        };

        Future<MatchStartResponseDto> future1 = executor.submit(task1);
        Future<MatchStartResponseDto> future2 = executor.submit(task2);

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        MatchStartResponseDto response1;
        MatchStartResponseDto response2;
        try {
            response1 = future1.get(5, TimeUnit.SECONDS);
            response2 = future2.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        List<MatchStartResponseDto> responses = List.of(response1, response2);
        assertThat(responses.stream().anyMatch(r -> r.getState() == MatchStartResponseDto.MatchState.MATCHED)).isTrue();

        List<MatchRequest> persistedRequests = matchRequestRepository.findAll();
        assertThat(persistedRequests).hasSize(2);
        assertThat(persistedRequests).allMatch(req -> req.getStatus() == MatchRequest.MatchStatus.MATCHED);

        assertThat(roomRepository.count()).isEqualTo(1);

        ArgumentCaptor<String> loginCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MatchEventMessage> eventCaptor = ArgumentCaptor.forClass(MatchEventMessage.class);

        verify(messagingTemplate, times(2)).convertAndSendToUser(loginCaptor.capture(), destinationCaptor.capture(), eventCaptor.capture());

        assertThat(new HashSet<>(destinationCaptor.getAllValues())).containsExactly("/queue/match-results");

        Map<String, MatchEventMessage> eventsByUser = new HashMap<>();
        for (int i = 0; i < loginCaptor.getAllValues().size(); i++) {
            eventsByUser.put(loginCaptor.getAllValues().get(i), eventCaptor.getAllValues().get(i));
        }

        assertThat(eventsByUser.keySet()).containsExactlyInAnyOrder(user1.getLoginId(), user2.getLoginId());
        eventsByUser.values().forEach(event -> assertThat(event.getEventType()).isEqualTo(MatchEventMessage.EventType.MATCH_FOUND));

        List<String> waitingUsers = new ArrayList<>();
        if (response1.getState() != MatchStartResponseDto.MatchState.MATCHED) {
            waitingUsers.add(user1.getLoginId());
        }
        if (response2.getState() != MatchStartResponseDto.MatchState.MATCHED) {
            waitingUsers.add(user2.getLoginId());
        }
        waitingUsers.forEach(user -> {
            assertThat(eventsByUser).containsKey(user);
            assertThat(eventsByUser.get(user).getEventType()).isEqualTo(MatchEventMessage.EventType.MATCH_FOUND);
        });
    }

    private User createUser(String loginId, String email, char gender, LocalDate birthDate) {
        User user = User.builder()
                .loginId(loginId)
                .passwordHash("hashed")
                .nickName(loginId + "Nick")
                .email(email)
                .countryCode("KR")
                .languageCode("ko")
                .gender(gender)
                .birthDate(birthDate)
                .enabled(true)
                .emailVerified(true)
                .build();
        return userRepository.save(user);
    }

    private MatchRequestDto buildRequestDto(String choiceGender) {
        MatchRequestDto dto = new MatchRequestDto();
        dto.setChoiceGender(choiceGender);
        dto.setMinAge(20);
        dto.setMaxAge(35);
        dto.setRegionCode("SEOUL");
        dto.setInterests(List.of("coffee"));
        return dto;
    }
}
