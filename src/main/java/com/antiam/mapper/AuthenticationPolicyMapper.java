package com.antiam.mapper;

import com.antiam.domain.AuthenticationPolicy;
import com.antiam.dto.PolicyDtos.AuthenticationPolicyResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthenticationPolicyMapper {

    /**
     * 将认证策略实体转换为策略管理响应。
     */
    AuthenticationPolicyResponse toResponse(AuthenticationPolicy policy);
}
