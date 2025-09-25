package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class InquiryCreateRequest {
    @NotNull private Long userPid;
    @NotBlank @Size(max=30)  private String category;
    @NotBlank @Size(max=200) private String title;
    @NotBlank               private String content;
}
