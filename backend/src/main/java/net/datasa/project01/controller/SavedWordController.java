package net.datasa.project01.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.domain.dto.SavedWordRequestDto;
import net.datasa.project01.domain.dto.SavedWordResponseDto;
import net.datasa.project01.service.SavedWordService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/words")
public class SavedWordController {

    private final SavedWordService savedWordService;

    @PostMapping
    public ResponseEntity<SavedWordResponseDto> saveWord(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SavedWordRequestDto requestDto
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        SavedWordResponseDto response = savedWordService.saveWord(userDetails.getUsername(), requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<SavedWordResponseDto>> getSavedWords(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<SavedWordResponseDto> words = savedWordService.getSavedWords(userDetails.getUsername());
        return ResponseEntity.ok(words);
    }

    @DeleteMapping("/{wordId}")
    public ResponseEntity<Void> deleteWord(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long wordId
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        savedWordService.deleteWord(userDetails.getUsername(), wordId);
        return ResponseEntity.noContent().build();
    }
}
