package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.SavedWordRequestDto;
import net.datasa.project01.domain.dto.SavedWordResponseDto;
import net.datasa.project01.domain.entity.SavedWord;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.SavedWordRepository;
import net.datasa.project01.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class SavedWordService {

    private final SavedWordRepository savedWordRepository;
    private final UserRepository userRepository;

    public SavedWordResponseDto saveWord(String loginId, SavedWordRequestDto requestDto) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        SavedWord savedWord = savedWordRepository.save(SavedWord.builder()
                .user(user)
                .sourceText(requestDto.getSourceText())
                .translatedText(requestDto.getTranslatedText())
                .sourceLang(requestDto.getSourceLang())
                .targetLang(requestDto.getTargetLang())
                .context(requestDto.getContext())
                .build());

        log.info("Saved translated word for user {}", loginId);
        return toResponse(savedWord);
    }

    @Transactional(readOnly = true)
    public List<SavedWordResponseDto> getSavedWords(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return savedWordRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public void deleteWord(String loginId, Long wordId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        SavedWord savedWord = savedWordRepository.findByWordIdAndUser(wordId, user)
                .orElseThrow(() -> new IllegalArgumentException("단어를 찾을 수 없습니다."));

        savedWordRepository.delete(savedWord);
        log.info("Deleted saved word {} for user {}", wordId, loginId);
    }

    private SavedWordResponseDto toResponse(SavedWord savedWord) {
        return SavedWordResponseDto.builder()
                .wordId(savedWord.getWordId())
                .sourceText(savedWord.getSourceText())
                .translatedText(savedWord.getTranslatedText())
                .sourceLang(savedWord.getSourceLang())
                .targetLang(savedWord.getTargetLang())
                .context(savedWord.getContext())
                .createdAt(savedWord.getCreatedAt())
                .build();
    }
}
