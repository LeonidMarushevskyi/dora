package gov.ca.cwds.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.Injector;
import com.hubspot.dropwizard.guice.GuiceBundle;
import gov.ca.cwds.rest.filters.RequestResponseLoggingFilter;
import gov.ca.cwds.rest.filters.UnhandledExceptionMapperImpl;
import gov.ca.cwds.rest.resources.SwaggerResource;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Environment;
import io.dropwizard.views.ViewBundle;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.jaxrs.listing.ApiListingResource;
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.secnod.dropwizard.shiro.ShiroBundle;
import org.secnod.dropwizard.shiro.ShiroConfiguration;
import org.secnod.shiro.jaxrs.ShiroExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.ca.cwds.inject.ApplicationModule;
import io.dropwizard.setup.Bootstrap;

/**
 * Core execution class of CWDS REST Dora server application.
 * <h3>Standard command line arguments:</h3>
 * <blockquote> server config/dora.yml </blockquote>
 * <h3>Standard JVM arguments:</h3>
 * <blockquote>-Djava.library.path=${workspace_loc:CWDS_API}/lib:/usr/local/lib/ </blockquote>
 *
 * @author CWDS API Team
 */
public final class DoraApplication extends Application<DoraConfiguration> {

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(DoraApplication.class);

  private GuiceBundle<DoraConfiguration> guiceBundle;

  private final ShiroBundle<DoraConfiguration> shiroBundle = new ShiroBundle<DoraConfiguration>() {
    @Override
    protected ShiroConfiguration narrow(DoraConfiguration configuration) {
      return configuration.getShiroConfiguration();
    }
  };

  /**
   * Start the CWDS RESTful API application.
   *
   * @param args command line
   */
  public static void main(final String[] args) {
    try {
      new DoraApplication().run(args);
    } catch (Exception e) {
      LOGGER.error("ERROR: {}", e.getMessage(), e);
      System.exit(1);
    }
  }

  /**
   * {@inheritDoc}
   *
   * @see gov.ca.cwds.rest.BaseApiApplication#applicationModule(io.dropwizard.setup.Bootstrap)
   */
  @Override
  public final void initialize(Bootstrap<DoraConfiguration> bootstrap) {
    bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
        bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));

    bootstrap.addBundle(new ViewBundle<>());
    guiceBundle = GuiceBundle.<DoraConfiguration>newBuilder()
        .addModule(new ApplicationModule())
        .setConfigClass(bootstrap.getApplication().getConfigurationClass())
        .enableAutoConfig(getClass().getPackage().getName()).build();

    bootstrap.addBundle(guiceBundle);
    bootstrap.addBundle(shiroBundle);
  }

  @Override
  public final void run(final DoraConfiguration configuration, final Environment environment) {
    environment.jersey().register(new ShiroExceptionMapper());
    environment.servlets().setSessionHandler(new SessionHandler());

    LOGGER.info("Application name: {}, Version: {}", configuration.getApplicationName(),
        configuration.getVersion());

    LOGGER.info("Configuring CORS: Cross-Origin Resource Sharing");
    configureCors(environment);

    LOGGER.info("Configuring SWAGGER");
    configureSwagger(configuration, environment);

    LOGGER.info("Registering Filters");
    registerFilters(environment);
  }

  private void configureCors(final Environment environment) {
    FilterRegistration.Dynamic filter =
        environment.servlets().addFilter("CORS", CrossOriginFilter.class);
    filter.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
    filter.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,PUT,POST,DELETE,OPTIONS");
    filter.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
    filter.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
    filter.setInitParameter("allowedHeaders",
        "Content-Type,Authorization,X-Requested-With,Content-Length,Accept,Origin,X-Auth-Token");
    filter.setInitParameter("allowCredentials", "true");
  }

  private void configureSwagger(final DoraConfiguration apiConfiguration,
      final Environment environment) {
    BeanConfig config = new BeanConfig();
    config.setTitle(apiConfiguration.getSwaggerConfiguration().getTitle());
    config.setDescription(apiConfiguration.getSwaggerConfiguration().getDescription());
    config.setResourcePackage(apiConfiguration.getSwaggerConfiguration().getResourcePackage());
    config.setScan(true);

    new AssetsBundle(apiConfiguration.getSwaggerConfiguration().getAssetsPath(),
        apiConfiguration.getSwaggerConfiguration().getAssetsPath(), null, "swagger")
        .run(environment);
    environment.getObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
    environment.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    LOGGER.info("Registering ApiListingResource");
    environment.jersey().register(new ApiListingResource());

    LOGGER.info("Registering SwaggerResource");
    final SwaggerResource swaggerResource =
        new SwaggerResource(apiConfiguration.getSwaggerConfiguration());
    environment.jersey().register(swaggerResource);
  }

  private void registerFilters(final Environment environment) {
    // Story #129093035: Catch/handle 500 errors.
    environment.jersey().register(UnhandledExceptionMapperImpl.class);

    Injector injector = guiceBundle.getInjector();
    environment.servlets()
        .addFilter("AuditAndLoggingFilter",
            injector.getInstance(RequestResponseLoggingFilter.class))
        .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
  }
}
