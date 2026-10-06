package cl.guzman.automation.issues;

import cl.guzman.automation.config.EnvironmentConfig;
import cl.guzman.automation.triage.Diagnostico;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Publica diagnósticos como work items de Azure DevOps (API REST 7.1). Crea un Bug con los pasos de reproducción
 * en HTML, la severidad en la escala de Azure y los tags (incluida la huella para no duplicar).
 *
 * <p>Requiere: issues.azureOrgUrl, issues.azureProject y AZURE_DEVOPS_PAT (permiso Work Items: lectura y escritura).
 * Sin esos datos solo genera la vista previa.
 */
public class AzureDevOpsIssueTracker implements IssueTracker {

    private static final String API_VERSION = "api-version=7.1";

    private final String organizacion;
    private final String proyecto;
    private final String tipoWorkItem;
    private final String tipoTarea;
    private final String pat;

    public AzureDevOpsIssueTracker(EnvironmentConfig.Issues cfg, EnvironmentConfig.Secrets secrets) {
        this.organizacion = cfg.azureOrgUrl() == null ? "" : cfg.azureOrgUrl().replaceAll("/+$", "");
        this.proyecto = cfg.azureProject();
        this.tipoWorkItem = ClienteHttp.vacio(cfg.azureWorkItemType()) ? "Bug" : cfg.azureWorkItemType();
        this.tipoTarea = ClienteHttp.vacio(cfg.azureTaskType()) ? "Task" : cfg.azureTaskType();
        this.pat = secrets.azureDevOpsPat();
    }

    @Override
    public String nombre() {
        return "Azure DevOps (" + (ClienteHttp.vacio(organizacion) ? "sin configurar" : organizacion + "/" + proyecto) + ")";
    }

    @Override
    public ResultadoIssue publicar(Diagnostico d, ContextoEjecucion contexto, Path captura, boolean dryRun) {
        String huella = Huella.de(d);
        String titulo = ContenidoIssue.titulo(d);
        List<String> tags = new ArrayList<>(ContenidoIssue.etiquetas(d, "-"));
        tags.add(ContenidoIssue.etiquetaHuella(huella));
        String reproSteps = html(d, contexto);

        // JSON Patch: así crea work items la API de Azure DevOps
        List<Map<String, Object>> operaciones = new ArrayList<>(List.of(
                campo("System.Title", titulo.length() > 255 ? titulo.substring(0, 252) + "..." : titulo),
                campo("Microsoft.VSTS.TCM.ReproSteps", reproSteps),
                campo("Microsoft.VSTS.Common.Severity", severidad(d.severidad())),
                campo("System.Tags", String.join("; ", tags))));
        if (captura != null) {
            // En dry-run se muestra dónde irá el adjunto; al publicar se reemplaza por la URL real del archivo subido
            operaciones.add(adjunto("<URL del adjunto subido: " + captura.getFileName() + ">"));
        }
        String payload = ClienteHttp.json(operaciones);
        String vista = "# " + titulo + "\n\n**Azure DevOps:** work item tipo `" + tipoWorkItem + "` · severidad `"
                + severidad(d.severidad()) + "` · tags: " + String.join("; ", tags)
                + "\n\n**Repro Steps (HTML):**\n\n" + reproSteps + "\n";

        List<String> faltantes = faltantes();
        if (dryRun || !faltantes.isEmpty()) {
            String motivo = dryRun ? "Modo dry-run: no se envió nada a Azure DevOps"
                    : "Azure DevOps no está configurado (falta " + String.join(", ", faltantes) + "): se generó solo la vista previa";
            return new ResultadoIssue(ResultadoIssue.Accion.SIMULADO, huella, titulo, null, motivo, payload, vista);
        }

        ClienteHttp http = ClienteHttp.basic("", pat);
        String base = organizacion + "/" + codificar(proyecto);
        try {
            Integer existente = buscarAbierto(http, base, huella);
            if (existente != null) {
                String comentario = ClienteHttp.json(Map.of("text", "<p>" + escapar(ContenidoIssue.comentarioRepeticion(d, contexto)) + "</p>"
                        + (contexto.urlEjecucion() == null ? "" : "<p><a href=\"" + contexto.urlEjecucion() + "\">Ver ejecución</a></p>")));
                http.enviar("POST", base + "/_apis/wit/workItems/" + existente + "/comments?api-version=7.1-preview.4",
                        comentario, "application/json");
                return new ResultadoIssue(ResultadoIssue.Accion.COMENTADO, huella, titulo, base + "/_workitems/edit/" + existente,
                        "El fallo ya tenía un work item abierto: se agregó un comentario", comentario, vista);
            }
            if (captura != null) {
                JsonNode subido = http.enviarBytes(base + "/_apis/wit/attachments?fileName=" + codificar(captura.getFileName().toString())
                        + "&" + API_VERSION, Files.readAllBytes(captura), "application/octet-stream");
                operaciones.set(operaciones.size() - 1, adjunto(subido.path("url").asText()));
                payload = ClienteHttp.json(operaciones);
            }
            JsonNode creado = http.enviar("POST", base + "/_apis/wit/workitems/$" + codificar(tipoWorkItem) + "?" + API_VERSION,
                    payload, "application/json-patch+json");
            int id = creado.path("id").asInt();
            return new ResultadoIssue(ResultadoIssue.Accion.CREADO, huella, titulo, base + "/_workitems/edit/" + id,
                    "Work item #" + id + " creado", payload, vista);
        } catch (IOException | RuntimeException e) {
            return new ResultadoIssue(ResultadoIssue.Accion.ERROR, huella, titulo, null, e.getMessage(), payload, vista);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ResultadoIssue(ResultadoIssue.Accion.ERROR, huella, titulo, null, "Interrumpido", payload, vista);
        }
    }

    @Override
    public ResultadoIssue publicarTarea(TareaReparacion tarea, ContextoEjecucion contexto, boolean dryRun) {
        List<String> tags = List.of("self-healing", "mantenimiento-test", "area-qa", ContenidoIssue.etiquetaHuella(tarea.huella()));
        String descripcion = htmlTarea(tarea, contexto);
        List<Map<String, Object>> operaciones = List.of(
                campo("System.Title", tarea.titulo()),
                campo("System.Description", descripcion),
                campo("System.Tags", String.join("; ", tags)));
        String payload = ClienteHttp.json(operaciones);
        String vista = "# " + tarea.titulo() + "\n\n**Azure DevOps:** work item tipo `" + tipoTarea + "` · tags: "
                + String.join("; ", tags) + "\n\n**Description (HTML):**\n\n" + descripcion + "\n";

        List<String> faltantes = faltantes();
        if (dryRun || !faltantes.isEmpty()) {
            String motivo = dryRun ? "Modo dry-run: no se envió nada a Azure DevOps"
                    : "Azure DevOps no está configurado (falta " + String.join(", ", faltantes) + "): se generó solo la vista previa";
            return new ResultadoIssue(ResultadoIssue.Accion.SIMULADO, tarea.huella(), tarea.titulo(), null, motivo, payload, vista);
        }
        ClienteHttp http = ClienteHttp.basic("", pat);
        String base = organizacion + "/" + codificar(proyecto);
        try {
            Integer existente = buscarAbierto(http, base, tarea.huella());
            if (existente != null) {
                String comentario = ClienteHttp.json(Map.of("text", "<p>🔁 El self-healing volvió a reparar este locator ("
                        + escapar(contexto.origen()) + ", rama " + escapar(contexto.rama()) + "). La corrección sigue pendiente de aprobación.</p>"));
                http.enviar("POST", base + "/_apis/wit/workItems/" + existente + "/comments?api-version=7.1-preview.4",
                        comentario, "application/json");
                return new ResultadoIssue(ResultadoIssue.Accion.COMENTADO, tarea.huella(), tarea.titulo(),
                        base + "/_workitems/edit/" + existente, "La tarea ya estaba abierta: se agregó un comentario", comentario, vista);
            }
            JsonNode creado = http.enviar("POST", base + "/_apis/wit/workitems/$" + codificar(tipoTarea) + "?" + API_VERSION,
                    payload, "application/json-patch+json");
            int id = creado.path("id").asInt();
            return new ResultadoIssue(ResultadoIssue.Accion.CREADO, tarea.huella(), tarea.titulo(), base + "/_workitems/edit/" + id,
                    "Tarea #" + id + " creada", payload, vista);
        } catch (IOException | RuntimeException e) {
            return new ResultadoIssue(ResultadoIssue.Accion.ERROR, tarea.huella(), tarea.titulo(), null, e.getMessage(), payload, vista);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ResultadoIssue(ResultadoIssue.Accion.ERROR, tarea.huella(), tarea.titulo(), null, "Interrumpido", payload, vista);
        }
    }

    private static String htmlTarea(TareaReparacion t, ContextoEjecucion c) {
        return "<p><i>" + escapar(TareaReparacion.AVISO) + "</i></p>"
                + "<h3>Cambio detectado</h3><table>"
                + "<tr><td><b>Elemento</b></td><td>" + escapar(t.descripcion()) + "</td></tr>"
                + "<tr><td><b>Page Object</b></td><td>" + escapar(t.pagina()) + "</td></tr>"
                + "<tr><td><b>Locator anterior</b></td><td><code>" + escapar(t.locatorOriginal()) + "</code></td></tr>"
                + "<tr><td><b>Locator nuevo</b></td><td><code>" + escapar(t.locatorNuevo()) + "</code></td></tr>"
                + "<tr><td><b>Confianza de la IA</b></td><td>" + t.confianza() + "%</td></tr>"
                + "<tr><td><b>Ambiente</b></td><td>" + escapar(t.ambiente()) + "</td></tr></table>"
                + "<h3>Razón de la IA</h3><p>" + escapar(t.razon()) + "</p>"
                + "<h3>Qué hacer</h3><ul><li>Revisar y aprobar el PR con la corrección"
                + (t.prUrl() == null ? "" : ": <a href=\"" + t.prUrl() + "\">" + escapar(t.prUrl()) + "</a>") + "</li>"
                + "<li>" + escapar(TareaReparacion.RECOMENDACION) + "</li></ul>"
                + (c.urlEjecucion() == null ? "" : "<p><a href=\"" + c.urlEjecucion() + "\">Ver la ejecución</a></p>");
    }

    private List<String> faltantes() {
        List<String> faltantes = new ArrayList<>();
        if (ClienteHttp.vacio(organizacion)) {
            faltantes.add("issues.azureOrgUrl");
        }
        if (ClienteHttp.vacio(proyecto)) {
            faltantes.add("issues.azureProject");
        }
        if (ClienteHttp.vacio(pat)) {
            faltantes.add("AZURE_DEVOPS_PAT");
        }
        return faltantes;
    }

    private static Integer buscarAbierto(ClienteHttp http, String base, String huella) throws IOException, InterruptedException {
        String wiql = "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project"
                + " AND [System.Tags] CONTAINS '" + ContenidoIssue.etiquetaHuella(huella) + "'"
                + " AND [System.State] NOT IN ('Closed', 'Done', 'Removed', 'Resolved')";
        JsonNode resultado = http.enviar("POST", base + "/_apis/wit/wiql?" + API_VERSION,
                ClienteHttp.json(Map.of("query", wiql)), "application/json");
        JsonNode items = resultado.path("workItems");
        return items.isArray() && !items.isEmpty() ? items.get(0).path("id").asInt() : null;
    }

    /**
     * Relación que vincula un archivo subido como adjunto del work item.
     */
    private static Map<String, Object> adjunto(String url) {
        return Map.of("op", "add", "path", "/relations/-",
                "value", Map.of("rel", "AttachedFile", "url", url, "attributes", Map.of("comment", "Captura al fallar")));
    }

    private static Map<String, Object> campo(String nombre, Object valor) {
        return Map.of("op", "add", "path", "/fields/" + nombre, "value", valor);
    }

    /**
     * Escala de severidad de Azure DevOps para Bugs.
     */
    private static String severidad(Diagnostico.Severidad severidad) {
        return switch (severidad) {
            case ALTA -> "2 - High";
            case MEDIA -> "3 - Medium";
            case BAJA -> "4 - Low";
        };
    }

    private static String html(Diagnostico d, ContextoEjecucion c) {
        StringBuilder html = new StringBuilder()
                .append("<p><i>").append(escapar(ContenidoIssue.AVISO_IA)).append("</i></p>");
        if (d.simulado()) {
            html.append("<p><b>").append(escapar(ContenidoIssue.AVISO_DEMO)).append("</b></p>");
        }
        html.append("<p><b>").append(escapar(ContenidoIssue.resumen(d))).append("</b></p>")
                .append("<h3>Causa probable</h3><p>").append(escapar(d.causaProbable())).append("</p>")
                .append("<h3>Evidencias</h3><ul>");
        d.evidencias().forEach(e -> html.append("<li>").append(escapar(e)).append("</li>"));
        html.append("</ul><h3>Acción recomendada</h3><p>").append(escapar(d.accionRecomendada())).append("</p>")
                .append("<h3>Contexto</h3><table>");
        ContenidoIssue.contexto(d, c).forEach((clave, valor) -> html.append("<tr><td><b>").append(escapar(clave))
                .append("</b></td><td>").append(escapar(valor)).append("</td></tr>"));
        html.append("<tr><td><b>Ejecución</b></td><td>")
                .append(c.urlEjecucion() == null ? "local" : "<a href=\"" + c.urlEjecucion() + "\">Ver captura y reportes</a>")
                .append("</td></tr></table>");
        return html.toString();
    }

    private static String escapar(String texto) {
        return texto == null ? "" : texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
