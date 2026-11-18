package com.codebuzzers.codebuzzcore.meta;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private final MetaConnectionService connections;

    public MetaController(MetaConnectionService connections) {
        this.connections = connections;
    }

    @GetMapping("/connect")
    public Map<String, Object> connect() {
        return connections.authorizationUrl();
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state) {
        String result = connections.callback(code, state);
        if (result.startsWith("redirect:")) {
            return ResponseEntity.status(HttpStatus.FOUND).header("Location", result.substring("redirect:".length())).build();
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body("<html><body><p>Meta is connected. You can return to the app.</p></body></html>");
    }

    @GetMapping("/connection")
    public Map<String, Object> connection() {
        return connections.current();
    }

    @PostMapping("/assets")
    public Map<String, Object> select(@RequestBody AssetSelection request) {
        return connections.selectAssets(request.pageIds(), request.adAccountIds());
    }

    @PostMapping("/disconnect")
    public Map<String, Object> disconnect() {
        connections.disconnectCurrent();
        return Map.of("ok", true);
    }

    @GetMapping("/forms")
    public List<Map<String, Object>> forms() {
        return connections.listForms();
    }

    @PutMapping("/forms/{id}/mappings")
    public Map<String, Object> mappings(@PathVariable String id, @RequestBody MappingRequest request) {
        connections.replaceMappings(id, request.mappings());
        return Map.of("ok", true);
    }

    public record AssetSelection(List<String> pageIds, List<String> adAccountIds) {
    }

    public record MappingRequest(List<Map<String, String>> mappings) {
    }
}
