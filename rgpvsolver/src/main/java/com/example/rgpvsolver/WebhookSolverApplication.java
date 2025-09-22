package in.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@SpringBootApplication
public class WebhookSolverApplication implements CommandLineRunner {

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        SpringApplication.run(WebhookSolverApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 1) Generate webhook - replace name/regNo/email as needed
        String generateUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";

        GenerateBody body = new GenerateBody("John Doe", "REG12347", "john@example.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<GenerateBody> request = new HttpEntity<>(body, headers);

        System.out.println("Sending generateWebhook request...");
        ResponseEntity<String> resp = rest.postForEntity(new URI(generateUrl), request, String.class);

        if (!resp.getStatusCode().is2xxSuccessful()) {
            System.err.println("generateWebhook returned non-2xx: " + resp.getStatusCode());
            System.exit(1);
        }

        // parse response JSON to extract webhook & accessToken
        Map<String, Object> respMap = mapper.readValue(resp.getBody(), Map.class);
        // The actual JSON structure may vary. Adjust keys as returned by API.
        String webhookUrl = (String) respMap.get("webhook");
        String accessToken = (String) respMap.get("accessToken");

        System.out.println("Received webhook: " + webhookUrl);
        System.out.println("Received accessToken: " + (accessToken == null ? "<null>" : "<REDACTED>"));

        // Example: If API returns a questionUrl in the response, download it:
        if (respMap.containsKey("questionUrl")) {
            String qUrl = (String) respMap.get("questionUrl");
            try {
                downloadFile(qUrl, "question.pdf");
                System.out.println("Downloaded question.pdf from questionUrl");
                // Optional: parse PDF and extract text (see parsePdf placeholder)
            } catch (Exception ex) {
                System.err.println("Failed to download question: " + ex.getMessage());
            }
        } else {
            System.out.println("No questionUrl returned in generateWebhook response. Check email/regNo flow or the docs.");
        }

            String manualQuery =
        "SELECT p.AMOUNT AS SALARY,\n" +
        "       CONCAT(e.FIRST_NAME, ' ', e.LAST_NAME) AS NAME,\n" +
        "       TIMESTAMPDIFF(YEAR, e.DOB, CURDATE()) AS AGE,\n" +
        "       d.DEPARTMENT_NAME\n" +
        "FROM PAYMENTS p\n" +
        "JOIN EMPLOYEE e ON p.EMP_ID = e.EMP_ID\n" +
        "JOIN DEPARTMENT d ON e.DEPARTMENT = d.DEPARTMENT_ID\n" +
        "WHERE DAY(p.PAYMENT_TIME) <> 1\n" +
        "ORDER BY p.AMOUNT DESC\n" +
        "LIMIT 1;";

        String finalQuery = manualQuery;

     
       // Prepare submission body
        Map<String, String> submitBody = Map.of("finalQuery", finalQuery);

        HttpHeaders submitHeaders = new HttpHeaders();
        submitHeaders.setContentType(MediaType.APPLICATION_JSON);
        // Use the returned accessToken as JWT in Authorization header
        if (accessToken != null) submitHeaders.set("Authorization", accessToken);

        HttpEntity<Map<String, String>> submitReq = new HttpEntity<>(submitBody, submitHeaders);

        System.out.println("Submitting finalQuery to webhook URL...");
        ResponseEntity<String> submitResp = rest.postForEntity(new URI("https://bfhldevapigw.healthrx.co.in/hiring/testWebhook/JAVA"), submitReq, String.class);

        System.out.println("Submission status: " + submitResp.getStatusCode());
        System.out.println("Submission response body: " + submitResp.getBody());

        System.out.println("Flow completed. Replace manualQuery variable with the actual SQL string before building jar.");
        // Exit application after the startup-run flow
        System.exit(0);
    }

    private void downloadFile(String fileUrl, String outFilename) throws Exception {
        ResponseEntity<byte[]> fileResp = rest.getForEntity(new URI(fileUrl), byte[].class);
        if (!fileResp.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Download failed status: " + fileResp.getStatusCode());
        }
        try (FileOutputStream fos = new FileOutputStream(outFilename)) {
            fos.write(fileResp.getBody());
        }
    }

    // JSON mapping
    static class GenerateBody {
        @JsonProperty("name") public String name;
        @JsonProperty("regNo") public String regNo;
        @JsonProperty("email") public String email;

        public GenerateBody() {}
        public GenerateBody(String name, String regNo, String email) {
            this.name = name;
            this.regNo = regNo;
            this.email = email;
        }
    }
}
