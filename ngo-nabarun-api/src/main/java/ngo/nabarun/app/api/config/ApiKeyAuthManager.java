package ngo.nabarun.app.api.config;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import ngo.nabarun.app.common.enums.ApiKeyStatus;
import ngo.nabarun.app.infra.dto.ApiKeyDTO;
import ngo.nabarun.app.infra.service.IApiKeyInfraService;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.util.concurrent.TimeUnit;

/**
 * Handles authenticating api keys against the database.
 */
@Configuration
public class ApiKeyAuthManager implements AuthenticationManager {
	private static final Logger LOG = LoggerFactory.getLogger(ApiKeyAuthManager.class);
	private @NonNull LoadingCache<String, ApiKeyDTO> keys;

	public ApiKeyAuthManager(IApiKeyInfraService apiKeyInfraService) {
		this.keys = Caffeine.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES)
				.build(new DatabaseCacheLoader(apiKeyInfraService));
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String principal = (String) authentication.getPrincipal();
		String credential = (String) authentication.getCredentials();
		//System.out.println(credential);

		@Nullable
		ApiKeyDTO apiKey = this.keys.get(principal);
		boolean isAuthenticated = apiKey != null && apiKey.getStatus() == ApiKeyStatus.ACTIVE
				&& apiKey.getScopes().contains(credential);

		if (isAuthenticated) {
			authentication.setAuthenticated(true);
			return authentication;
		} else {
			throw new BadCredentialsException("The API key was not found or not the expected value.");
		}
	}

	/**
	 * Caffeine CacheLoader that checks the database for the api key if it not found
	 * in the cache.
	 */
	private static class DatabaseCacheLoader implements CacheLoader<String, ApiKeyDTO> {
		private final IApiKeyInfraService apiKeyInfraService;

		DatabaseCacheLoader(IApiKeyInfraService apiKeyInfraService) {
			this.apiKeyInfraService = apiKeyInfraService;
		}

		@Override
		public ApiKeyDTO load(String key) throws Exception {
			LOG.info("Loading api key from database: [key: {}]", key);
			try {
				return apiKeyInfraService.getApiKeyDetail(key);
			} catch (Exception e) {
				return null;
			}
		}
	}
}