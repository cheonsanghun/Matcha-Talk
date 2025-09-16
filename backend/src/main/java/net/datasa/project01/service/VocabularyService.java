package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.VocabularyEntryResponseDto;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.domain.entity.VocabularyEntry;
import net.datasa.project01.repository.UserRepository;
import net.datasa.project01.repository.VocabularyEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class VocabularyService {

    private final VocabularyEntryRepository vocabularyEntryRepository;
    private final UserRepository userRepository;

    public VocabularyEntryResponseDto addWord(String loginId, String original, String translated) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        VocabularyEntry entry = vocabularyEntryRepository.save(
                VocabularyEntry.builder()
                        .user(user)
                        .originalText(original)
                        .translatedText(translated)
                        .build()
        );

        return VocabularyEntryResponseDto.from(entry);
    }

    @Transactional(readOnly = true)
    public List<VocabularyEntryResponseDto> list(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return vocabularyEntryRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(VocabularyEntryResponseDto::from)
                .collect(Collectors.toList());
    }
}
