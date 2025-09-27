package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.SavedWord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SavedWordRepository extends JpaRepository<SavedWord, Long> {

    @Query("SELECT w FROM SavedWord w " +
            "WHERE w.user.userPid = :userPid " +
            "AND (:before IS NULL OR w.createdAt < :before) " +
            "ORDER BY w.createdAt DESC")
    List<SavedWord> findRecentWords(@Param("userPid") Long userPid,
                                    @Param("before") LocalDateTime before,
                                    Pageable pageable);

    Optional<SavedWord> findByWordIdAndUser_UserPid(Long wordId, Long userPid);
}
