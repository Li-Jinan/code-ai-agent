package com.jinan.codeaiagent.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Fails fast when the public main-site and generated-site origins are inconsistent.
 * A bad value here otherwise survives startup and is only discovered after a user
 * deploys an application and clicks its link.
 */
@Component
@Slf4j
public class PublicUrlConfigurationValidator {

    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    @Value("${code.deploy-host:http://localhost}")
    private String deployBaseUrl;

    @PostConstruct
    public void validate() {
        validateOrigins(publicBaseUrl, deployBaseUrl);
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            log.info("Validated public origins: main={}, generated={}", publicBaseUrl, deployBaseUrl);
        }
    }

    static void validateOrigins(String publicBaseUrl, String deployBaseUrl) {
        // Local development does not define a public origin.
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return;
        }

        URI publicUri = parseOrigin("PUBLIC_BASE_URL", publicBaseUrl);
        URI deployUri = parseOrigin("DEPLOY_BASE_URL", deployBaseUrl);

        if (sameOrigin(publicUri, deployUri)) {
            throw new IllegalStateException("DEPLOY_BASE_URL must use a different origin from PUBLIC_BASE_URL");
        }
        if ("https".equalsIgnoreCase(publicUri.getScheme())
                && !"https".equalsIgnoreCase(deployUri.getScheme())) {
            throw new IllegalStateException("DEPLOY_BASE_URL must use HTTPS when PUBLIC_BASE_URL uses HTTPS");
        }
        if (deployUri.getPort() == 8080) {
            throw new IllegalStateException("DEPLOY_BASE_URL must not expose the legacy public port 8080");
        }

    }

    private static URI parseOrigin(String name, String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if ((scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(name + " must be an absolute HTTP(S) origin without path/query/fragment");
        }
    }

    private static boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equals(uri.getScheme().toLowerCase(Locale.ROOT)) ? 443 : 80;
    }
}
