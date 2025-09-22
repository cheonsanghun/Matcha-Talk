package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.FriendResponseDto;
import net.datasa.project01.domain.entity.Friendship;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.FriendshipRepository;
import net.datasa.project01.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;

    @Transactional
    public FriendResponseDto follow(String followerLoginId, String followeeLoginId) {
        if (followerLoginId.equalsIgnoreCase(followeeLoginId)) {
            throw new IllegalArgumentException("자기 자신은 팔로우할 수 없습니다.");
        }

        User follower = userRepository.findByLoginId(followerLoginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        User followee = userRepository.findByLoginId(followeeLoginId)
                .orElseThrow(() -> new IllegalArgumentException("대상 사용자를 찾을 수 없습니다."));

        Optional<Friendship> existing = friendshipRepository.findByFollowerAndFollowee(follower, followee);
        Friendship friendship = existing.orElseGet(() -> friendshipRepository.save(Friendship.builder()
                .follower(follower)
                .followee(followee)
                .build()));

        Room room = chatService.getOrCreatePrivateRoom(follower, followee);

        return new FriendResponseDto(friendship.getFollowee().getLoginId(),
                friendship.getFollowee().getNickName(),
                room.getRoomId());
    }

    @Transactional(readOnly = true)
    public List<FriendResponseDto> list(String followerLoginId) {
        User follower = userRepository.findByLoginId(followerLoginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return friendshipRepository.findByFollower(follower).stream()
                .map(friendship -> {
                    User followee = friendship.getFollowee();
                    Room room = chatService.findPrivateRoom(follower.getLoginId(), followee.getLoginId())
                            .orElseGet(() -> chatService.getOrCreatePrivateRoom(follower, followee));
                    return new FriendResponseDto(followee.getLoginId(), followee.getNickName(), room.getRoomId());
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void unfollow(String followerLoginId, String followeeLoginId) {
        User follower = userRepository.findByLoginId(followerLoginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        User followee = userRepository.findByLoginId(followeeLoginId)
                .orElseThrow(() -> new IllegalArgumentException("대상 사용자를 찾을 수 없습니다."));
        friendshipRepository.deleteByFollowerAndFollowee(follower, followee);
    }
}
