package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.FollowListItemDto;
import net.datasa.project01.domain.dto.FollowResponseDto;
import net.datasa.project01.domain.entity.Follow;
import net.datasa.project01.domain.entity.FollowList;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.FollowListRepository;
import net.datasa.project01.repository.FollowRepository;
import net.datasa.project01.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class FollowService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final FollowListRepository followListRepository;

    public FollowResponseDto requestFollow(String loginId, Long targetPid) {
        User follower = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        User followee = userRepository.findById(targetPid)
                .orElseThrow(() -> new IllegalArgumentException("대상 사용자를 찾을 수 없습니다."));

        if (follower.getUserPid().equals(followee.getUserPid())) {
            throw new IllegalArgumentException("자기 자신을 팔로우할 수 없습니다.");
        }

        Optional<Follow> existing = followRepository.findByFollowerAndFollowee(follower, followee);

        Follow follow = existing.map(value -> {
                    if (value.getStatus() == Follow.Status.PENDING) {
                        throw new IllegalStateException("이미 팔로우 요청이 진행 중입니다.");
                    }
                    if (value.getStatus() == Follow.Status.ACCEPTED) {
                        throw new IllegalStateException("이미 상대를 팔로우하고 있습니다.");
                    }
                    value.setStatus(Follow.Status.PENDING);
                    syncFollowListEntries(value);
                    log.info("Re-requested follow from {} to {}", loginId, followee.getLoginId());
                    return value;
                })
                .orElseGet(() -> {
                    Follow created = followRepository.save(Follow.builder()
                            .follower(follower)
                            .followee(followee)
                            .status(Follow.Status.PENDING)
                            .build());
                    createFollowListEntries(created);
                    log.info("Created follow request from {} to {}", loginId, followee.getLoginId());
                    return created;
                });

        return toResponseDto(follow);
    }

    public FollowResponseDto approveFollow(String loginId, Long followId) {
        Follow follow = followRepository.findById(followId)
                .orElseThrow(() -> new IllegalArgumentException("팔로우 요청을 찾을 수 없습니다."));

        if (!follow.getFollowee().getLoginId().equals(loginId)) {
            throw new IllegalStateException("승인 권한이 없습니다.");
        }

        follow.setStatus(Follow.Status.ACCEPTED);
        syncFollowListEntries(follow);
        log.info("Follow request {} approved by {}", followId, loginId);
        return toResponseDto(follow);
    }

    public FollowResponseDto rejectFollow(String loginId, Long followId) {
        Follow follow = followRepository.findById(followId)
                .orElseThrow(() -> new IllegalArgumentException("팔로우 요청을 찾을 수 없습니다."));

        if (!follow.getFollowee().getLoginId().equals(loginId)) {
            throw new IllegalStateException("거절 권한이 없습니다.");
        }

        follow.setStatus(Follow.Status.REJECTED);
        syncFollowListEntries(follow);
        log.info("Follow request {} rejected by {}", followId, loginId);
        return toResponseDto(follow);
    }

    public void removeFollow(String loginId, Long followId) {
        Follow follow = followRepository.findById(followId)
                .orElseThrow(() -> new IllegalArgumentException("팔로우 요청을 찾을 수 없습니다."));

        String followerLogin = follow.getFollower().getLoginId();
        String followeeLogin = follow.getFollowee().getLoginId();

        if (!followerLogin.equals(loginId) && !followeeLogin.equals(loginId)) {
            throw new IllegalStateException("삭제 권한이 없습니다.");
        }

        followRepository.delete(follow);
        log.info("Follow relationship {} removed by {}", followId, loginId);
    }

    @Transactional(readOnly = true)
    public List<FollowListItemDto> getFollowings(String loginId, Follow.Status status) {
        User owner = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        List<FollowList> entries = fetchEntries(owner, FollowList.Direction.FOLLOWING, status);
        return entries.stream().map(this::toListDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FollowListItemDto> getFollowers(String loginId, Follow.Status status) {
        User owner = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        List<FollowList> entries = fetchEntries(owner, FollowList.Direction.FOLLOWER, status);
        return entries.stream().map(this::toListDto).collect(Collectors.toList());
    }

    private List<FollowList> fetchEntries(User owner, FollowList.Direction direction, Follow.Status status) {
        if (status == null) {
            return followListRepository.findByOwnerAndDirectionOrderByCreatedAtDesc(owner, direction);
        }
        return followListRepository.findByOwnerAndDirectionAndStatusOrderByCreatedAtDesc(owner, direction, status);
    }

    private void createFollowListEntries(Follow follow) {
        FollowList followerEntry = FollowList.builder()
                .follow(follow)
                .owner(follow.getFollower())
                .target(follow.getFollowee())
                .direction(FollowList.Direction.FOLLOWING)
                .status(follow.getStatus())
                .build();

        FollowList followeeEntry = FollowList.builder()
                .follow(follow)
                .owner(follow.getFollowee())
                .target(follow.getFollower())
                .direction(FollowList.Direction.FOLLOWER)
                .status(follow.getStatus())
                .build();

        followListRepository.save(followerEntry);
        followListRepository.save(followeeEntry);
    }

    private void syncFollowListEntries(Follow follow) {
        List<FollowList> lists = followListRepository.findByFollow(follow);
        if (lists.isEmpty()) {
            createFollowListEntries(follow);
            return;
        }
        lists.forEach(entry -> entry.setStatus(follow.getStatus()));
    }

    private FollowResponseDto toResponseDto(Follow follow) {
        return FollowResponseDto.builder()
                .followId(follow.getFollowId())
                .followerPid(follow.getFollower().getUserPid())
                .followerLoginId(follow.getFollower().getLoginId())
                .followerNickName(follow.getFollower().getNickName())
                .followeePid(follow.getFollowee().getUserPid())
                .followeeLoginId(follow.getFollowee().getLoginId())
                .followeeNickName(follow.getFollowee().getNickName())
                .status(follow.getStatus())
                .build();
    }

    private FollowListItemDto toListDto(FollowList followList) {
        User target = followList.getTarget();
        return FollowListItemDto.builder()
                .followId(followList.getFollow().getFollowId())
                .userPid(target.getUserPid())
                .loginId(target.getLoginId())
                .nickName(target.getNickName())
                .status(followList.getStatus())
                .direction(followList.getDirection())
                .createdAt(followList.getCreatedAt())
                .updatedAt(followList.getUpdatedAt())
                .build();
    }
}
