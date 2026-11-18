package io.github.nguyenquephong13062003.eureka_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EurekaServiceApplicationTests {

	@LocalServerPort
	private int port;

	@Value("${spring.security.user.name}")
	private String username;

	@Value("${spring.security.user.password}")
	private String password;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Test
	void dashboardRequiresAuthentication() throws Exception {
		HttpResponse<String> response = get("/", false);

		assertEquals(401, response.statusCode());
	}

	@Test
	void dashboardAcceptsValidBasicCredentials() throws Exception {
		HttpResponse<String> response = get("/", true);

		assertEquals(200, response.statusCode());
	}

	@Test
	void actuatorHealthIsPublic() throws Exception {
		HttpResponse<String> response = get("/actuator/health", false);

		assertEquals(200, response.statusCode());
	}

	@Test
	void actuatorEnvironmentEndpointIsNotExposed() throws Exception {
		HttpResponse<String> response = get("/actuator/env", true);

		assertEquals(404, response.statusCode());
	}

	@Test
	void eurekaApiDoesNotRequireCsrfToken() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(uri("/eureka/apps/CI_TEST"))
				.header("Authorization", basicAuthorization())
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertNotEquals(403, response.statusCode());
	}

	private HttpResponse<String> get(String path, boolean authenticated) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
		if (authenticated) {
			request.header("Authorization", basicAuthorization());
		}
		return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private String basicAuthorization() {
		String credentials = username + ":" + password;
		String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
		return "Basic " + encoded;
	}
}
