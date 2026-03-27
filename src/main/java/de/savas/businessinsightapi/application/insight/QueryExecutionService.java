package de.savas.businessinsightapi.application.insight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.savas.businessinsightapi.common.error.BadRequestException;
import de.savas.businessinsightapi.domain.insight.dataset.Dataset;
import de.savas.businessinsightapi.domain.insight.datasource.DataSource;
import de.savas.businessinsightapi.domain.insight.datasource.DataSourceRepository;
import de.savas.businessinsightapi.domain.insight.query.QueryTemplate;
import de.savas.businessinsightapi.domain.insight.query.QueryTemplateRepository;
import de.savas.businessinsightapi.domain.insight.run.QueryRun;
import de.savas.businessinsightapi.domain.insight.run.QueryRunRepository;
import de.savas.businessinsightapi.domain.insight.run.QueryRunStatus;
import de.savas.businessinsightapi.domain.insight.snapshot.DatasetSnapshot;
import de.savas.businessinsightapi.domain.insight.snapshot.DatasetSnapshotRepository;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class QueryExecutionService {

    private final DataSourceRepository dataSourceRepo;
    private final QueryTemplateRepository queryTemplateRepo;
    private final QueryRunRepository queryRunRepo;
    private final DatasetSnapshotRepository snapshotRepo;
    private final JdbcConnectionProvider jdbcConnectionProvider;
    private final InsightExecutionProperties properties;
    private final InsightSqlValidator sqlValidator;
    private final ObjectMapper objectMapper;

    public QueryExecutionService(
            DataSourceRepository dataSourceRepo,
            QueryTemplateRepository queryTemplateRepo,
            QueryRunRepository queryRunRepo,
            DatasetSnapshotRepository snapshotRepo,
            JdbcConnectionProvider jdbcConnectionProvider,
            InsightExecutionProperties properties,
            InsightSqlValidator sqlValidator,
            ObjectMapper objectMapper
    ) {
        this.dataSourceRepo = dataSourceRepo;
        this.queryTemplateRepo = queryTemplateRepo;
        this.queryRunRepo = queryRunRepo;
        this.snapshotRepo = snapshotRepo;
        this.jdbcConnectionProvider = jdbcConnectionProvider;
        this.properties = properties;
        this.sqlValidator = sqlValidator;
        this.objectMapper = objectMapper;
    }

    public ExecutionResult execute(Long orgId, Long queryTemplateId, Map<String, Object> params, boolean snapshot) {
        return executeInternal(orgId, queryTemplateId, params, snapshot, null);
    }

    public ExecutionResult executeForDataset(Long orgId, Dataset dataset, Map<String, Object> params) {
        Map<String, Object> mergedParams = new LinkedHashMap<>(defaultParamsAsMap(dataset));
        if (params != null) {
            mergedParams.putAll(params);
        }
        return executeInternal(orgId, dataset.getQueryTemplateId(), mergedParams, true, dataset.getId());
    }

    private ExecutionResult executeInternal(Long orgId,
                                            Long queryTemplateId,
                                            Map<String, Object> params,
                                            boolean snapshot,
                                            Long datasetId) {
        LoadedExecutionContext context = loadContext(orgId, queryTemplateId);
        ValidationResult validation = validateInternal(context.queryTemplate(), context.dataSource(), params);
        if (!validation.valid()) {
            throw new BadRequestException(String.join("; ", validation.errors()));
        }

        String requestedBy = currentUsername();
        QueryRun run = queryRunRepo.save(new QueryRun(orgId, queryTemplateId, context.dataSource().getId(), requestedBy));

        Instant start = Instant.now();
        try {
            String sql = sqlValidator.applyLimitIfMissing(context.queryTemplate().getSqlText(), properties.maxRows(), context.dataSource().getType());
            JdbcTemplate jdbcTemplate = jdbcConnectionProvider.templateFor(context.dataSource());
            NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
            MapSqlParameterSource parameterSource = buildSqlParameters(context.queryTemplate(), validation.allowedParams(), params);

            List<Map<String, Object>> rows = namedJdbcTemplate.query(sql, parameterSource, new ColumnMapRowMapper());

            long durationMs = Duration.between(start, Instant.now()).toMillis();
            run.markSuccess(rows.size(), durationMs);
            queryRunRepo.save(run);

            DatasetSnapshot savedSnapshot = null;
            if (snapshot) {
                JsonNode json = toJson(rows.stream().limit(properties.snapshotRows()).toList());
                savedSnapshot = datasetId == null
                        ? snapshotRepo.save(new DatasetSnapshot(orgId, run.getId(), json))
                        : snapshotRepo.save(new DatasetSnapshot(orgId, run.getId(), datasetId, json));
            }

            return new ExecutionResult(
                    run.getId(),
                    run.getStatus(),
                    rows.size(),
                    durationMs,
                    requestedBy,
                    context.dataSource().getId(),
                    queryTemplateId,
                    savedSnapshot == null ? null : savedSnapshot.getId()
            );
        } catch (Exception ex) {
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            run.markFailed(safeMessage(ex), durationMs);
            queryRunRepo.save(run);
            throw new BadRequestException("Query execution failed: " + safeMessage(ex));
        }
    }

    public ValidationResult validate(Long orgId, Long queryTemplateId, Map<String, Object> params) {
        LoadedExecutionContext context = loadContext(orgId, queryTemplateId);
        return validateInternal(context.queryTemplate(), context.dataSource(), params);
    }

    private LoadedExecutionContext loadContext(Long orgId, Long queryTemplateId) {
        QueryTemplate queryTemplate = queryTemplateRepo.findById(queryTemplateId)
                .orElseThrow(() -> new BadRequestException("QueryTemplate not found"));

        if (!queryTemplate.getOrganizationId().equals(orgId)) {
            throw new BadRequestException("QueryTemplate not found");
        }

        DataSource dataSource = dataSourceRepo.findById(queryTemplate.getDataSourceId())
                .orElseThrow(() -> new BadRequestException("DataSource not found"));

        if (!dataSource.getOrganizationId().equals(orgId)) {
            throw new BadRequestException("DataSource not found");
        }
        if (!dataSource.isActive()) {
            throw new BadRequestException("DataSource is inactive");
        }
        if (!queryTemplate.isActive()) {
            throw new BadRequestException("QueryTemplate is inactive");
        }
        if (!queryTemplate.getDataSourceId().equals(dataSource.getId())) {
            throw new BadRequestException("QueryTemplate does not belong to the requested DataSource");
        }

        return new LoadedExecutionContext(queryTemplate, dataSource);
    }

    private ValidationResult validateInternal(QueryTemplate queryTemplate, DataSource dataSource, Map<String, Object> params) {
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        InsightSqlValidator.ValidationResult sqlValidation = sqlValidator.validateSql(queryTemplate.getSqlText(), dataSource.getType());
        Set<String> detectedParams = sqlValidation.detectedParams();
        Map<String, ParameterDefinition> definitions = parameterDefinitions(queryTemplate);
        Set<String> allowedParams = allowedParams(definitions, detectedParams);
        Set<String> requiredParams = requiredParams(definitions, detectedParams);

        Set<String> errors = new LinkedHashSet<>(sqlValidation.errors());

        for (String paramKey : safeParams.keySet()) {
            if (!allowedParams.contains(paramKey)) {
                errors.add("Unexpected parameter: " + paramKey);
            }
        }
        for (String requiredParam : requiredParams) {
            if (!safeParams.containsKey(requiredParam) || safeParams.get(requiredParam) == null) {
                errors.add("Missing required parameter: " + requiredParam);
            }
        }

        return new ValidationResult(errors.isEmpty(), List.copyOf(errors), List.copyOf(detectedParams), List.copyOf(allowedParams));
    }

    private Map<String, ParameterDefinition> parameterDefinitions(QueryTemplate queryTemplate) {
        Map<String, ParameterDefinition> definitions = new LinkedHashMap<>();
        JsonNode parameters = queryTemplate.getParameters();
        if (parameters == null || !parameters.isArray()) {
            return definitions;
        }
        for (JsonNode parameter : parameters) {
            String name = text(parameter, "name");
            if (name == null) {
                continue;
            }
            definitions.put(name, new ParameterDefinition(
                    name,
                    text(parameter, "type"),
                    parameter.path("required").asBoolean(false)
            ));
        }
        return definitions;
    }

    private Set<String> allowedParams(Map<String, ParameterDefinition> definitions, Set<String> detectedParams) {
        return definitions.isEmpty() ? new LinkedHashSet<>(detectedParams) : new LinkedHashSet<>(definitions.keySet());
    }

    private Set<String> requiredParams(Map<String, ParameterDefinition> definitions, Set<String> detectedParams) {
        Set<String> required = new LinkedHashSet<>();
        if (definitions.isEmpty()) {
            required.addAll(detectedParams);
            return required;
        }
        for (ParameterDefinition definition : definitions.values()) {
            if (definition.required()) {
                required.add(definition.name());
            }
        }
        return required;
    }

    private MapSqlParameterSource buildSqlParameters(QueryTemplate queryTemplate, List<String> allowedParams, Map<String, Object> params) {
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        Map<String, ParameterDefinition> definitions = parameterDefinitions(queryTemplate);
        MapSqlParameterSource source = new MapSqlParameterSource();
        for (String paramName : allowedParams) {
            if (!safeParams.containsKey(paramName)) {
                continue;
            }
            ParameterDefinition definition = definitions.get(paramName);
            Object convertedValue = convertValue(paramName, safeParams.get(paramName), definition == null ? null : definition.type());
            source.addValue(paramName, convertedValue);
        }
        return source;
    }

    private Object convertValue(String paramName, Object rawValue, String type) {
        if (rawValue == null || type == null || type.isBlank()) {
            return rawValue;
        }
        String normalizedType = type.toLowerCase(Locale.ROOT);
        try {
            return switch (normalizedType) {
                case "string" -> rawValue.toString();
                case "number", "decimal", "bigdecimal" -> rawValue instanceof Number ? rawValue : new BigDecimal(rawValue.toString());
                case "integer", "int", "long" -> rawValue instanceof Number ? ((Number) rawValue).longValue() : Long.parseLong(rawValue.toString());
                case "boolean", "bool" -> convertBoolean(rawValue);
                case "date", "localdate" -> rawValue instanceof LocalDate ? rawValue : LocalDate.parse(rawValue.toString());
                case "datetime", "timestamp", "localdatetime" -> convertDateTime(rawValue);
                case "offsetdatetime" -> rawValue instanceof OffsetDateTime ? rawValue : OffsetDateTime.parse(rawValue.toString());
                default -> rawValue;
            };
        } catch (Exception ex) {
            throw new BadRequestException("Invalid value for parameter %s of type %s".formatted(paramName, type));
        }
    }

    private Object convertDateTime(Object rawValue) {
        if (rawValue instanceof LocalDateTime || rawValue instanceof OffsetDateTime) {
            return rawValue;
        }
        String text = rawValue.toString();
        if (text.endsWith("Z") || text.contains("+")) {
            return OffsetDateTime.parse(text);
        }
        return LocalDateTime.parse(text);
    }

    private Boolean convertBoolean(Object rawValue) {
        if (rawValue instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String value = rawValue.toString().trim().toLowerCase(Locale.ROOT);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean value");
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Map<String, Object> defaultParamsAsMap(Dataset dataset) {
        JsonNode defaultParams = dataset.getDefaultParams();
        if (defaultParams == null || defaultParams.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(defaultParams, Map.class);
    }

    private JsonNode toJson(List<Map<String, Object>> rows) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (Map<String, Object> row : rows) {
            ObjectNode obj = objectMapper.createObjectNode();
            row.forEach((k, v) -> obj.set(k, objectMapper.valueToTree(v)));
            arr.add(obj);
        }
        return arr;
    }

    private String safeMessage(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null) return "unknown_error";
        // Avoid leaking credentials
        return msg.length() > 600 ? msg.substring(0, 600) : msg;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private record LoadedExecutionContext(QueryTemplate queryTemplate, DataSource dataSource) {
    }

    private record ParameterDefinition(String name, String type, boolean required) {
    }

    public record ExecutionResult(
            Long queryRunId,
            QueryRunStatus status,
            int rowCount,
            long durationMs,
            String requestedBy,
            Long dataSourceId,
            Long queryTemplateId,
            Long snapshotId
    ) {
    }

    public record ValidationResult(
            boolean valid,
            List<String> errors,
            List<String> detectedParams,
            List<String> allowedParams
    ) {
    }
}
