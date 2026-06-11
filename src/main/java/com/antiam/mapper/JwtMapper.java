package com.antiam.mapper;

import com.antiam.domain.JwtSigningKey;
import com.antiam.dto.JwkDtos.SigningKeyResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface JwtMapper {

    /**
     * 将 JWT 签名密钥实体转换为管理端元数据响应。
     */
    SigningKeyResponse toResponse(JwtSigningKey key);
}
