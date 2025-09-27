package net.datasa.project01.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class InquiryCreateRequest {
    @NotBlank @Size(max=30)  private String category;
    @NotBlank @Size(max=200) private String title;
    @NotBlank               private String content;
}
