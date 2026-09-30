package com.damso.match.api.app.dating.dto;

import com.damso.match.domain.dating.dto.DatingAnswerView;
import com.damso.match.domain.dating.entity.DatingProfile;
import com.damso.match.domain.dating.entity.DatingStatus;
import com.damso.match.global.common.Gender;

import java.util.List;

/**
 * 본인 프로필 (등록/로그인 후 본인에게만 반환. 이름·연락처 포함).
 *  - answers: 본인이 작성한 설문 답변. 수정 화면 프리필에 쓴다.
 *  - canSelect/notice: 지금 이성 프로필을 보고 고를 수 있는 상태인지와, 아니라면 그 안내 문구.
 *  - visible: 본인이 켜둔 노출 스위치. 끄면 남의 목록에서 빠지고 나도 열람할 수 없다.
 *  - verified: 매장에서 직접 확인받은 회원인지.
 */
public record DatingSelfDto(
        Long id,
        String name,
        String contact,
        Integer birthYear,
        Gender gender,
        String photoUrl,
        List<DatingAnswerView> answers,
        DatingStatus status,
        String statusLabel,
        boolean canSelect,
        String notice,
        boolean visible,
        boolean verified
) {
    public static DatingSelfDto from(DatingProfile p, List<DatingAnswerView> answers) {
        DatingStatus status = DatingStatus.orDefault(p.getStatus());
        boolean visible = !p.isHidden();
        // 관리자가 막아둔 상태가 아니어도, 본인이 노출을 꺼두면 열람할 수 없다.
        String notice = status.notice();
        if (notice == null && !visible) notice = DatingProfile.HIDDEN_NOTICE;
        return new DatingSelfDto(
                p.getId(), p.getName(), p.getContact(),
                p.getBirthYear(), p.getGender(), p.getPhotoUrl(),
                answers == null ? List.of() : answers,
                status, status.label(), status.isBrowsable() && visible, notice,
                visible, p.isVerified()
        );
    }
}
