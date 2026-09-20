package com.antiam.web;

import com.antiam.service.storage.FileStorageService;
import com.antiam.service.storage.StoredFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "文件存储", description = "文件上传接口，写入已配置的对象存储")
public class FileController {

    private final FileStorageService fileStorage;

    /**
     * 上传文件到已配置的对象存储。
     */
    @Operation(summary = "上传文件", description = "将文件写入已配置的对象存储，返回对象键和可访问地址。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    StoredFile upload(
        @Parameter(description = "待上传文件") @RequestParam("file") MultipartFile file,
        Principal principal
    ) throws IOException {
        return fileStorage.store(file.getOriginalFilename(), file.getContentType(), file.getBytes());
    }
}
