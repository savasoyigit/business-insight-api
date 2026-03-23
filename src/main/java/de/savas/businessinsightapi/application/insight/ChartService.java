package de.savas.businessinsightapi.application.insight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.savas.businessinsightapi.common.error.BadRequestException;
import de.savas.businessinsightapi.domain.insight.chart.Chart;
import de.savas.businessinsightapi.domain.insight.chart.ChartRepository;
import de.savas.businessinsightapi.domain.insight.chart.ChartType;
import de.savas.businessinsightapi.domain.insight.dataset.Dataset;
import de.savas.businessinsightapi.domain.insight.dataset.DatasetRepository;
import de.savas.businessinsightapi.domain.insight.snapshot.DatasetSnapshot;
import de.savas.businessinsightapi.domain.insight.snapshot.DatasetSnapshotRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ChartService {

    private final ChartRepository chartRepo;
    private final DatasetRepository datasetRepo;
    private final DatasetSnapshotRepository snapshotRepo;

    public ChartService(ChartRepository chartRepo, DatasetRepository datasetRepo, DatasetSnapshotRepository snapshotRepo) {
        this.chartRepo = chartRepo;
        this.datasetRepo = datasetRepo;
        this.snapshotRepo = snapshotRepo;
    }

    public Page<Chart> list(Long organizationId, Pageable pageable) {
        return chartRepo.findAllByOrganizationId(organizationId, pageable);
    }

    public Chart get(Long organizationId, Long id) {
        Chart chart = chartRepo.findById(id).orElseThrow(() -> new BadRequestException("Chart not found"));
        if (!chart.getOrganizationId().equals(organizationId)) {
            throw new BadRequestException("Chart not found");
        }
        return chart;
    }

    public Chart create(Long organizationId, String name, ChartType type, Long datasetId, JsonNode config) {
        ensureDatasetBelongsToOrg(organizationId, datasetId);
        return chartRepo.save(new Chart(
                organizationId,
                requireName(name),
                requireType(type),
                datasetId,
                normalizeConfig(config),
                currentUsername()
        ));
    }

    public Chart update(Long organizationId, Long id, String name, ChartType type, Long datasetId, JsonNode config) {
        Chart chart = get(organizationId, id);
        if (name != null) {
            chart.setName(requireName(name));
        }
        if (type != null) {
            chart.setType(type);
        }
        if (datasetId != null) {
            ensureDatasetBelongsToOrg(organizationId, datasetId);
            chart.setDatasetId(datasetId);
        }
        if (config != null) {
            chart.setConfig(normalizeConfig(config));
        }
        return chartRepo.save(chart);
    }

    public void delete(Long organizationId, Long id) {
        chartRepo.delete(get(organizationId, id));
    }

    public RenderResult render(Long organizationId, Long chartId, Long snapshotId) {
        Chart chart = get(organizationId, chartId);
        Dataset dataset = ensureDatasetBelongsToOrg(organizationId, chart.getDatasetId());
        DatasetSnapshot snapshot = snapshotRepo.findByIdAndOrganizationId(snapshotId, organizationId)
                .orElseThrow(() -> new BadRequestException("DatasetSnapshot not found"));

        if (!Objects.equals(snapshot.getDatasetId(), dataset.getId())) {
            throw new BadRequestException("Snapshot does not belong to chart dataset");
        }
        if (snapshot.getData() == null || !snapshot.getData().isArray()) {
            throw new BadRequestException("Snapshot data is not renderable");
        }

        ChartConfig config = parseConfig(chart.getConfig());
        Map<GroupKey, AggregateState> grouped = new LinkedHashMap<>();
        for (JsonNode row : snapshot.getData()) {
            String xValue = asKeyValue(row.get(config.xField()));
            String seriesValue = config.seriesField() == null ? null : asKeyValue(row.get(config.seriesField()));
            GroupKey key = new GroupKey(xValue, seriesValue);
            grouped.computeIfAbsent(key, ignored -> new AggregateState()).add(config.aggregation(), row.get(config.yField()));
        }

        List<Map<String, Object>> points = new ArrayList<>();
        grouped.forEach((key, state) -> {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("x", key.x());
            if (key.series() != null) {
                point.put("series", key.series());
            }
            point.put("value", state.value(config.aggregation()));
            points.add(point);
        });

        return new RenderResult(chart.getId(), chart.getType(), snapshot.getId(), points);
    }

    private Dataset ensureDatasetBelongsToOrg(Long organizationId, Long datasetId) {
        Dataset dataset = datasetRepo.findById(datasetId).orElseThrow(() -> new BadRequestException("Dataset not found"));
        if (!dataset.getOrganizationId().equals(organizationId)) {
            throw new BadRequestException("Dataset not found");
        }
        return dataset;
    }

    private JsonNode normalizeConfig(JsonNode config) {
        if (config == null || config.isNull()) {
            return JsonNodeFactory.instance.objectNode();
        }
        if (!config.isObject()) {
            throw new BadRequestException("config must be a JSON object");
        }
        return config;
    }

    private ChartConfig parseConfig(JsonNode config) {
        String xField = text(config, "xField");
        String yField = text(config, "yField");
        if (xField == null || xField.isBlank()) {
            throw new BadRequestException("Chart config xField is required");
        }
        if (yField == null || yField.isBlank()) {
            throw new BadRequestException("Chart config yField is required");
        }
        String seriesField = text(config, "seriesField");
        String aggregation = text(config, "aggregation");
        return new ChartConfig(xField, yField, seriesField, aggregation == null ? "sum" : aggregation.trim().toLowerCase());
    }

    private String asKeyValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        return name.trim();
    }

    private ChartType requireType(ChartType type) {
        if (type == null) {
            throw new BadRequestException("type is required");
        }
        return type;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "anonymous";
        }
        return authentication.getName();
    }

    public record RenderResult(Long chartId, ChartType type, Long snapshotId, List<Map<String, Object>> points) {
    }

    private record ChartConfig(String xField, String yField, String seriesField, String aggregation) {
    }

    private record GroupKey(String x, String series) {
    }

    private static class AggregateState {
        private BigDecimal sum = BigDecimal.ZERO;
        private BigDecimal count = BigDecimal.ZERO;

        void add(String aggregation, JsonNode valueNode) {
            count = count.add(BigDecimal.ONE);
            if ("count".equals(aggregation)) {
                return;
            }
            BigDecimal numericValue = toBigDecimal(valueNode);
            sum = sum.add(numericValue);
        }

        Object value(String aggregation) {
            return switch (aggregation) {
                case "count" -> count.longValue();
                case "avg" -> count.signum() == 0 ? BigDecimal.ZERO : sum.divide(count, 4, RoundingMode.HALF_UP);
                case "sum" -> sum;
                default -> throw new BadRequestException("Unsupported aggregation: " + aggregation);
            };
        }

        private BigDecimal toBigDecimal(JsonNode valueNode) {
            if (valueNode == null || valueNode.isNull()) {
                return BigDecimal.ZERO;
            }
            if (valueNode.isNumber()) {
                return valueNode.decimalValue();
            }
            try {
                return new BigDecimal(valueNode.asText());
            } catch (Exception ex) {
                throw new BadRequestException("Chart aggregation requires numeric yField values");
            }
        }
    }
}
