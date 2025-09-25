// backend/src/main/java/net/datasa/project01/domain/dto/InquiryResponse.java
package net.datasa.project01.domain.dto;

import lombok.*;
import net.datasa.project01.domain.entity.UserInquiry;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InquiryResponse {
    private Long inquiryId;
    private Long userPid;
    private String userLoginId;
    private String category;
    private String title;
    private String content;
    private String status;
    private LocalDateTime answeredAt;
    private LocalDateTime createdAt;

    public static InquiryResponse of(UserInquiry q){
        return InquiryResponse.builder()
                .inquiryId(q.getInquiryId())
                .userPid(q.getUser().getUserPid())
                .userLoginId(q.getUser().getLoginId())
                .category(q.getCategory())
                .title(q.getTitle())
                .content(q.getContent())
                .status(q.getStatus())
                .answeredAt(q.getAnsweredAt())
                .createdAt(q.getCreatedAt())
                .build();
    }
}
