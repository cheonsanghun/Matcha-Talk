package net.datasa.project01.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.datasa.project01.domain.entity.FollowRequest;
import net.datasa.project01.domain.entity.Room;
import net.datasa.project01.domain.entity.User;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FollowResponseDto {

    private Long followRequestId;
    private Long roomId;
    private String status;
    private String direction;
    private boolean actionable;
    private boolean accepted;
    private String partnerLoginId;
    private String partnerNickName;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
    private String message;

    public static FollowResponseDto from(FollowRequest request, User viewer) {
        return from(request, viewer, null);
    }

    public static FollowResponseDto from(FollowRequest request, User viewer, String message) {
        if (request == null || viewer == null) {
            return FollowResponseDto.builder()
                    .status("NONE")
                    .actionable(false)
                    .accepted(false)
                    .message(message)
                    .build();
        }

        boolean viewerIsRequester = request.getRequester() != null
                && request.getRequester().getUserPid() != null
                && request.getRequester().getUserPid().equals(viewer.getUserPid());

        User partner = viewerIsRequester ? request.getReceiver() : request.getRequester();
        FollowRequest.Status status = request.getStatus();

        return FollowResponseDto.builder()
                .followRequestId(request.getFollowRequestId())
                .roomId(request.getRoom() != null ? request.getRoom().getRoomId() : null)
                .status(status != null ? status.name() : "NONE")
                .direction(viewerIsRequester ? "OUTGOING" : "INCOMING")
                .actionable(status == FollowRequest.Status.PENDING && !viewerIsRequester)
                .accepted(status == FollowRequest.Status.ACCEPTED)
                .partnerLoginId(partner != null ? partner.getLoginId() : null)
                .partnerNickName(partner != null ? partner.getNickName() : null)
                .createdAt(request.getCreatedAt())
                .respondedAt(request.getRespondedAt())
                .message(message)
                .build();
    }

    public static FollowResponseDto none(Room room, User viewer, User partner) {
        return FollowResponseDto.builder()
                .followRequestId(null)
                .roomId(room != null ? room.getRoomId() : null)
                .status("NONE")
                .direction(null)
                .actionable(false)
                .accepted(false)
                .partnerLoginId(partner != null ? partner.getLoginId() : null)
                .partnerNickName(partner != null ? partner.getNickName() : null)
                .createdAt(null)
                .respondedAt(null)
                .message(null)
                .build();
    }
}
