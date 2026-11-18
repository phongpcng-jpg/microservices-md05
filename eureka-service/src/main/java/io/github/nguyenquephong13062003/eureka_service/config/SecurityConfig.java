package io.github.nguyenquephong13062003.eureka_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(authorize -> authorize
					.requestMatchers("/actuator/health").permitAll()
					.anyRequest().authenticated())
			.httpBasic(Customizer.withDefaults())
			.csrf(csrf -> csrf.ignoringRequestMatchers("/eureka/**"));

		return http.build();
	}
}
