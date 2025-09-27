package net.datasa.project01.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.datasa.project01.service.FileUploadPresignService;
import net.datasa.project01.service.FileUploadPresignService.PresignedUploadResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/rooms", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class RoomFileController {

    private final FileUploadPresignService fileUploadPresignService;

    @PostMapping("/{roomId}/files/presign")
    public ResponseEntity<PresignedUploadResponse> presignUpload(@AuthenticationPrincipal UserDetails principal,
                                                                 @PathVariable Long roomId,
                                                                 @Valid @RequestBody PresignRequest request) {
        PresignedUploadResponse response = fileUploadPresignService.createUploadUrl(
                roomId,
                principal.getUsername(),
                request.getFilename(),
                request.getContentType(),
                request.getContentLength()
        );
        return ResponseEntity.ok(response);
    }

    @Getter
    public static class PresignRequest {
        @NotBlank
        private String filename;

        @NotBlank
        private String contentType;

        @Min(1)
        private long contentLength;
    }
}
