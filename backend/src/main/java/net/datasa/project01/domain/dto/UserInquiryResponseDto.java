package net.datasa.project01.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.datasa.project01.domain.entity.UserInquiry;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
@AllArgsConstructor
public class UserInquiryResponseDto {

    private final Long inquiryId;
    private final Long userId;
    private final String nickname;
    private final String category;
    private final String title;
    private final String content;
    private final String status;
    private final String createdAt;
    private final String answeredAt;

    public static UserInquiryResponseDto fromEntity(UserInquiry inquiry) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String formattedAnsweredAt = (inquiry.getAnsweredAt() != null) ? inquiry.getAnsweredAt().format(formatter) : null;

        return UserInquiryResponseDto.builder()
                .inquiryId(inquiry.getInquiryId())
                .userId(inquiry.getUser().getUserPid())
                .nickname(inquiry.getUser().getNickName())
                .category(inquiry.getCategory())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .status(inquiry.getStatus().name())
                .createdAt(inquiry.getCreatedAt().format(formatter))
                .answeredAt(formattedAnsweredAt)
                .build();
    }
}
