package com.antiam.service.identitysource;

import java.util.List;

public record DirectoryGroup(String code, String name, List<String> members) {
}
