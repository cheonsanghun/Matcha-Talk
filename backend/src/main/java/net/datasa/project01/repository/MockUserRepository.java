package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.User;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [MockUserRepository]
 * - 개발/테스트 환경에서 DB 없이 인메모리 저장소로 회원 정보를 관리하는 클래스입니다.
 * - 실제 DB 연결 없이 빠르게 기능을 검증할 수 있습니다.
 * - Spring의 "mock" 프로필에서만 활성화됩니다.
 * - UserRepository 인터페이스를 구현하여, DB 저장소와 동일한 메서드 구조를 제공합니다.
 */
@Repository
@Profile("mock") // mock 프로필에서만 활성화됨
public class MockUserRepository implements UserRepository {
    // 의사 AUTO_INCREMENT 시퀀스
    private long seq = 1L;
    // 회원번호(PK)로 회원정보를 저장하는 인메모리 맵
    private final Map<Long, User> store = new ConcurrentHashMap<>();
    // 로그인ID로 회원번호를 찾는 인덱스 맵
    private final Map<String, Long> byLoginId = new ConcurrentHashMap<>();
    // 이메일로 회원번호를 찾는 인덱스 맵
    private final Map<String, Long> byEmail   = new ConcurrentHashMap<>();

    @Override
    public synchronized User save(User user) {
        // 회원정보를 저장(신규/수정)
        if (user.getUserPid() == null) {
            user.setUserPid(seq++); // 새 ID 발급
        }
        store.put(user.getUserPid(), user); // 회원번호로 저장
        byLoginId.put(user.getLoginId(), user.getUserPid()); // 로그인ID로 회원번호 저장
        byEmail.put(user.getEmail(), user.getUserPid());     // 이메일로 회원번호 저장
        return user; // 저장된 회원정보 반환
    }

    @Override
    public Optional<User> findById(Long userPid) {
        // 회원번호(PK)로 회원정보 조회
        return Optional.ofNullable(store.get(userPid));
    }

    @Override
    public Optional<User> findByLoginId(String loginId) {
        // 로그인ID로 회원정보 조회
        Long id = byLoginId.get(loginId); // 로그인ID로 회원번호 찾기
        return Optional.ofNullable(id).map(store::get); // 회원정보 반환
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        // 로그인ID 중복 체크
        return byLoginId.containsKey(loginId);
    }

    @Override
    public boolean existsByEmail(String email) {
        // 이메일 중복 체크
        return byEmail.containsKey(email);
    }

    @Override // UserRepository 인터페이스의 메서드 구현임을 명시
    public Optional<User> findByEmail(String email) {
        // 이메일로 회원번호(PK)를 조회 (byEmail 인덱스 맵 사용)
        Long id = byEmail.get(email); // 해당 이메일에 매핑된 회원번호를 가져옴 (없으면 null)
        // 회원번호가 있으면 store 맵에서 회원정보(User)를 꺼내고, 없으면 Optional.empty 반환
        return Optional.ofNullable(id) // id가 null이면 빈 Optional, 아니면 값이 있는 Optional
                .map(store::get); // id가 있으면 store에서 User 객체를 꺼내서 Optional로 감싸 반환
    }
}