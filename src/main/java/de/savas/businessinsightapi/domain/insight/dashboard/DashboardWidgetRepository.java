package de.savas.businessinsightapi.domain.insight.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DashboardWidgetRepository extends JpaRepository<DashboardWidget, Long> {
    List<DashboardWidget> findAllByDashboardId(Long dashboardId);
    void deleteAllByDashboardId(Long dashboardId);
}
