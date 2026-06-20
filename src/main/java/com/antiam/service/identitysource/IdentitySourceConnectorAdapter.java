package com.antiam.service.identitysource;

import com.antiam.domain.IdentitySource;
import com.antiam.domain.IdentitySourceConnector;
import com.antiam.domain.IdentitySourceType;

public interface IdentitySourceConnectorAdapter {

    boolean supports(IdentitySourceType type);

    DirectorySyncPayload load(IdentitySource source, IdentitySourceConnector connector);
}
