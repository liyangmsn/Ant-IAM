package com.antiam.web;

import com.antiam.dto.ApplicationPermissionDtos.ManagedApplicationResponse;
import com.antiam.service.ApplicationDelegationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "应用内权限", description = "应用内权限点、应用内角色与授权管理")
@RestController
@RequestMapping("/api/v1/access/me")
@RequiredArgsConstructor
public class ApplicationDelegationController {

    private final ApplicationDelegationService applicationDelegation;

    @Operation(summary = "我管理的应用", description = "返回当前用户以应用权限负责人或授权管理员身份管理的应用。")
    @GetMapping("/managed-applications")
    List<ManagedApplicationResponse> managedApplications(Principal principal) {
        return applicationDelegation.managedApplications(principal.getName());
    }
}
