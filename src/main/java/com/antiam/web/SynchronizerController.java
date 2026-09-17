package com.antiam.web;

import static com.antiam.dto.IdentitySourceDtos.RealtimeSyncResult;

import com.antiam.service.IdentitySourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/synchronizer")
@RequiredArgsConstructor
@Tag(name = "身份源实时同步", description = "接收身份源推送的实时目录事件")
public class SynchronizerController {

    private static final String REALTIME_ACTOR = "identity-source-realtime";

    private final IdentitySourceService identitySources;

    /**
     * 接收身份源推送的实时目录事件。
     */
    @Operation(
        summary = "接收身份源事件",
        description = "接收身份源推送的组织、用户和用户组事件，使用连接器密钥引用做 HMAC-SHA256 验签后增量写入目录。")
    @PostMapping("/event_receive/{sourceCode}")
    RealtimeSyncResult receive(
        @Parameter(description = "身份源编码") @PathVariable String sourceCode,
        @Parameter(description = "HMAC-SHA256 十六进制签名，基于原始请求体计算")
        @RequestHeader(name = "X-Ant-Iam-Signature", required = false) String signature,
        @Parameter(description = "目录事件 JSON，结构与身份源连接器同步载荷一致")
        @RequestBody String rawBody
    ) {
        return identitySources.receiveRealtimeEvent(sourceCode, signature, rawBody, REALTIME_ACTOR);
    }
}
