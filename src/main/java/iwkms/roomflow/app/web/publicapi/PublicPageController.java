package iwkms.roomflow.app.web.publicapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import iwkms.roomflow.modules.booking.impl.domain.Room;
import iwkms.roomflow.modules.booking.impl.repository.RoomRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequiredArgsConstructor
public class PublicPageController {
    private final RoomRepository roomRepository;
    private final ObjectMapper mapper;

    @Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @GetMapping(value = "/web/schedule", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> schedule() throws IOException {
        return page(
                "RoomFlow — бронирование переговорных",
                "Выберите переговорную и свободное время для встречи команды.",
                "/schedule",
                HttpStatus.OK,
                Map.of(
                        "@context",
                        "https://schema.org",
                        "@type",
                        "WebApplication",
                        "name",
                        "RoomFlow",
                        "applicationCategory",
                        "BusinessApplication"));
    }

    @GetMapping(value = "/web/schedule/room/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> room(@PathVariable String id) throws IOException {
        Room room = null;
        try {
            room = roomRepository.findByIdAndActiveTrue(UUID.fromString(id)).orElse(null);
        } catch (IllegalArgumentException ignored) {
            // An invalid public identifier is an unknown page.
        }
        if (room == null) {
            return page(
                    "Переговорная не найдена — RoomFlow",
                    "Эта переговорная недоступна.",
                    "/schedule/room/" + id,
                    HttpStatus.NOT_FOUND,
                    Map.of());
        }
        String description = "Переговорная " + room.getName() + ": этаж " + room.getFloor() + ", мест: "
                + room.getCapacity() + ". Расписание и бронирование.";
        return page(
                room.getName() + " — RoomFlow",
                description,
                "/schedule/room/" + id,
                HttpStatus.OK,
                Map.of(
                        "@context",
                        "https://schema.org",
                        "@type",
                        "Place",
                        "name",
                        room.getName(),
                        "maximumAttendeeCapacity",
                        room.getCapacity()));
    }

    @GetMapping(value = "/web/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return "User-agent: *\nAllow: /schedule\nDisallow: /admin\nDisallow: /my-bookings\nDisallow: /login\nDisallow: /register\nDisallow: /api/\nDisallow: /booking/\nSitemap: "
                + base() + "/sitemap.xml\n";
    }

    private ResponseEntity<String> page(
            String title, String description, String path, HttpStatus status, Map<String, Object> data)
            throws IOException {
        ClassPathResource resource = new ClassPathResource("static/index.html");
        if (!resource.exists()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Frontend is not built. Run npm ci and npm run build in frontend before bootJar.");
        }
        String template = resource.getContentAsString(StandardCharsets.UTF_8);
        String canonical = HtmlUtils.htmlEscape(base() + path);
        String safeTitle = HtmlUtils.htmlEscape(title);
        String safeDescription = HtmlUtils.htmlEscape(description);
        String metadata = "<meta name=\"description\" content=\"" + safeDescription + "\">"
                + "<link rel=\"canonical\" href=\"" + canonical + "\">"
                + "<meta name=\"robots\" content=\"" + (status == HttpStatus.OK ? "index,follow" : "noindex,nofollow")
                + "\">"
                + "<meta property=\"og:title\" content=\"" + safeTitle + "\">"
                + "<meta property=\"og:description\" content=\"" + safeDescription + "\">"
                + "<meta property=\"og:url\" content=\"" + canonical + "\">"
                + "<meta property=\"og:type\" content=\"website\">"
                + "<meta property=\"og:image\" content=\"" + HtmlUtils.htmlEscape(base() + "/og-default.svg") + "\">"
                + jsonLd(data);
        return ResponseEntity.status(status)
                .body(template.replaceAll(
                                "<title>[^<]*</title>",
                                "<title>" + java.util.regex.Matcher.quoteReplacement(safeTitle) + "</title>")
                        .replace("<!-- PUBLIC_SEO -->", metadata));
    }

    private String jsonLd(Map<String, Object> data) throws JsonProcessingException {
        if (data.isEmpty()) {
            return "";
        }
        return "<script type=\"application/ld+json\" data-rf-seo=\"jsonld\">"
                + mapper.writeValueAsString(data).replace("<", "\\u003c") + "</script>";
    }

    private String base() {
        return publicBaseUrl.replaceAll("/+$", "");
    }
}
