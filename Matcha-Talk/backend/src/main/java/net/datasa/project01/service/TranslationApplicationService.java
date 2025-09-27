package net.datasa.project01.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.project01.domain.dto.SavedWordCreateDto;
import net.datasa.project01.domain.dto.SavedWordResponseDto;
import net.datasa.project01.domain.entity.SavedWord;
import net.datasa.project01.domain.entity.User;
import net.datasa.project01.repository.SavedWordRepository;
import net.datasa.project01.repository.UserRepository;
import net.datasa.project01.service.translation.TranslateService;
import net.datasa.project01.service.translation.TranslationResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranslationApplicationService {

    private final TranslateService translateService;
    private final SavedWordRepository savedWordRepository;
    private final UserRepository userRepository;

    @Transactional
    public TranslationResult translate(String loginId,
                                       String text,
                                       String sourceLang,
                                       String targetLang,
                                       @Nullable String context,
                                       boolean save) {
        TranslationResult result = translateService.translate(text, sourceLang, targetLang, context);

        if (save) {
            User user = userRepository.findByLoginId(loginId)
                    .orElseThrow(() -> new AccessDeniedException("사용자 정보를 찾을 수 없습니다."));

            SavedWordCreateDto createDto = new SavedWordCreateDto(text, result.getTranslatedText(), sourceLang, targetLang, context);
            savedWordRepository.save(createDto.toEntity(user));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<SavedWordResponseDto> fetchSavedWords(String loginId, int limit, @Nullable LocalDateTime before) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new AccessDeniedException("사용자 정보를 찾을 수 없습니다."));

        return savedWordRepository.findRecentWords(user.getUserPid(), before, PageRequest.of(0, limit)).stream()
                .map(SavedWordResponseDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteSavedWord(String loginId, Long wordId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new AccessDeniedException("사용자 정보를 찾을 수 없습니다."));

        SavedWord savedWord = savedWordRepository.findByWordIdAndUser_UserPid(wordId, user.getUserPid())
                .orElseThrow(() -> new AccessDeniedException("삭제할 단어가 존재하지 않거나 권한이 없습니다."));

        savedWordRepository.delete(savedWord);
    }
}
