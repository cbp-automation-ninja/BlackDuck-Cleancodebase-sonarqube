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
package org.sonar.server.user.ws;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.sonar.api.server.ws.Change;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService.NewController;
import org.sonar.core.platform.EditionProvider;
import org.sonar.core.platform.PlatformEditionProvider;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.permission.GlobalPermission;
import org.sonar.db.project.ProjectDto;
import org.sonar.db.property.PropertyQuery;
import org.sonar.db.user.UserDto;
import org.sonar.server.common.avatar.AvatarResolver;
import org.sonar.server.permission.PermissionService;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.Users.CurrentWsResponse;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.emptyToNull;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.sonar.db.permission.ProjectPermission.USER;
import static org.sonar.server.ws.WsUtils.writeProtobuf;
import static org.sonarqube.ws.Users.CurrentWsResponse.HomepageType.APPLICATION;
import static org.sonarqube.ws.Users.CurrentWsResponse.HomepageType.PORTFOLIO;
import static org.sonarqube.ws.Users.CurrentWsResponse.HomepageType.PROJECT;
import static org.sonarqube.ws.Users.CurrentWsResponse.Permissions;
import static org.sonarqube.ws.Users.CurrentWsResponse.newBuilder;
import static org.sonarqube.ws.client.user.UsersWsParameters.ACTION_CURRENT;

public class CurrentAction implements UsersWsAction {
  private final UserSession userSession;
  private final DbClient dbClient;
  private final AvatarResolver avatarResolver;
  private final HomepageTypes homepageTypes;
  private final PlatformEditionProvider editionProvider;
  private final PermissionService permissionService;

  public CurrentAction(UserSession userSession, DbClient dbClient, AvatarResolver avatarResolver, HomepageTypes homepageTypes,
    PlatformEditionProvider editionProvider, PermissionService permissionService) {
    this.userSession = userSession;
    this.dbClient = dbClient;
    this.avatarResolver = avatarResolver;
    this.homepageTypes = homepageTypes;
    this.editionProvider = editionProvider;
    this.permissionService = permissionService;
  }

  @Override
  public void define(NewController context) {
    context.createAction(ACTION_CURRENT)
      .setDescription("Get the details of the current authenticated user.")
      .setSince("5.2")
      .setInternal(true)
      .setHandler(this)
      .setResponseExample(getClass().getResource("current-example.json"))
      .setChangelog(
        new Change("6.5", "showOnboardingTutorial is now returned in the response"),
        new Change("7.1", "'parameter' is replaced by 'component' and 'organization' in the response"),
        new Change("9.2", "boolean 'usingSonarLintConnectedMode' and 'sonarLintAdSeen' fields are now returned in the response"),
        new Change("9.5", "showOnboardingTutorial is not returned anymore in the response"),
        new Change("9.6", "'sonarLintAdSeen' is removed and replaced by a 'dismissedNotices' map that support multiple values")
      );
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    if (userSession.isLoggedIn()) {
      try (DbSession dbSession = dbClient.openSession(false)) {
        writeProtobuf(toWsResponse(dbSession, userSession.getLogin()), request, response);
      }
    } else {
      writeProtobuf(newBuilder()
          .setIsLoggedIn(false)
          .setPermissions(Permissions.newBuilder().addAllGlobal(getGlobalPermissions()).build())
          .build(),
        request, response);
    }
  }

  private CurrentWsResponse toWsResponse(DbSession dbSession, String userLogin) {
    UserDto user = dbClient.userDao().selectActiveUserByLogin(dbSession, userLogin);
    checkState(user != null, "User login '%s' cannot be found", userLogin);
    Collection<String> groups = dbClient.groupMembershipDao().selectGroupsByLogins(dbSession, singletonList(userLogin)).get(userLogin);

    CurrentWsResponse.Builder builder = newBuilder()
      .setIsLoggedIn(true)
      .setLogin(user.getLogin())
      .setName(user.getName())
      .setLocal(user.isLocal())
      .addAllGroups(groups)
      .addAllScmAccounts(user.getSortedScmAccounts())
      .setPermissions(Permissions.newBuilder().addAllGlobal(getGlobalPermissions()).build())
      .setHomepage(buildHomepage(dbSession, user))
      .setUsingSonarLintConnectedMode(user.getLastSonarlintConnectionDate() != null);

    DismissNoticeAction.DismissNotices.getAvailableKeys()
      .forEach(key -> builder.putDismissedNotices(key, isNoticeDismissed(user, key)));
    
    ofNullable(emptyToNull(user.getEmail())).ifPresent(builder::setEmail);
    ofNullable(emptyToNull(user.getEmail())).ifPresent(u -> builder.setAvatar(avatarResolver.create(user)));
    ofNullable(user.getExternalLogin()).ifPresent(builder::setExternalIdentity);
    ofNullable(user.getExternalIdentityProvider()).ifPresent(builder::setExternalProvider);
    return builder.build();
  }

  private List<String> getGlobalPermissions() {
    return permissionService.getGlobalPermissions().stream()
      .filter(userSession::hasPermission)
      .map(GlobalPermission::getKey)
      .toList();
  }

  private boolean isNoticeDismissed(UserDto user, String noticeName) {
    String paramKey = DismissNoticeAction.USER_DISMISS_CONSTANT + noticeName;
    PropertyQuery query = new PropertyQuery.Builder()
      .setUserUuid(user.getUuid())
      .setKey(paramKey)
      .build();

    try (DbSession dbSession = dbClient.openSession(false)) {
      return !dbClient.propertiesDao().selectByQuery(query, dbSession).isEmpty();
    }
  }

  private CurrentWsResponse.Homepage buildHomepage(DbSession dbSession, UserDto user) {
    if (noHomepageSet(user)) {
      return defaultHomepage();
    }

    return doBuildHomepage(dbSession, user).orElse(defaultHomepage());
  }

  private Optional<CurrentWsResponse.Homepage> doBuildHomepage(DbSession dbSession, UserDto user) {

    if (PROJECT.toString().equals(user.getHomepageType())) {
      return projectHomepage(dbSession, user);
    }

    if (APPLICATION.toString().equals(user.getHomepageType()) || PORTFOLIO.toString().equals(user.getHomepageType())) {
      return applicationAndPortfolioHomepage(dbSession, user);
    }

    return of(CurrentWsResponse.Homepage.newBuilder()
      .setType(CurrentWsResponse.HomepageType.valueOf(user.getHomepageType()))
      .build());
  }

  private Optional<CurrentWsResponse.Homepage> projectHomepage(DbSession dbSession, UserDto user) {
    Optional<BranchDto> branchOptional = ofNullable(user.getHomepageParameter()).flatMap(p -> dbClient.branchDao().selectByUuid(dbSession, p));
    Optional<ProjectDto> projectOptional = branchOptional.flatMap(b -> dbClient.projectDao().selectByUuid(dbSession, b.getProjectUuid()));
    if (shouldCleanProjectHomepage(projectOptional, branchOptional)) {
      cleanUserHomepageInDb(dbSession, user);
      return empty();
    }

    CurrentWsResponse.Homepage.Builder homepage = CurrentWsResponse.Homepage.newBuilder()
      .setType(CurrentWsResponse.HomepageType.valueOf(user.getHomepageType()))
      .setComponent(projectOptional.get().getKey());

    if (!branchOptional.get().getProjectUuid().equals(branchOptional.get().getUuid())) {
      homepage.setBranch(branchOptional.get().getKey());
    }
    return of(homepage.build());
  }

  private boolean shouldCleanProjectHomepage(Optional<ProjectDto> projectOptional, Optional<BranchDto> branchOptional) {
    return !projectOptional.isPresent() || !branchOptional.isPresent() || !userSession.hasEntityPermission(USER, projectOptional.get());
  }

  private Optional<CurrentWsResponse.Homepage> applicationAndPortfolioHomepage(DbSession dbSession, UserDto user) {
    Optional<ComponentDto> componentOptional = dbClient.componentDao().selectByUuid(dbSession, of(user.getHomepageParameter()).orElse(EMPTY));
    if (shouldCleanApplicationOrPortfolioHomepage(componentOptional)) {
      cleanUserHomepageInDb(dbSession, user);
      return empty();
    }

    return of(CurrentWsResponse.Homepage.newBuilder()
      .setType(CurrentWsResponse.HomepageType.valueOf(user.getHomepageType()))
      .setComponent(componentOptional.get().getKey())
      .build());
  }

  private boolean shouldCleanApplicationOrPortfolioHomepage(Optional<ComponentDto> componentOptional) {
    return !componentOptional.isPresent() || !hasValidEdition()
      || !userSession.hasComponentPermission(USER, componentOptional.get());
  }

  private boolean hasValidEdition() {
    Optional<EditionProvider.Edition> edition = editionProvider.get();
    if (!edition.isPresent()) {
      return false;
    }
    return switch (edition.get()) {
      case ENTERPRISE, DATACENTER -> true;
      default -> false;
    };
  }

  private void cleanUserHomepageInDb(DbSession dbSession, UserDto user) {
    dbClient.userDao().cleanHomepage(dbSession, user);
  }

  private CurrentWsResponse.Homepage defaultHomepage() {
    return CurrentWsResponse.Homepage.newBuilder()
      .setType(CurrentWsResponse.HomepageType.valueOf(homepageTypes.getDefaultType().name()))
      .build();
  }

  private static boolean noHomepageSet(UserDto user) {
    return user.getHomepageType() == null;
  }

}
