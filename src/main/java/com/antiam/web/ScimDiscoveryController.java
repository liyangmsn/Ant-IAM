package com.antiam.web;

import static com.antiam.dto.ScimDtos.ScimAuthenticationScheme;
import static com.antiam.dto.ScimDtos.ScimFeature;
import static com.antiam.dto.ScimDtos.ScimResourceTypeResponse;
import static com.antiam.dto.ScimDtos.ScimSchemaAttribute;
import static com.antiam.dto.ScimDtos.ScimSchemaResponse;
import static com.antiam.dto.ScimDtos.ScimServiceProviderConfigResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "SCIM 发现", description = "SCIM 2.0 服务能力、资源类型和 Schema 发现接口")
public class ScimDiscoveryController {

    private static final List<String> SERVICE_PROVIDER_SCHEMA = List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig");
    private static final List<String> RESOURCE_TYPE_SCHEMA = List.of("urn:ietf:params:scim:schemas:core:2.0:ResourceType");
    private static final List<String> SCHEMA_SCHEMA = List.of("urn:ietf:params:scim:schemas:core:2.0:Schema");
    private static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";
    private static final String ORGANIZATION_SCHEMA = "urn:antiam:params:scim:schemas:extension:2.0:Organization";

    /**
     * 返回 SCIM 服务能力声明，供外部身份源识别过滤、分页等支持情况。
     */
    @Operation(summary = "获取 SCIM 服务配置", description = "返回 SCIM ServiceProviderConfig，声明过滤、分页、认证方式等能力。")
    @GetMapping("/scim/v2/ServiceProviderConfig")
    ScimServiceProviderConfigResponse serviceProviderConfig() {
        return new ScimServiceProviderConfigResponse(
            SERVICE_PROVIDER_SCHEMA,
            "/README.md",
            new ScimFeature(false),
            new ScimFeature(false),
            new ScimFeature(true),
            new ScimFeature(false),
            new ScimFeature(false),
            new ScimFeature(false),
            List.of(new ScimAuthenticationScheme(
                "oauthbearertoken",
                "OAuth Bearer Token",
                "Bearer session token authentication for SCIM API access.",
                "https://www.rfc-editor.org/rfc/rfc6750",
                "/README.md",
                true)));
    }

    /**
     * 返回当前支持的 SCIM 资源类型清单。
     */
    @Operation(summary = "获取 SCIM 资源类型", description = "返回当前支持的 SCIM User、Group 和 Organization 资源类型。")
    @GetMapping("/scim/v2/ResourceTypes")
    List<ScimResourceTypeResponse> resourceTypes() {
        return List.of(
            new ScimResourceTypeResponse(RESOURCE_TYPE_SCHEMA, "User", "User", "/Users", "User account resource", USER_SCHEMA),
            new ScimResourceTypeResponse(RESOURCE_TYPE_SCHEMA, "Group", "Group", "/Groups", "User group resource", GROUP_SCHEMA),
            new ScimResourceTypeResponse(
                RESOURCE_TYPE_SCHEMA,
                "Organization",
                "Organization",
                "/Organizations",
                "Organization directory resource",
                ORGANIZATION_SCHEMA));
    }

    /**
     * 返回所有 SCIM schema 定义。
     */
    @Operation(summary = "获取 SCIM Schema 列表", description = "返回所有支持的 SCIM Schema 定义。")
    @GetMapping("/scim/v2/Schemas")
    List<ScimSchemaResponse> schemas() {
        return List.of(userSchema(), groupSchema(), organizationSchema());
    }

    /**
     * 返回 SCIM User schema 定义。
     */
    @Operation(summary = "获取 SCIM User Schema", description = "返回 SCIM User 资源的属性定义。")
    @GetMapping("/scim/v2/Schemas/urn:ietf:params:scim:schemas:core:2.0:User")
    ScimSchemaResponse userSchema() {
        return new ScimSchemaResponse(
            SCHEMA_SCHEMA,
            USER_SCHEMA,
            "User",
            "Core user account schema",
            List.of(
                new ScimSchemaAttribute("userName", "string", false, true, "readWrite"),
                new ScimSchemaAttribute("displayName", "string", false, false, "readWrite"),
                new ScimSchemaAttribute("name", "complex", false, false, "readWrite"),
                new ScimSchemaAttribute("emails", "complex", true, false, "readWrite"),
                new ScimSchemaAttribute("phoneNumbers", "complex", true, false, "readWrite"),
                new ScimSchemaAttribute("active", "boolean", false, false, "readOnly")));
    }

    /**
     * 返回 SCIM Group schema 定义。
     */
    @Operation(summary = "获取 SCIM Group Schema", description = "返回 SCIM Group 资源的属性定义。")
    @GetMapping("/scim/v2/Schemas/urn:ietf:params:scim:schemas:core:2.0:Group")
    ScimSchemaResponse groupSchema() {
        return new ScimSchemaResponse(
            SCHEMA_SCHEMA,
            GROUP_SCHEMA,
            "Group",
            "Core group schema",
            List.of(
                new ScimSchemaAttribute("displayName", "string", false, true, "readWrite"),
                new ScimSchemaAttribute("members", "complex", true, false, "readWrite")));
    }

    /**
     * 返回 SCIM Organization schema 定义。
     */
    @Operation(summary = "获取 SCIM Organization Schema", description = "返回扩展 Organization 资源的属性定义。")
    @GetMapping("/scim/v2/Schemas/urn:antiam:params:scim:schemas:extension:2.0:Organization")
    ScimSchemaResponse organizationSchema() {
        return new ScimSchemaResponse(
            SCHEMA_SCHEMA,
            ORGANIZATION_SCHEMA,
            "Organization",
            "Organization directory schema",
            List.of(
                new ScimSchemaAttribute("externalId", "string", false, true, "readWrite"),
                new ScimSchemaAttribute("displayName", "string", false, true, "readWrite"),
                new ScimSchemaAttribute("parentId", "string", false, false, "readWrite")));
    }
}
