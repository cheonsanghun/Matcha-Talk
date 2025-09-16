package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.FollowResponseDto;
import net.datasa.project01.domain.entity.Follow;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.FollowRepository;
import net.datasa.project01.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    public FollowResponseDto follow(String followerLoginId, String targetLoginId) {
        if (followerLoginId.equals(targetLoginId)) {
            throw new IllegalArgumentException("자기 자신은 팔로우할 수 없습니다.");
        }

        User follower = userRepository.findByLoginId(followerLoginId)
                .orElseThrow(() -> new IllegalArgumentException("요청 사용자를 찾을 수 없습니다."));
        User followee = userRepository.findByLoginId(targetLoginId)
                .orElseThrow(() -> new IllegalArgumentException("대상 사용자를 찾을 수 없습니다."));

        Follow follow = followRepository.findByFollowerAndFollowee(follower, followee)
                .map(existing -> {
                    if (existing.getStatus() != Follow.Status.ACCEPTED) {
                        existing.accept();
                    }
                    return existing;
                })
                .orElseGet(() -> followRepository.save(
                        Follow.builder()
                                .follower(follower)
                                .followee(followee)
                                .status(Follow.Status.ACCEPTED)
                                .build()
                ));

        log.info("User {} followed {} with status {}", followerLoginId, targetLoginId, follow.getStatus());
        return FollowResponseDto.fromUser(follow.getFollowee());
    }

    public void unfollow(String followerLoginId, String targetLoginId) {
        User follower = userRepository.findByLoginId(followerLoginId)
                .orElseThrow(() -> new IllegalArgumentException("요청 사용자를 찾을 수 없습니다."));
        User followee = userRepository.findByLoginId(targetLoginId)
                .orElseThrow(() -> new IllegalArgumentException("대상 사용자를 찾을 수 없습니다."));

        followRepository.findByFollowerAndFollowee(follower, followee)
                .ifPresent(followRepository::delete);

        log.info("User {} unfollowed {}", followerLoginId, targetLoginId);
    }

    @Transactional(readOnly = true)
    public List<FollowResponseDto> getFollowing(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return followRepository.findByFollowerAndStatus(user, Follow.Status.ACCEPTED)
                .stream()
                .map(follow -> FollowResponseDto.fromUser(follow.getFollowee()))
                .collect(Collectors.toList());
    }
}
