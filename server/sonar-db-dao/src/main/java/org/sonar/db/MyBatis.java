/*
 * SonarQube
 * Copyright (C) 2009-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.db;

import com.google.common.annotations.VisibleForTesting;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.sonar.db.alm.pat.AlmPatMapper;
import org.sonar.db.alm.setting.AlmSettingMapper;
import org.sonar.db.alm.setting.ProjectAlmKeyAndProject;
import org.sonar.db.alm.setting.ProjectAlmSettingMapper;
import org.sonar.db.audit.AuditMapper;
import org.sonar.db.ce.CeActivityMapper;
import org.sonar.db.ce.CeQueueMapper;
import org.sonar.db.ce.CeScannerContextMapper;
import org.sonar.db.ce.CeTaskCharacteristicDto;
import org.sonar.db.ce.CeTaskCharacteristicMapper;
import org.sonar.db.ce.CeTaskInputMapper;
import org.sonar.db.ce.CeTaskMessageMapper;
import org.sonar.db.common.Common;
import org.sonar.db.component.AnalysisPropertiesMapper;
import org.sonar.db.component.AnalysisPropertyValuePerProject;
import org.sonar.db.component.ApplicationProjectsMapper;
import org.sonar.db.component.BranchMapper;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ComponentKeyUpdaterMapper;
import org.sonar.db.component.ComponentMapper;
import org.sonar.db.component.FilePathWithHashDto;
import org.sonar.db.component.KeyWithUuidDto;
import org.sonar.db.component.PrBranchAnalyzedLanguageCountByProjectDto;
import org.sonar.db.component.ProjectLinkMapper;
import org.sonar.db.component.ResourceDto;
import org.sonar.db.component.ScrapAnalysisPropertyDto;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.component.SnapshotMapper;
import org.sonar.db.component.UuidWithBranchUuidDto;
import org.sonar.db.component.ViewsSnapshotDto;
import org.sonar.db.duplication.DuplicationMapper;
import org.sonar.db.duplication.DuplicationUnitDto;
import org.sonar.db.entity.EntityDto;
import org.sonar.db.entity.EntityMapper;
import org.sonar.db.es.EsQueueMapper;
import org.sonar.db.event.EventComponentChangeMapper;
import org.sonar.db.event.EventDto;
import org.sonar.db.event.EventMapper;
import org.sonar.db.issue.AnticipatedTransitionDto;
import org.sonar.db.issue.AnticipatedTransitionMapper;
import org.sonar.db.issue.ImpactDto;
import org.sonar.db.issue.IssueChangeDto;
import org.sonar.db.issue.IssueChangeMapper;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.issue.IssueFixedMapper;
import org.sonar.db.issue.IssueMapper;
import org.sonar.db.issue.NewCodeReferenceIssueDto;
import org.sonar.db.issue.PrIssueDto;
import org.sonar.db.measure.LargestBranchNclocDto;
import org.sonar.db.measure.MeasureMapper;
import org.sonar.db.measure.ProjectMeasureDto;
import org.sonar.db.measure.ProjectMeasureMapper;
import org.sonar.db.metric.MetricMapper;
import org.sonar.db.migrationlog.MigrationLogMapper;
import org.sonar.db.newcodeperiod.NewCodePeriodMapper;
import org.sonar.db.notification.NotificationQueueDto;
import org.sonar.db.notification.NotificationQueueMapper;
import org.sonar.db.permission.AuthorizationMapper;
import org.sonar.db.permission.GroupPermissionDto;
import org.sonar.db.permission.GroupPermissionMapper;
import org.sonar.db.permission.UserPermissionDto;
import org.sonar.db.permission.UserPermissionMapper;
import org.sonar.db.permission.template.PermissionTemplateCharacteristicDto;
import org.sonar.db.permission.template.PermissionTemplateCharacteristicMapper;
import org.sonar.db.permission.template.PermissionTemplateDto;
import org.sonar.db.permission.template.PermissionTemplateGroupDto;
import org.sonar.db.permission.template.PermissionTemplateMapper;
import org.sonar.db.permission.template.PermissionTemplateUserDto;
import org.sonar.db.plugin.PluginDto;
import org.sonar.db.plugin.PluginMapper;
import org.sonar.db.portfolio.PortfolioDto;
import org.sonar.db.portfolio.PortfolioMapper;
import org.sonar.db.portfolio.PortfolioProjectDto;
import org.sonar.db.portfolio.ReferenceDto;
import org.sonar.db.project.ApplicationProjectDto;
import org.sonar.db.project.ProjectBadgeTokenDto;
import org.sonar.db.project.ProjectBadgeTokenMapper;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.project.ProjectExportMapper;
import org.sonar.db.project.ProjectMapper;
import org.sonar.db.property.InternalComponentPropertiesMapper;
import org.sonar.db.property.InternalComponentPropertyDto;
import org.sonar.db.property.InternalPropertiesMapper;
import org.sonar.db.property.InternalPropertyDto;
import org.sonar.db.property.PropertiesMapper;
import org.sonar.db.property.ScrapPropertyDto;
import org.sonar.db.provisioning.DevOpsPermissionsMappingDto;
import org.sonar.db.provisioning.DevOpsPermissionsMappingMapper;
import org.sonar.db.provisioning.GithubOrganizationGroupDto;
import org.sonar.db.provisioning.GithubOrganizationGroupMapper;
import org.sonar.db.purge.PurgeMapper;
import org.sonar.db.purge.PurgeableAnalysisDto;
import org.sonar.db.pushevent.PushEventDto;
import org.sonar.db.pushevent.PushEventMapper;
import org.sonar.db.qualitygate.ProjectQgateAssociationDto;
import org.sonar.db.qualitygate.ProjectQgateAssociationMapper;
import org.sonar.db.qualitygate.QualityGateConditionDto;
import org.sonar.db.qualitygate.QualityGateConditionMapper;
import org.sonar.db.qualitygate.QualityGateDto;
import org.sonar.db.qualitygate.QualityGateGroupPermissionsMapper;
import org.sonar.db.qualitygate.QualityGateMapper;
import org.sonar.db.qualitygate.QualityGateUserPermissionsMapper;
import org.sonar.db.qualityprofile.ActiveRuleDto;
import org.sonar.db.qualityprofile.ActiveRuleMapper;
import org.sonar.db.qualityprofile.ActiveRuleParamDto;
import org.sonar.db.qualityprofile.DefaultQProfileMapper;
import org.sonar.db.qualityprofile.QProfileChangeMapper;
import org.sonar.db.qualityprofile.QProfileEditGroupsMapper;
import org.sonar.db.qualityprofile.QProfileEditUsersMapper;
import org.sonar.db.qualityprofile.QualityProfileExportMapper;
import org.sonar.db.qualityprofile.QualityProfileMapper;
import org.sonar.db.report.RegulatoryReportMapper;
import org.sonar.db.report.ReportScheduleMapper;
import org.sonar.db.report.ReportSubscriptionMapper;
import org.sonar.db.rule.RuleChangeMapper;
import org.sonar.db.rule.RuleMapper;
import org.sonar.db.rule.RuleParamDto;
import org.sonar.db.rule.RuleRepositoryMapper;
import org.sonar.db.scannercache.ScannerAnalysisCacheMapper;
import org.sonar.db.schemamigration.SchemaMigrationDto;
import org.sonar.db.schemamigration.SchemaMigrationMapper;
import org.sonar.db.scim.ScimGroupMapper;
import org.sonar.db.scim.ScimUserMapper;
import org.sonar.db.source.FileSourceMapper;
import org.sonar.db.telemetry.TelemetryMetricsSentMapper;
import org.sonar.db.user.ExternalGroupDto;
import org.sonar.db.user.ExternalGroupMapper;
import org.sonar.db.user.GroupDto;
import org.sonar.db.user.GroupMapper;
import org.sonar.db.user.GroupMembershipDto;
import org.sonar.db.user.GroupMembershipMapper;
import org.sonar.db.user.RoleMapper;
import org.sonar.db.user.SamlMessageIdMapper;
import org.sonar.db.user.SessionTokenMapper;
import org.sonar.db.user.UserDismissedMessagesMapper;
import org.sonar.db.user.UserDto;
import org.sonar.db.user.UserGroupDto;
import org.sonar.db.user.UserGroupMapper;
import org.sonar.db.user.UserMapper;
import org.sonar.db.user.UserTelemetryDto;
import org.sonar.db.user.UserTokenCount;
import org.sonar.db.user.UserTokenDto;
import org.sonar.db.user.UserTokenMapper;
import org.sonar.db.user.ai.UserAiToolUsageMapper;
import org.sonar.db.webhook.WebhookDeliveryMapper;
import org.sonar.db.webhook.WebhookMapper;
import org.springframework.beans.factory.annotation.Autowired;

public class MyBatis {
  private final List<MyBatisConfExtension> confExtensions;
  private final Database database;
  private SqlSessionFactory sessionFactory;

  @Autowired(required = false)
  public MyBatis(Database database) {
    this(database, null);
  }

  @Autowired(required = false)
  public MyBatis(Database database, @Nullable MyBatisConfExtension[] confExtensions) {
    this.confExtensions = confExtensions == null ? Collections.emptyList() : Arrays.asList(confExtensions);
    this.database = database;
  }

  public void start() {
    LogFactory.useNoLogging();

    MyBatisConfBuilder confBuilder = new MyBatisConfBuilder(database);

    // DTO aliases, keep them sorted alphabetically
    confBuilder.loadAlias("ActiveRule", ActiveRuleDto.class);
    confBuilder.loadAlias("ActiveRuleParam", ActiveRuleParamDto.class);
    confBuilder.loadAlias("ApplicationProject", ApplicationProjectDto.class);
    confBuilder.loadAlias("AnticipatedTransition", AnticipatedTransitionDto.class);
    confBuilder.loadAlias("CeTaskCharacteristic", CeTaskCharacteristicDto.class);
    confBuilder.loadAlias("Component", ComponentDto.class);
    confBuilder.loadAlias("DevOpsPermissionsMapping", DevOpsPermissionsMappingDto.class);
    confBuilder.loadAlias("DuplicationUnit", DuplicationUnitDto.class);
    confBuilder.loadAlias("Entity", EntityDto.class);
    confBuilder.loadAlias("Event", EventDto.class);
    confBuilder.loadAlias("ExternalGroup", ExternalGroupDto.class);
    confBuilder.loadAlias("GithubOrganizationGroup", GithubOrganizationGroupDto.class);
    confBuilder.loadAlias("FilePathWithHash", FilePathWithHashDto.class);
    confBuilder.loadAlias("KeyWithUuid", KeyWithUuidDto.class);
    confBuilder.loadAlias("Group", GroupDto.class);
    confBuilder.loadAlias("GroupMembership", GroupMembershipDto.class);
    confBuilder.loadAlias("GroupPermission", GroupPermissionDto.class);
    confBuilder.loadAlias("InternalProperty", InternalPropertyDto.class);
    confBuilder.loadAlias("InternalComponentProperty", InternalComponentPropertyDto.class);
    confBuilder.loadAlias("IssueChange", IssueChangeDto.class);
    confBuilder.loadAlias("KeyLongValue", KeyLongValue.class);
    confBuilder.loadAlias("Impact", ImpactDto.class);
    confBuilder.loadAlias("Issue", IssueDto.class);
    confBuilder.loadAlias("NewCodeReferenceIssue", NewCodeReferenceIssueDto.class);
    confBuilder.loadAlias("ProjectMeasure", ProjectMeasureDto.class);
    confBuilder.loadAlias("LargestBranchNclocDto", LargestBranchNclocDto.class);
    confBuilder.loadAlias("NotificationQueue", NotificationQueueDto.class);
    confBuilder.loadAlias("PermissionTemplateCharacteristic", PermissionTemplateCharacteristicDto.class);
    confBuilder.loadAlias("PermissionTemplateGroup", PermissionTemplateGroupDto.class);
    confBuilder.loadAlias("PermissionTemplate", PermissionTemplateDto.class);
    confBuilder.loadAlias("PermissionTemplateUser", PermissionTemplateUserDto.class);
    confBuilder.loadAlias("Plugin", PluginDto.class);
    confBuilder.loadAlias("Portfolio", PortfolioDto.class);
    confBuilder.loadAlias("PortfolioProject", PortfolioProjectDto.class);
    confBuilder.loadAlias("PortfolioReference", ReferenceDto.class);
    confBuilder.loadAlias("PrIssue", PrIssueDto.class);
    confBuilder.loadAlias("ProjectQgateAssociation", ProjectQgateAssociationDto.class);
    confBuilder.loadAlias("Project", ProjectDto.class);
    confBuilder.loadAlias("ProjectBadgeToken", ProjectBadgeTokenDto.class);
    confBuilder.loadAlias("AnalysisPropertyValuePerProject", AnalysisPropertyValuePerProject.class);
    confBuilder.loadAlias("ProjectAlmKeyAndProject", ProjectAlmKeyAndProject.class);
    confBuilder.loadAlias("PrAndBranchCountByProjectDto", PrBranchAnalyzedLanguageCountByProjectDto.class);
    confBuilder.loadAlias("PurgeableAnalysis", PurgeableAnalysisDto.class);
    confBuilder.loadAlias("PushEvent", PushEventDto.class);
    confBuilder.loadAlias("QualityGateCondition", QualityGateConditionDto.class);
    confBuilder.loadAlias("QualityGate", QualityGateDto.class);
    confBuilder.loadAlias("Resource", ResourceDto.class);
    confBuilder.loadAlias("RuleParam", RuleParamDto.class);
    confBuilder.loadAlias("SchemaMigration", SchemaMigrationDto.class);
    confBuilder.loadAlias("ScrapProperty", ScrapPropertyDto.class);
    confBuilder.loadAlias("ScrapAnalysisProperty", ScrapAnalysisPropertyDto.class);
    confBuilder.loadAlias("Snapshot", SnapshotDto.class);
    confBuilder.loadAlias("User", UserDto.class);
    confBuilder.loadAlias("UserGroup", UserGroupDto.class);
    confBuilder.loadAlias("UserPermission", UserPermissionDto.class);
    confBuilder.loadAlias("UserTelemetry", UserTelemetryDto.class);
    confBuilder.loadAlias("UserToken", UserTokenDto.class);
    confBuilder.loadAlias("UserTokenCount", UserTokenCount.class);
    confBuilder.loadAlias("UuidWithBranchUuid", UuidWithBranchUuidDto.class);
    confBuilder.loadAlias("ViewsSnapshot", ViewsSnapshotDto.class);
    confExtensions.forEach(ext -> ext.loadAliases(confBuilder::loadAlias));

    // keep them sorted alphabetically
    Class<?>[] mappers = {
      ActiveRuleMapper.class,
      AlmPatMapper.class,
      AlmSettingMapper.class,
      AnalysisPropertiesMapper.class,
      AnticipatedTransitionMapper.class,
      ApplicationProjectsMapper.class,
      AuditMapper.class,
      AuthorizationMapper.class,
      BranchMapper.class,
      CeActivityMapper.class,
      CeQueueMapper.class,
      CeScannerContextMapper.class,
      CeTaskInputMapper.class,
      CeTaskCharacteristicMapper.class,
      CeTaskMessageMapper.class,
      ComponentKeyUpdaterMapper.class,
      ComponentMapper.class,
      DefaultQProfileMapper.class,
      DuplicationMapper.class,
      EntityMapper.class,
      EsQueueMapper.class,
      EventMapper.class,
      EventComponentChangeMapper.class,
      GithubOrganizationGroupMapper.class,
      DevOpsPermissionsMappingMapper.class,
      ExternalGroupMapper.class,
      FileSourceMapper.class,
      GroupMapper.class,
      GroupMembershipMapper.class,
      GroupPermissionMapper.class,
      InternalComponentPropertiesMapper.class,
      InternalPropertiesMapper.class,
      IsAliveMapper.class,
      IssueChangeMapper.class,
      IssueMapper.class,
      IssueFixedMapper.class,
      MeasureMapper.class,
      ProjectMeasureMapper.class,
      MetricMapper.class,
      MigrationLogMapper.class,
      NewCodePeriodMapper.class,
      NotificationQueueMapper.class,
      PermissionTemplateCharacteristicMapper.class,
      PermissionTemplateMapper.class,
      PluginMapper.class,
      PortfolioMapper.class,
      ProjectAlmSettingMapper.class,
      ProjectLinkMapper.class,
      ProjectMapper.class,
      ProjectBadgeTokenMapper.class,
      ProjectExportMapper.class,
      ProjectQgateAssociationMapper.class,
      PropertiesMapper.class,
      PurgeMapper.class,
      PushEventMapper.class,
      QProfileChangeMapper.class,
      QProfileEditGroupsMapper.class,
      QProfileEditUsersMapper.class,
      QualityGateConditionMapper.class,
      QualityGateMapper.class,
      QualityGateGroupPermissionsMapper.class,
      QualityGateUserPermissionsMapper.class,
      QualityProfileMapper.class,
      QualityProfileExportMapper.class,
      RegulatoryReportMapper.class,
      ReportScheduleMapper.class,
      ReportSubscriptionMapper.class,
      RoleMapper.class,
      RuleMapper.class,
      RuleChangeMapper.class,
      RuleRepositoryMapper.class,
      SamlMessageIdMapper.class,
      ScannerAnalysisCacheMapper.class,
      SchemaMigrationMapper.class,
      ScimGroupMapper.class,
      ScimUserMapper.class,
      SessionTokenMapper.class,
      SnapshotMapper.class,
      TelemetryMetricsSentMapper.class,
      UserAiToolUsageMapper.class,
      UserDismissedMessagesMapper.class,
      UserGroupMapper.class,
      UserMapper.class,
      UserPermissionMapper.class,
      UserTokenMapper.class,
      WebhookMapper.class,
      WebhookDeliveryMapper.class,
      Common.class
    };
    confBuilder.loadMappers(mappers);
    confExtensions.stream()
      .flatMap(MyBatisConfExtension::getMapperClasses)
      .forEach(confBuilder::loadMapper);

    sessionFactory = new SqlSessionFactoryBuilder().build(confBuilder.build());
  }

  @VisibleForTesting
  SqlSessionFactory getSessionFactory() {
    return sessionFactory;
  }

  public DbSession openSession(boolean batch) {
    if (batch) {
      SqlSession session = sessionFactory.openSession(ExecutorType.BATCH, TransactionIsolationLevel.READ_COMMITTED);
      return new BatchSession(session);
    }
    SqlSession session = sessionFactory.openSession(ExecutorType.REUSE, TransactionIsolationLevel.READ_COMMITTED);
    return new DbSessionImpl(session);
  }

  /**
   * Create a PreparedStatement for SELECT requests with scrolling of results
   */
  public PreparedStatement newScrollingSelectStatement(DbSession session, String sql) {
    int fetchSize = database.getDialect().getScrollDefaultFetchSize();
    return newScrollingSelectStatement(session, sql, fetchSize);
  }

  private static PreparedStatement newScrollingSelectStatement(DbSession session, String sql, int fetchSize) {
    try {
      PreparedStatement stmt = session.getConnection().prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
      stmt.setFetchSize(fetchSize);
      return stmt;
    } catch (SQLException e) {
      throw new IllegalStateException("Fail to create SQL statement: " + sql, e);
    }
  }
}
