package gov.ca.cwds.dora.health;

import com.google.inject.Inject;
import gov.ca.cwds.rest.DoraConfiguration;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * @author CWDS TPT-2
 */
public class ElasticsearchRolesHealthCheck extends ElasticsearchHealthCheck {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchRolesHealthCheck.class);

  static final String ES_ROLES_ENDPOINT = "/_xpack/security/role?pretty";

  static final String HEALTHY_ES_ROLES_MSG = "[%s] role exist on the server.";
  static final String UNHEALTHY_ES_ROLES_MSG = "[%s] role does not exist on the server.";

  private String roleName;

  /**
   * Constructor
   *
   * @param config instance of DoraConfiguration
   */
  @Inject
  public ElasticsearchRolesHealthCheck(DoraConfiguration config, String roleName) {
    super(config);
    this.roleName = roleName;
  }

  @Override
  protected Result elasticsearchCheck(RestClient esRestClient) throws IOException {
    Map<String, Object> jsonMap = performRequest(esRestClient, "GET", ES_ROLES_ENDPOINT);

    if (jsonMap.get(roleName) == null) {
      String unhealthyMsg = String
          .format(UNHEALTHY_ES_ROLES_MSG, roleName);
      LOGGER.error(unhealthyMsg);
      return Result.unhealthy(unhealthyMsg);
    } else {
      String healthyMsg = String.format(HEALTHY_ES_ROLES_MSG, roleName);
      LOGGER.info(healthyMsg);
      return Result.healthy(healthyMsg);
    }
  }
}
