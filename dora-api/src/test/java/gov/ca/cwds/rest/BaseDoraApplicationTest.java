package gov.ca.cwds.rest;

import static gov.ca.cwds.rest.DoraConstants.PROD_MODE;

import gov.ca.cwds.rest.ElasticsearchConfiguration.XpackConfiguration;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import javax.ws.rs.client.Client;
import org.glassfish.jersey.client.JerseyClient;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;

/**
 * @author TPT-2
 */
public abstract class BaseDoraApplicationTest {

  private static final String configFile = "config/test-dora.yml";

  @BeforeClass
  public static void setUp() {
  }

  @ClassRule
  public static final DropwizardAppRule<DoraConfiguration> appRule =
      new DropwizardAppRule<DoraConfiguration>(DoraApplication.class,
          ResourceHelpers.resourceFilePath(configFile)) {

        @Override
        public Client client() {
          Client client = super.client();
          if (((JerseyClient) client).isClosed()) {
            client = clientBuilder().build();
          }
          return client;
        }
      };

  @Rule
  public RestClientTestRule clientTestRule = new RestClientTestRule(appRule);

  public static DoraConfiguration config(String mode, ElasticsearchConfiguration esConfig) {
    DoraConfiguration config = new DoraConfiguration();
    config.setMode(mode);
    config.setElasticsearchConfiguration(esConfig);
    return config;
  }

  public static ElasticsearchConfiguration esConfig(String nodes, boolean xPackEnabled, String user,
      String password) {
    XpackConfiguration xpackConfig = new XpackConfiguration();
    xpackConfig.setEnabled(xPackEnabled);

    ElasticsearchConfiguration esConfig = new ElasticsearchConfiguration();
    esConfig.setNodes(nodes);
    esConfig.setUser(user);
    esConfig.setPassword(password);
    esConfig.setXpack(xpackConfig);

    return esConfig;
  }

  public static DoraConfiguration config(String nodes, boolean xPackEnabled, String user,
      String password) {
    return config(PROD_MODE, esConfig(nodes, xPackEnabled, user, password));
  }
}
