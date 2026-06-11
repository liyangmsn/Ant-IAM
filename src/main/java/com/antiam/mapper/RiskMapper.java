package com.antiam.mapper;

import com.antiam.domain.RiskAssessment;
import com.antiam.domain.RiskRule;
import com.antiam.dto.RiskDtos.RiskAssessmentResponse;
import com.antiam.dto.RiskDtos.RiskRuleResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RiskMapper {

    /**
     * 将风险规则实体转换为管理端响应。
     */
    RiskRuleResponse toResponse(RiskRule rule);

    /**
     * 将风险评估实体转换为历史记录响应，并展开命中的规则编码。
     */
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "matchedRules", source = "matchedRules")
    RiskAssessmentResponse toResponse(RiskAssessment assessment);

    default List<String> mapMatchedRules(String matchedRules) {
        return matchedRules == null || matchedRules.isBlank()
            ? List.of()
            : java.util.Arrays.asList(matchedRules.split(","));
    }
}
