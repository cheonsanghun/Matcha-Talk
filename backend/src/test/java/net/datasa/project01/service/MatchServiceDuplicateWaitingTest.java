package net.datasa.project01.service;

import net.datasa.project01.domain.dto.MatchRequestDto;
import net.datasa.project01.domain.dto.MatchStartResponseDto;
import net.datasa.project01.domain.entity.MatchRequest;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.MatchRequestRepository;
import net.datasa.project01.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MatchServiceDuplicateWaitingTest {

    @Autowired
    private MatchService matchService;

    @Autowired
    private MatchRequestRepository matchRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private ChatService chatService;

    @MockBean
    private SimpMessageSendingOperations messagingTemplate;

    @BeforeEach
    void cleanUp() {
        matchRequestRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void whenMultipleWaitingRequestsExist_userIsReportedAsAlreadyWaiting() {
        User user = userRepository.save(User.builder()
                .loginId("duplicate-user")
                .passwordHash("password")
                .nickName("duplicate-user")
                .email("duplicate@example.com")
                .countryCode("KR")
                .languageCode("ko")
                .gender('M')
                .birthDate(LocalDate.of(1995, 1, 1))
                .build());

        MatchRequest first = matchRequestRepository.saveAndFlush(MatchRequest.builder()
                .user(user)
                .choiceGender(MatchRequest.Gender.A)
                .minAge(20)
                .maxAge(30)
                .regionCode("11")
                .interestsJson("[]")
                .status(MatchRequest.MatchStatus.WAITING)
                .build());

        MatchRequest second = matchRequestRepository.saveAndFlush(MatchRequest.builder()
                .user(user)
                .choiceGender(MatchRequest.Gender.A)
                .minAge(20)
                .maxAge(30)
                .regionCode("11")
                .interestsJson("[]")
                .status(MatchRequest.MatchStatus.WAITING)
                .build());

        MatchRequestDto requestDto = new MatchRequestDto();
        requestDto.setChoiceGender("A");
        requestDto.setMinAge(20);
        requestDto.setMaxAge(30);
        requestDto.setRegionCode("11");
        requestDto.setInterests(List.of("music"));

        MatchStartResponseDto response = matchService.startOrFindMatch(user.getLoginId(), requestDto);

        assertThat(response.getState()).isEqualTo(MatchStartResponseDto.MatchState.ALREADY_WAITING);
        assertThat(response.getMyRequestId()).isEqualTo(second.getRequestId());
        assertThat(matchRequestRepository.countByStatus(MatchRequest.MatchStatus.WAITING)).isEqualTo(2);
        assertThat(matchRequestRepository.findFirstByUserAndStatusOrderByRequestedAtDesc(user, MatchRequest.MatchStatus.WAITING))
                .contains(second);
        assertThat(first.getRequestId()).isNotEqualTo(second.getRequestId());
    }
}
