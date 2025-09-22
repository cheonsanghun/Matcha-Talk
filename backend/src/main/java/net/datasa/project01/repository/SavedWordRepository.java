package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.SavedWord;
import net.datasa.project01.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedWordRepository extends JpaRepository<SavedWord, Long> {
    List<SavedWord> findByUserOrderByCreatedAtDesc(User user);

    Optional<SavedWord> findByWordIdAndUser(Long wordId, User user);
}
