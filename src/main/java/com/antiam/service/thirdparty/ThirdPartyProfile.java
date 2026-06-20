package com.antiam.service.thirdparty;

import java.util.Map;

public record ThirdPartyProfile(
    String subject,
    String unionId,
    String displayName,
    String email,
    String mobile,
    String avatarUrl,
    Map<String, Object> raw
) {
}
