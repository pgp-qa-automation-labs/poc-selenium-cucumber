package cl.guzman.automation.issues;

import cl.guzman.automation.config.EnvironmentConfig;
import cl.guzman.automation.triage.Diagnostico;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publica diagnósticos como issues de Jira Cloud (API REST v3). La descripción va en ADF (Atlassian Document Format).
 * La huella del fallo se guarda como etiqueta para encontrar el issue abierto y comentar en vez de duplicar.
 *
 * <p>Requiere: issues.jiraBaseUrl, issues.jiraProjectKey, JIRA_EMAIL y JIRA_API_TOKEN.
 * Sin esos datos solo genera la vista previa.
 */
public class JiraIssueTracker implements IssueTracker {

    private final String baseUrl;
    private final String proyecto;
    private final String tipoIssue;
    private final String tipoTarea;
    private final String email;
    private final String token;

    public JiraIssueTracker(EnvironmentConfig.Issues cfg, EnvironmentConfig.Secrets secrets) {
        this.baseUrl = cfg.jiraBaseUrl() == null ? "" : cfg.jiraBaseUrl().replaceAll("/+$", "");
        this.proyecto = cfg.jiraProjectKey();
        this.tipoIssue = ClienteHttp.vacio(cfg.jiraIssueType()) ? "Bug" : cfg.jiraIssueType();
        this.tipoTarea = ClienteHttp.vacio(cfg.jiraTaskType()) ? "Task" : cfg.jiraTaskType();
        this.email = secrets.jiraEmail();
        this.token = secrets.jiraApiToken();
    }

    @Override
    public String nombre() {
        return "Jira (" + (ClienteHttp.vacio(baseUrl) ? "sin configurar" : baseUrl + ", proyecto " + proyecto) + ")";
    }

    @Override
    public ResultadoIssue publicar(Diagnostico d, ContextoEjecucion contexto, Path captura, boolean dryRun) {
        String huella = Huella.de(d);
        String titulo = ContenidoIssue.titulo(d);
        List<String> etiquetas = new ArrayList<>(ContenidoIssue.etiquetas(d, "-"));
        etiquetas.add(ContenidoIssue.etiquetaHuella(huella));

        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("project", Map.of("key", ClienteHttp.vacio(proyecto) ? "<CLAVE_PROYECTO>" : proyecto));
        campos.put("issuetype", Map.of("name", tipoIssue));
        campos.put("summary", titulo.length() > 255 ? titulo.substring(0, 252) + "..." : titulo);
        campos.put("labels", etiquetas);
        campos.put("description", descripcion(d, contexto));
        String payload = ClienteHttp.json(Map.of("fields", campos));
        String vista = vistaPrevia(titulo, etiquetas, d, contexto)
                + (captura == null ? "" : "\n**Adjunto:** `" + captura.getFileName() + "` (captura al fallar)\n");

        List<String> faltantes = faltantes();
        if (dryRun || !faltantes.isEmpty()) {
            String motivo = dryRun ? "Modo dry-run: no se envió nada a Jira"
                    : "Jira no está configurado (falta " + String.join(", ", faltantes) + "): se generó solo la vista previa";
            return new ResultadoIssue(ResultadoIssue.Accion.SIMULADO, huella, titulo, null, motivo, payload, vista);
        }

        ClienteHttp http = ClienteHttp.basic(email, token);
        try {
            String existente = buscarAbierto(http, huella);
            if (existente != null) {
                String comentario = ClienteHttp.json(Map.of("body", adf(List.of(
                        parrafo(texto(ContenidoIssue.comentarioRepeticion(d, contexto)), enlaceEjecucion(contexto))))));
                http.enviar("POST", baseUrl + "/rest/api/3/issue/" + existente + "/comment", comentario, "application/json");
                return new ResultadoIssue(ResultadoIssue.Accion.COMENTADO, huella, titulo, baseUrl + "/browse/" + existente,
                        "El fallo ya tenía un issue abierto: se agregó un comentario", comentario, vista);
            }
            JsonNode creado = http.enviar("POST", baseUrl + "/rest/api/3/issue", payload, "application/json");
            String clave = creado.path("key").asText();
            String adjunto = "";
            if (captura != null) {
                adjuntar(http, clave, captura);
                adjunto = " con la captura adjunta";
            }
            return new ResultadoIssue(ResultadoIssue.Accion.CREADO, huella, titulo, baseUrl + "/browse/" + clave,
                    "Issue " + clave + " creado" + adjunto, payload, vista);
        } catch (IOException | RuntimeException e) {
            return new ResultadoIssue(ResultadoIssue.Accion.ERROR, huella, titulo, null, e.getMessage(), payload, vista);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ResultadoIssue(ResultadoIssue.Accion.ERROR, huella, titulo, null, "Interrumpido", payload, vista);
        }
    }

    /**
     * Adjunta la captura al issue (multipart, con la cabecera que exige Jira para subir archivos).
     */
    private void adjuntar(ClienteHttp http, String clave, Path captura) throws IOException, InterruptedException {
        String boundary = "triage-" + UUID.randomUUID();
        byte[] cuerpo = ClienteHttp.multipart(boundary, "file", captura.getFileName().toString(), "image/png",
                Files.readAllBytes(captura));
        http.enviarBytes(baseUrl + "/rest/api/3/issue/" + clave + "/attachments", cuerpo,
                "multipart/form-data; boundary=" + boundary, "X-Atlassian-Token", "no-check");
    }

    @Override
    public ResultadoIssue publicarTarea(TareaReparacion tarea, ContextoEjecucion contexto, boolean dryRun) {
        List<String> etiquetas = List.of("self-healing", "mantenimiento-test", "area-qa",
                ContenidoIssue.etiquetaHuella(tarea.huella()));
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("project", Map.of("key", ClienteHttp.vacio(proyecto) ? "<CLAVE_PROYECTO>" : proyecto));
        campos.put("issuetype", Map.of("name", tipoTarea));
        campos.put("summary", tarea.titulo());
        campos.put("labels", etiquetas);
        campos.put("description", descripcionTarea(tarea, contexto));
        String payload = ClienteHttp.json(Map.of("fields", campos));
        String vista = "# " + tarea.titulo() + "\n\n**Jira:** issue tipo `" + tipoTarea + "` · etiquetas: "
                + String.join(", ", etiquetas) + "\n\n" + TareaReparacion.AVISO + "\n\n"
                + "- Locator anterior: `" + tarea.locatorOriginal() + "`\n- Locator nuevo: `" + tarea.locatorNuevo() + "`\n"
                + "- Confianza de la IA: " + tarea.confianza() + "%\n- Razón: " + tarea.razon() + "\n"
                + "- PR con la corrección: " + (tarea.prUrl() == null ? "—" : tarea.prUrl()) + "\n\n" + TareaReparacion.RECOMENDACION + "\n";

        List<String> faltantes = faltantes();
        if (dryRun || !faltantes.isEmpty()) {
            String motivo = dryRun ? "Modo dry-run: no se envió nada a Jira"
                    : "Jira no está configurado (falta " + String.join(", ", faltantes) + "): se generó solo la vista previa";
            return new ResultadoIssue(ResultadoIssue.Accion.SIMULADO, tarea.huella(), tarea.titulo(), null, motivo, payload, vista);
        }
        ClienteHttp http = ClienteHttp.basic(email, token);
        try {
            String existente = buscarAbierto(http, tarea.huella());
            if (existente != null) {
                String comentario = ClienteHttp.json(Map.of("body", adf(List.of(parrafo(texto(
                        "🔁 El self-healing volvió a reparar este locator (" + contexto.origen() + ", rama " + contexto.rama()
                                + "). La corrección sigue pendiente de aprobación."), enlaceEjecucion(contexto))))));
                http.enviar("POST", baseUrl + "/rest/api/3/issue/" + existente + "/comment", comentario, "application/json");
                return new ResultadoIssue(ResultadoIssue.Accion.COMENTADO, tarea.huella(), tarea.titulo(),
                        baseUrl + "/browse/" + existente, "La tarea ya estaba abierta: se agregó un comentario", comentario, vista);
            }
            JsonNode creado = http.enviar("POST", baseUrl + "/rest/api/3/issue", payload, "application/json");
            String clave = creado.path("key").asText();
            return new ResultadoIssue(ResultadoIssue.Accion.CREADO, tarea.huella(), tarea.titulo(), baseUrl + "/browse/" + clave,
                    "Tarea " + clave + " creada", payload, vista);
        } catch (IOException | RuntimeException e) {
            return new ResultadoIssue(ResultadoIssue.Accion.ERROR, tarea.huella(), tarea.titulo(), null, e.getMessage(), payload, vista);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ResultadoIssue(ResultadoIssue.Accion.ERROR, tarea.huella(), tarea.titulo(), null, "Interrumpido", payload, vista);
        }
    }

    private static Map<String, Object> descripcionTarea(TareaReparacion t, ContextoEjecucion c) {
        List<Object> bloques = new ArrayList<>();
        bloques.add(panel("info", parrafo(texto(TareaReparacion.AVISO))));
        bloques.add(titulo("Cambio detectado"));
        bloques.add(lista(List.of(
                "Elemento: " + t.descripcion(),
                "Page Object: " + t.pagina(),
                "Locator anterior: " + t.locatorOriginal(),
                "Locator nuevo: " + t.locatorNuevo(),
                "Confianza de la IA: " + t.confianza() + "%",
                "Ambiente: " + t.ambiente())));
        bloques.add(titulo("Razón de la IA"));
        bloques.add(parrafo(texto(t.razon())));
        bloques.add(titulo("Qué hacer"));
        bloques.add(lista(List.of(
                "Revisar y aprobar el PR con la corrección" + (t.prUrl() == null ? "" : ": " + t.prUrl()),
                TareaReparacion.RECOMENDACION)));
        bloques.add(parrafo(enlaceEjecucion(c)));
        return adf(bloques);
    }

    private List<String> faltantes() {
        List<String> faltantes = new ArrayList<>();
        if (ClienteHttp.vacio(baseUrl)) {
            faltantes.add("issues.jiraBaseUrl");
        }
        if (ClienteHttp.vacio(proyecto)) {
            faltantes.add("issues.jiraProjectKey");
        }
        if (ClienteHttp.vacio(email)) {
            faltantes.add("JIRA_EMAIL");
        }
        if (ClienteHttp.vacio(token)) {
            faltantes.add("JIRA_API_TOKEN");
        }
        return faltantes;
    }

    private String buscarAbierto(ClienteHttp http, String huella) throws IOException, InterruptedException {
        String jql = "project = \"" + proyecto + "\" AND labels = \"" + ContenidoIssue.etiquetaHuella(huella)
                + "\" AND statusCategory != Done";
        JsonNode resultado = http.enviar("POST", baseUrl + "/rest/api/3/search/jql",
                ClienteHttp.json(Map.of("jql", jql, "fields", List.of("key"), "maxResults", 1)), "application/json");
        JsonNode issues = resultado.path("issues");
        return issues.isArray() && !issues.isEmpty() ? issues.get(0).path("key").asText() : null;
    }

    // ---- ADF ----

    private static Map<String, Object> descripcion(Diagnostico d, ContextoEjecucion c) {
        List<Object> bloques = new ArrayList<>();
        bloques.add(panel("info", parrafo(texto(ContenidoIssue.AVISO_IA))));
        if (d.simulado()) {
            bloques.add(panel("warning", parrafo(texto(ContenidoIssue.AVISO_DEMO))));
        }
        bloques.add(parrafo(textoNegrita(ContenidoIssue.resumen(d))));
        bloques.add(titulo("Causa probable"));
        bloques.add(parrafo(texto(d.causaProbable())));
        bloques.add(titulo("Evidencias"));
        bloques.add(lista(d.evidencias()));
        bloques.add(titulo("Acción recomendada"));
        bloques.add(parrafo(texto(d.accionRecomendada())));
        bloques.add(titulo("Contexto"));
        List<String> filas = new ArrayList<>();
        ContenidoIssue.contexto(d, c).forEach((clave, valor) -> filas.add(clave + ": " + valor));
        bloques.add(lista(filas));
        bloques.add(parrafo(enlaceEjecucion(c)));
        return adf(bloques);
    }

    private static Map<String, Object> adf(List<Object> contenido) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", "doc");
        doc.put("version", 1);
        doc.put("content", contenido);
        return doc;
    }

    private static Map<String, Object> titulo(String texto) {
        return Map.of("type", "heading", "attrs", Map.of("level", 3), "content", List.of(texto(texto)));
    }

    @SafeVarargs
    private static Map<String, Object> parrafo(Map<String, Object>... nodos) {
        return Map.of("type", "paragraph", "content", List.of(nodos));
    }

    private static Map<String, Object> panel(String tipo, Map<String, Object> contenido) {
        return Map.of("type", "panel", "attrs", Map.of("panelType", tipo), "content", List.of(contenido));
    }

    private static Map<String, Object> lista(List<String> items) {
        List<Object> elementos = new ArrayList<>();
        items.forEach(item -> elementos.add(Map.of("type", "listItem", "content", List.of(parrafo(texto(item))))));
        return Map.of("type", "bulletList", "content", elementos);
    }

    private static Map<String, Object> texto(String valor) {
        return Map.of("type", "text", "text", valor == null || valor.isEmpty() ? "—" : valor);
    }

    private static Map<String, Object> textoNegrita(String valor) {
        return Map.of("type", "text", "text", valor, "marks", List.of(Map.of("type", "strong")));
    }

    private static Map<String, Object> enlaceEjecucion(ContextoEjecucion c) {
        if (c.urlEjecucion() == null) {
            return texto("Ejecución: local");
        }
        return Map.of("type", "text", "text", " Ver captura y reportes de la ejecución",
                "marks", List.of(Map.of("type", "link", "attrs", Map.of("href", c.urlEjecucion()))));
    }

    private String vistaPrevia(String titulo, List<String> etiquetas, Diagnostico d, ContextoEjecucion c) {
        StringBuilder md = new StringBuilder("# ").append(titulo).append("\n\n")
                .append("**Jira:** issue tipo `").append(tipoIssue).append("` · etiquetas: ")
                .append(String.join(", ", etiquetas)).append("\n\n")
                .append("> ").append(ContenidoIssue.AVISO_IA).append("\n");
        if (d.simulado()) {
            md.append(">\n> ").append(ContenidoIssue.AVISO_DEMO).append("\n");
        }
        md.append("\n**").append(ContenidoIssue.resumen(d)).append("**\n\n")
                .append("### Causa probable\n").append(d.causaProbable()).append("\n\n### Evidencias\n");
        d.evidencias().forEach(e -> md.append("- ").append(e).append("\n"));
        md.append("\n### Acción recomendada\n").append(d.accionRecomendada()).append("\n\n### Contexto\n");
        ContenidoIssue.contexto(d, c).forEach((clave, valor) -> md.append("- ").append(clave).append(": ").append(valor).append("\n"));
        md.append("- Ejecución: ").append(c.urlEjecucion() == null ? "local" : c.urlEjecucion()).append("\n");
        return md.toString();
    }
}
