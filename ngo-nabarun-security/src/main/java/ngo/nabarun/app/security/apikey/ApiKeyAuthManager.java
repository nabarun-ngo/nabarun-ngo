package ngo.nabarun.app.security.apikey;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.TimeUnit;

/**
 * Handles authenticating api keys against the database.
 */
public class ApiKeyAuthManager implements AuthenticationManager {
    private static final Logger LOG = LoggerFactory.getLogger(ApiKeyAuthManager.class);

   // private final LoadingCache<String, Boolean> keys;

 //   public ApiKeyAuthManager() {
//        this.keys = Caffeine.newBuilder()
//                .expireAfterAccess(5, TimeUnit.MINUTES)
//                .build(new DatabaseCacheLoader(dataSource));
 //   }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String principal = (String) authentication.getPrincipal();
       
        
        System.out.println(principal);
        System.out.println(authentication.getCredentials());
        System.out.println(authentication.getDetails());
        System.out.println(authentication.getName());
        if (principal.equals("1234")) {
            authentication.setAuthenticated(true);
            return authentication;
        } else {
            throw new BadCredentialsException("The API key was not found or not the expected value.");
        }
    }

    /**
     * Caffeine CacheLoader that checks the database for the api key if it not found in the cache.
     */
    private static class DatabaseCacheLoader implements CacheLoader<String, Boolean> {
        private final DataSource dataSource;

        DatabaseCacheLoader(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public Boolean load(String key) throws Exception {
            LOG.info("Loading api key from database: [key: {}]", key);

            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM auth WHERE api_key = ?")) {
                   // ps.setObject(1, UUIDUtil.fromHex(key));

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            // Valid API Key
                            return true;
                        } else {
                            // Invalid API Key
                            return false;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("An error occurred while retrieving api key from database", e);
                return false;
            }
        }
    }
}