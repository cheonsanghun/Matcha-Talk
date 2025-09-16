package net.datasa.project01.repository;

import net.datasa.project01.domain.entity.User;
import net.datasa.project01.domain.entity.VocabularyEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VocabularyEntryRepository extends JpaRepository<VocabularyEntry, Long> {

    List<VocabularyEntry> findByUserOrderByCreatedAtDesc(User user);
}
