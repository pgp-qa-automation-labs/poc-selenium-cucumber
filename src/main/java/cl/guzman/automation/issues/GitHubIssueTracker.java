package cl.guzman.automation.issues;

import cl.guzman.automation.triage.Diagnostico;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Publica diagnósticos como GitHub Issues usando la API REST.
 * En GitHub Actions se autentica con el GITHUB_TOKEN del workflow (permiso issues: write).
 */
public class GitHubIssueTracker implements IssueTracker {

    static final String ETIQUETA_BASE = "triage-ia";
    private static final String API = "https://api.github.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Map<String, String> COLORES = Map.of(
            "severidad", "d73a4a", "categoria", "5319e7", "area", "0e8a16", ETIQUETA_BASE, "1d76db", "demo", "fbca04");

    private final String repositorio;
    private final String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public GitHubIssueTracker(String repositorio, String token) {
        this.repositorio = repositorio;
        this.token = token;
    }

    @Override
    public String nombre() {
        return "GitHub (" + repositorio + ")";
    }

    @Override
    public ResultadoIssue publicar(Diagnostico d, ContextoEjecucion contexto, boolean dryRun) {
        String huella = Huella.de(d);
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("title", titulo(d));
        issue.put("body", cuerpo(d, contexto, huella));
        issue.put("labels", etiquetas(d));
        String payload = json(issue);
        String vista = "# " + titulo(d) + "\n\n**Etiquetas:** " + String.join(", ", etiquetas(d)) + "\n\n" + issue.get("body");

        if (dryRun) {
            return new ResultadoIssue(ResultadoIssue.Accion.SIMULADO, huella, titulo(d), null,
                    "Modo dry-run: no se envió nada a GitHub", payload, vista);
        }
        if (token == null || token.isBlank()) {
            return new ResultadoIssue(ResultadoIssue.Accion.OMITIDO, huella, titulo(d), null,
                    "Falta GITHUB_TOKEN para publicar en GitHub", payload, vista);
        }

        try {
            Optional<JsonNode> existente = buscarAbierto(huella);
            if (existente.isPresent()) {
                int numero = existente.get().path("number").asInt();
                String comentario = json(Map.of("body", comentarioRepeticion(d, contexto)));
                enviar("POST", "/repos/" + repositorio + "/issues/" + numero + "/comments", comentario);
                return new ResultadoIssue(ResultadoIssue.Accion.COMENTADO, huella, titulo(d),
                        existente.get().path("html_url").asText(), "El fallo ya tenía un issue abierto: se agregó un comentario", comentario, vista);
            }
            asegurarEtiquetas(etiquetas(d));
            JsonNode creado = enviar("POST", "/repos/" + repositorio + "/issues", payload);
            return new ResultadoIssue(ResultadoIssue.Accion.CREADO, huella, titulo(d), creado.path("html_url").asText(),
                    "Issue #" + creado.path("number").asInt() + " creado", payload, vista);
        } catch (IOException | RuntimeException e) {
            return new ResultadoIssue(ResultadoIssue.Accion.ERROR, huella, titulo(d), null, e.getMessage(), payload, vista);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ResultadoIssue(ResultadoIssue.Accion.ERROR, huella, titulo(d), null, "Interrumpido", payload, vista);
        }
    }

    static String titulo(Diagnostico d) {
        return (d.simulado() ? "[Demo] " : "") + "[" + d.severidad() + "] " + d.titulo();
    }

    static List<String> etiquetas(Diagnostico d) {
        List<String> etiquetas = new ArrayList<>(List.of(
                ETIQUETA_BASE,
                "severidad:" + d.severidad().name().toLowerCase(Locale.ROOT),
                "categoria:" + d.categoria().name().toLowerCase(Locale.ROOT).replace('_', '-'),
                "area:" + d.areaResponsable().name().toLowerCase(Locale.ROOT)));
        if (d.simulado()) {
            etiquetas.add("demo");
        }
        return etiquetas;
    }

    static String cuerpo(Diagnostico d, ContextoEjecucion c, String huella) {
        StringBuilder md = new StringBuilder();
        md.append("> 🤖 Issue creado automáticamente por el agente de triage de la suite E2E. ")
                .append("Es un diagnóstico asistido por IA: valida la evidencia antes de actuar.\n");
        if (d.simulado()) {
            md.append(">\n> ⚠️ **Demo:** el fallo proviene de un escenario que simula un problema; no es un incidente real.\n");
        }
        md.append("\n**Severidad:** ").append(d.severidad())
                .append(" · **Categoría:** ").append(d.categoria())
                .append(" · **Área:** ").append(d.areaResponsable())
                .append(" · **Confianza:** ").append(d.confianza()).append("%\n\n")
                .append("### Causa probable\n").append(d.causaProbable()).append("\n\n")
                .append("### Evidencias\n");
        d.evidencias().forEach(e -> md.append("- ").append(e).append("\n"));
        md.append("\n### Acción recomendada\n").append(d.accionRecomendada()).append("\n\n")
                .append("### Contexto\n")
                .append("| | |\n|---|---|\n")
                .append("| Escenario | ").append(d.escenario()).append(" |\n")
                .append("| Feature | `").append(d.feature()).append("` |\n")
                .append("| Paso fallido | ").append(d.pasoFallido()).append(" |\n")
                .append("| URL | ").append(d.url()).append(" |\n")
                .append("| Ambiente | ").append(d.ambiente()).append(" |\n")
                .append("| Origen | ").append(c.origen()).append(" |\n")
                .append("| Rama / commit | `").append(c.rama()).append("` / `").append(c.commit()).append("` |\n")
                .append("| Ejecución | ").append(c.urlEjecucion() == null ? "local" : "[Ver captura y reportes](" + c.urlEjecucion() + ")").append(" |\n")
                .append("| Análisis | ").append(d.modelo()).append(", ").append(d.iteraciones()).append(" iteraciones |\n\n")
                .append("<!-- triage-huella: ").append(huella).append(" -->\n");
        return md.toString();
    }

    private static String comentarioRepeticion(Diagnostico d, ContextoEjecucion c) {
        return "🔁 El fallo se repitió (" + c.origen() + ", rama `" + c.rama() + "`, commit `" + c.commit() + "`). "
                + "Confianza del nuevo diagnóstico: " + d.confianza() + "%."
                + (c.urlEjecucion() == null ? "" : " [Ver ejecución](" + c.urlEjecucion() + ")");
    }

    private Optional<JsonNode> buscarAbierto(String huella) throws IOException, InterruptedException {
        JsonNode issues = enviar("GET", "/repos/" + repositorio + "/issues?state=open&labels=" + ETIQUETA_BASE + "&per_page=100", null);
        for (JsonNode issue : issues) {
            if (issue.path("body").asText("").contains("triage-huella: " + huella)) {
                return Optional.of(issue);
            }
        }
        return Optional.empty();
    }

    private void asegurarEtiquetas(List<String> etiquetas) throws IOException, InterruptedException {
        for (String etiqueta : etiquetas) {
            String prefijo = etiqueta.contains(":") ? etiqueta.substring(0, etiqueta.indexOf(':')) : etiqueta;
            String cuerpo = json(Map.of("name", etiqueta, "color", COLORES.getOrDefault(prefijo, "ededed")));
            HttpResponse<String> respuesta = http.send(peticion("POST", "/repos/" + repositorio + "/labels", cuerpo),
                    HttpResponse.BodyHandlers.ofString());
            // 422 = la etiqueta ya existe
            if (respuesta.statusCode() >= 400 && respuesta.statusCode() != 422) {
                throw new IOException("No se pudo crear la etiqueta '" + etiqueta + "': HTTP " + respuesta.statusCode());
            }
        }
    }

    private JsonNode enviar(String metodo, String ruta, String cuerpo) throws IOException, InterruptedException {
        HttpResponse<String> respuesta = http.send(peticion(metodo, ruta, cuerpo), HttpResponse.BodyHandlers.ofString());
        if (respuesta.statusCode() >= 300) {
            throw new IOException("GitHub respondió HTTP " + respuesta.statusCode() + " en " + metodo + " " + ruta + ": "
                    + recortar(respuesta.body()));
        }
        return MAPPER.readTree(respuesta.body());
    }

    private HttpRequest peticion(String metodo, String ruta, String cuerpo) {
        return HttpRequest.newBuilder(URI.create(API + ruta))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
    }

    private static String json(Object valor) {
        try {
            return MAPPER.writeValueAsString(valor);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo serializar el payload", e);
        }
    }

    private static String recortar(String texto) {
        return texto == null ? "" : texto.length() > 300 ? texto.substring(0, 300) + "..." : texto;
    }
}
