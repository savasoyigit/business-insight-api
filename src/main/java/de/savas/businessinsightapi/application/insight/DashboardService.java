package de.savas.businessinsightapi.application.insight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.savas.businessinsightapi.common.error.BadRequestException;
import de.savas.businessinsightapi.domain.insight.chart.ChartRepository;
import de.savas.businessinsightapi.domain.insight.dashboard.Dashboard;
import de.savas.businessinsightapi.domain.insight.dashboard.DashboardRepository;
import de.savas.businessinsightapi.domain.insight.dashboard.DashboardWidget;
import de.savas.businessinsightapi.domain.insight.dashboard.DashboardWidgetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepo;
    private final DashboardWidgetRepository widgetRepo;
    private final ChartRepository chartRepo;

    public DashboardService(DashboardRepository dashboardRepo,
                            DashboardWidgetRepository widgetRepo,
                            ChartRepository chartRepo) {
        this.dashboardRepo = dashboardRepo;
        this.widgetRepo = widgetRepo;
        this.chartRepo = chartRepo;
    }

    public Page<Dashboard> list(Long organizationId, Pageable pageable) {
        return dashboardRepo.findAllByOrganizationId(organizationId, pageable);
    }

    public DashboardDetails get(Long organizationId, Long id) {
        Dashboard dashboard = dashboardRepo.findById(id).orElseThrow(() -> new BadRequestException("Dashboard not found"));
        if (!dashboard.getOrganizationId().equals(organizationId)) {
            throw new BadRequestException("Dashboard not found");
        }
        return new DashboardDetails(dashboard, widgetRepo.findAllByDashboardId(dashboard.getId()));
    }

    @Transactional
    public DashboardDetails create(Long organizationId, String name, List<DashboardWidgetInput> widgets) {
        Dashboard dashboard = dashboardRepo.save(new Dashboard(organizationId, requireName(name), currentUsername()));
        replaceWidgets(organizationId, dashboard.getId(), widgets);
        return get(organizationId, dashboard.getId());
    }

    @Transactional
    public DashboardDetails update(Long organizationId, Long id, String name, List<DashboardWidgetInput> widgets) {
        Dashboard dashboard = get(organizationId, id).dashboard();
        if (name != null) {
            dashboard.setName(requireName(name));
            dashboardRepo.save(dashboard);
        }
        if (widgets != null) {
            replaceWidgets(organizationId, dashboard.getId(), widgets);
        }
        return get(organizationId, id);
    }

    @Transactional
    public void delete(Long organizationId, Long id) {
        Dashboard dashboard = get(organizationId, id).dashboard();
        widgetRepo.deleteAllByDashboardId(dashboard.getId());
        dashboardRepo.delete(dashboard);
    }

    private void replaceWidgets(Long organizationId, Long dashboardId, List<DashboardWidgetInput> widgets) {
        widgetRepo.deleteAllByDashboardId(dashboardId);
        if (widgets == null) {
            return;
        }
        for (DashboardWidgetInput widget : widgets) {
            ensureChartBelongsToOrg(organizationId, widget.chartId());
            widgetRepo.save(new DashboardWidget(
                    dashboardId,
                    widget.chartId(),
                    normalizeLayout(widget.layout())
            ));
        }
    }

    private void ensureChartBelongsToOrg(Long organizationId, Long chartId) {
        var chart = chartRepo.findById(chartId).orElseThrow(() -> new BadRequestException("Chart not found"));
        if (!chart.getOrganizationId().equals(organizationId)) {
            throw new BadRequestException("Chart not found");
        }
    }

    private JsonNode normalizeLayout(JsonNode layout) {
        if (layout == null || layout.isNull()) {
            return JsonNodeFactory.instance.objectNode();
        }
        if (!layout.isObject()) {
            throw new BadRequestException("widget layout must be a JSON object");
        }
        return layout;
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        return name.trim();
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "anonymous";
        }
        return authentication.getName();
    }

    public record DashboardDetails(Dashboard dashboard, List<DashboardWidget> widgets) {
    }

    public record DashboardWidgetInput(Long chartId, JsonNode layout) {
    }
}
